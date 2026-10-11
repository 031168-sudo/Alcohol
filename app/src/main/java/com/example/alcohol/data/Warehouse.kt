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
 * Склад, рецепты, варка и этапы партий.
 * Закупки пополняют остатки, варка списывает их по FIFO и фиксирует стоимость партии.
 */
class Warehouse(private val db: AppDatabase) {

    private val stockDao = db.stockDao()
    private val batchDao = db.batchDao()
    private val recipeDao = db.recipeDao()

    fun stock(): Flow<List<StockRow>> = stockDao.stock()

    fun recipes(type: DrinkType): Flow<List<Recipe>> = recipeDao.recipes(type)

    suspend fun recipeItems(recipeId: Long): List<RecipeItem> = recipeDao.items(recipeId)

    fun batches(type: DrinkType): Flow<List<BatchSummary>> = batchDao.summaries(type)

    fun batch(batchId: Long): Flow<Batch?> = batchDao.batch(batchId)

    fun stages(batchId: Long): Flow<List<BatchStage>> = batchDao.stages(batchId)

    /**
     * Записывает закупку. Если ингредиента с таким именем ещё нет, создаёт его.
     * Цена за единицу = totalPrice / quantity.
     */
    suspend fun purchase(
        name: String,
        kind: IngredientKind,
        unit: String,
        quantity: Double,
        totalPrice: Double,
        purchasedAt: Long,
        note: String = "",
    ): Long {
        require(quantity > 0) { "Количество должно быть больше нуля" }
        require(totalPrice >= 0) { "Цена не может быть отрицательной" }

        return db.withTransaction {
            val ingredientId = stockDao.findIngredientByName(name)?.id
                ?: stockDao.insertIngredient(Ingredient(name = name, kind = kind, unit = unit))

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

    suspend fun dueStages(until: Long): List<DueStage> = batchDao.dueStages(until)
}
