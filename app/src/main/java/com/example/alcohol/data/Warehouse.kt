package com.example.alcohol.data

import androidx.room.withTransaction
import com.example.alcohol.domain.AllocationResult
import com.example.alcohol.domain.FifoAllocator
import com.example.alcohol.domain.Lot
import com.example.alcohol.domain.StageTemplates
import kotlinx.coroutines.flow.Flow

sealed interface BrewResult {
    data class Brewed(val batchId: Long, val total: Double) : BrewResult

    /** Ингредиентов не хватает. Ключ — ingredientId, значение — сколько не хватило. Ничего не списано. */
    data class NotEnoughStock(val shortfalls: Map<Long, Double>) : BrewResult
}

/**
 * Склад, категории, рецепты, варка и этапы партий.
 * Закупки пополняют остатки, варка списывает их по FIFO и фиксирует стоимость партии.
 */
class Warehouse(private val db: AppDatabase) {

    private val stockDao = db.stockDao()
    private val categoryDao = db.categoryDao()
    private val batchDao = db.batchDao()
    private val recipeDao = db.recipeDao()

    fun stock(): Flow<List<StockRow>> = stockDao.stock()

    fun categories(): Flow<List<Category>> = categoryDao.all()

    /** Добавляет категорию. Возвращает false, если такое название уже есть. */
    suspend fun addCategory(name: String): Boolean {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Укажи название категории" }
        return db.withTransaction {
            if (categoryDao.countByName(clean) > 0) return@withTransaction false
            categoryDao.insert(Category(name = clean))
            true
        }
    }

    /** Переименовывает категорию. Возвращает false, если название уже занято. */
    suspend fun renameCategory(id: Long, name: String): Boolean {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Укажи название категории" }
        return db.withTransaction {
            if (categoryDao.countByName(clean) > 0) return@withTransaction false
            categoryDao.rename(id, clean)
            true
        }
    }

    /**
     * Удаляет категорию. Возвращает false, если в ней есть ингредиенты:
     * сначала их нужно перенести в другую категорию.
     */
    suspend fun deleteCategory(id: Long): Boolean = db.withTransaction {
        if (categoryDao.ingredientsInCategory(id) > 0) return@withTransaction false
        categoryDao.delete(id)
        true
    }

    fun recipes(type: DrinkType): Flow<List<Recipe>> = recipeDao.recipes(type)

    suspend fun recipeItems(recipeId: Long): List<RecipeItem> = recipeDao.items(recipeId)

    fun batches(type: DrinkType): Flow<List<BatchSummary>> = batchDao.summaries(type)

    fun batch(batchId: Long): Flow<Batch?> = batchDao.batch(batchId)

    fun stages(batchId: Long): Flow<List<BatchStage>> = batchDao.stages(batchId)

    /**
     * Записывает закупку. Позиция склада — это пара «название + категория»: если такой позиции ещё нет, она создаётся.
     * Цена за единицу = totalPrice / quantity.
     */
    suspend fun purchase(
        name: String,
        categoryId: Long,
        unit: String,
        quantity: Double,
        totalPrice: Double,
        purchasedAt: Long,
        note: String = "",
    ): Long {
        require(name.isNotBlank()) { "Укажи название" }
        require(quantity > 0) { "Количество должно быть больше нуля" }
        require(totalPrice >= 0) { "Цена не может быть отрицательной" }

        return db.withTransaction {
            val ingredientId = stockDao.findIngredient(name.trim(), categoryId)?.id
                ?: stockDao.insertIngredient(
                    Ingredient(name = name.trim(), categoryId = categoryId, unit = unit.trim())
                )

            stockDao.insertPurchase(
                Purchase(
                    ingredientId = ingredientId,
                    purchasedAt = purchasedAt,
                    quantity = quantity,
                    totalPrice = totalPrice,
                    remaining = quantity,
                    note = note,
                )
            )
        }
    }

    /** Рецепт — шаблон: сколько каждого ингредиента нужно на партию. */
    suspend fun createRecipe(
        name: String,
        drinkType: DrinkType,
        volumeL: Double,
        items: Map<Long, Double>,
        notes: String = "",
    ): Long {
        require(name.isNotBlank()) { "Укажи название рецепта" }
        require(volumeL > 0) { "Объём должен быть больше нуля" }
        require(items.isNotEmpty()) { "Добавь хотя бы один ингредиент" }

        return db.withTransaction {
            val recipeId = recipeDao.insertRecipe(
                Recipe(name = name, drinkType = drinkType, batchVolumeL = volumeL, notes = notes)
            )
            recipeDao.insertItems(
                items.map { (ingredientId, amount) ->
                    RecipeItem(recipeId = recipeId, ingredientId = ingredientId, amount = amount)
                }
            )
            recipeId
        }
    }

    /**
     * Варит партию: списывает needs (ingredientId → количество) со склада по FIFO,
     * записывает партию, её стоимость и типовые этапы с прогнозом.
     * Если чего-то не хватает, ничего не меняется.
     */
    suspend fun brew(
        name: String,
        drinkType: DrinkType,
        recipeId: Long?,
        volumeL: Double,
        startedAt: Long,
        needs: Map<Long, Double>,
    ): BrewResult {
        require(volumeL > 0) { "Объём партии должен быть больше нуля" }
        require(needs.isNotEmpty()) { "Нет ингредиентов для варки" }

        return db.withTransaction {
            val purchases = mutableMapOf<Long, Purchase>()
            val shortfalls = mutableMapOf<Long, Double>()
            val plans = mutableListOf<Pair<Long, AllocationResult>>()

            for ((ingredientId, amount) in needs) {
                val open = stockDao.openPurchases(ingredientId)
                open.forEach { purchases[it.id] = it }
                val lots = open.map { Lot(it.id, it.remaining, it.totalPrice / it.quantity) }

                val result = FifoAllocator.allocate(lots, amount)
                if (result.shortfall > 0) shortfalls[ingredientId] = result.shortfall
                plans += ingredientId to result
            }

            if (shortfalls.isNotEmpty()) {
                return@withTransaction BrewResult.NotEnoughStock(shortfalls)
            }

            val batchId = batchDao.insertBatch(
                Batch(
                    recipeId = recipeId,
                    name = name,
                    drinkType = drinkType,
                    startedAt = startedAt,
                    volumeL = volumeL,
                )
            )

            val uses = mutableListOf<BatchIngredient>()
            for ((ingredientId, result) in plans) {
                for (allocation in result.allocations) {
                    uses += BatchIngredient(
                        batchId = batchId,
                        purchaseId = allocation.purchaseId,
                        ingredientId = ingredientId,
                        amount = allocation.amount,
                        cost = allocation.cost,
                    )
                    val current = purchases.getValue(allocation.purchaseId)
                    val left = current.remaining - allocation.amount
                    stockDao.setRemaining(allocation.purchaseId, left)
                    purchases[allocation.purchaseId] = current.copy(remaining = left)
                }
            }
            batchDao.insertUses(uses)

            val planned = StageTemplates.plan(drinkType, startedAt)
            batchDao.insertStages(
                planned.map { (type, at) -> BatchStage(batchId = batchId, type = type, plannedAt = at) }
            )

            BrewResult.Brewed(batchId = batchId, total = uses.sumOf { it.cost })
        }
    }

    /**
     * Отмечает этап выполненным и пересчитывает прогноз для следующих этапов
     * от фактической даты.
     */
    suspend fun completeStage(
        stageId: Long,
        doneAt: Long,
        temperatureC: Double?,
        gravity: Double?,
        notes: String,
    ) {
        db.withTransaction {
            val stage = batchDao.stage(stageId) ?: return@withTransaction
            val batch = batchDao.batchOnce(stage.batchId) ?: return@withTransaction

            batchDao.updateStage(
                stage.copy(
                    doneAt = doneAt,
                    temperatureC = temperatureC,
                    gravity = gravity,
                    notes = notes,
                )
            )

            val replanned = StageTemplates.replan(batch.drinkType, stage.type, doneAt)
            for (next in batchDao.stagesOnce(batch.id)) {
                if (next.doneAt != null) continue
                val newPlan = replanned[next.type] ?: continue
                batchDao.updateStage(next.copy(plannedAt = newPlan))
            }
        }
    }

    /** Закупки ингредиента, новые сверху. */
    fun purchasesOf(ingredientId: Long): Flow<List<Purchase>> = stockDao.purchasesOf(ingredientId)

    /**
     * Меняет дату, количество и сумму закупки. Остаток пересчитывается,
     * но уже списанное в варки не возвращается: количество не может стать меньше списанного.
     */
    suspend fun updatePurchase(id: Long, purchasedAt: Long, quantity: Double, totalPrice: Double) {
        require(quantity > 0) { "Количество должно быть больше нуля" }
        require(totalPrice >= 0) { "Цена не может быть отрицательной" }
        db.withTransaction {
            val current = stockDao.purchase(id) ?: return@withTransaction
            val used = current.quantity - current.remaining
            require(quantity >= used) {
                "Из этой закупки уже списано ${"%.1f".format(used)} — количество не может быть меньше"
            }
            stockDao.updatePurchase(
                current.copy(
                    purchasedAt = purchasedAt,
                    quantity = quantity,
                    totalPrice = totalPrice,
                    remaining = quantity - used,
                )
            )
        }
    }

    /** Удаляет закупку. Возвращает false, если из неё уже что-то списано в варку. */
    suspend fun deletePurchase(id: Long): Boolean = db.withTransaction {
        if (stockDao.usageCount(id) > 0) return@withTransaction false
        val purchase = stockDao.purchase(id) ?: return@withTransaction true
        stockDao.deletePurchase(id)
        // Позиция без закупок и без рецептов больше не нужна: убираем её со склада.
        val ingredientId = purchase.ingredientId
        if (stockDao.purchaseCountOf(ingredientId) == 0 && stockDao.recipeUsageOf(ingredientId) == 0) {
            stockDao.deleteIngredient(ingredientId)
        }
        true
    }

    /** Меняет название, объём и состав рецепта. Уже сваренные партии не меняются. */
    suspend fun updateRecipe(recipe: Recipe, items: Map<Long, Double>) {
        require(recipe.name.isNotBlank()) { "Укажи название рецепта" }
        require(recipe.batchVolumeL > 0) { "Объём должен быть больше нуля" }
        require(items.isNotEmpty()) { "Добавь хотя бы один ингредиент" }

        db.withTransaction {
            recipeDao.update(recipe.copy(name = recipe.name.trim()))
            recipeDao.deleteItems(recipe.id)
            recipeDao.insertItems(
                items.map { (ingredientId, amount) ->
                    RecipeItem(recipeId = recipe.id, ingredientId = ingredientId, amount = amount)
                }
            )
        }
    }

    /** Удаляет рецепт. Партии, сваренные по нему, остаются, но теряют ссылку на рецепт. */
    suspend fun deleteRecipe(recipeId: Long) {
        db.withTransaction {
            recipeDao.detachBatches(recipeId)
            recipeDao.deleteRecipe(recipeId)
        }
    }

    suspend fun dueStages(until: Long): List<DueStage> = batchDao.dueStages(until)
}
