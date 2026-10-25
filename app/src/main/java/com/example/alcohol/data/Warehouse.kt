package com.example.alcohol.data

import androidx.room.withTransaction
import com.example.alcohol.domain.AllocationResult
import com.example.alcohol.domain.FifoAllocator
import com.example.alcohol.domain.Lot
import com.example.alcohol.domain.StagePlanner
import kotlinx.coroutines.flow.Flow

/** Внутренний сигнал: для правки списаний не хватает ингредиентов. Откатывает транзакцию. */
private class BatchShortfall(val shortfalls: Map<Long, Double>) : RuntimeException()

private class WriteOffShortfall(val shortfall: Double) : RuntimeException()

sealed interface BrewResult {
    data class Brewed(val batchId: Long, val total: Double) : BrewResult

    /** Ингредиентов не хватает. Ключ — ingredientId, значение — сколько не хватило. Ничего не списано. */
    data class NotEnoughStock(val shortfalls: Map<Long, Double>) : BrewResult
}

/**
 * Склад, категории, рецепты, варка и этапы партий.
 * Закупки пополняют остатки, варка списывает их по FIFO и фиксирует стоимость партии.
 */
private const val STAGE_DAY_MS = 24L * 60 * 60 * 1000

class Warehouse(private val db: AppDatabase) {

    private val stockDao = db.stockDao()
    private val categoryDao = db.categoryDao()
    private val batchDao = db.batchDao()
    private val yearTotalDao = db.yearTotalDao()
    private val resourceDao = db.resourceDao()
    private val stageTemplateDao = db.stageTemplateDao()
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

    fun yearTotals(): Flow<List<YearTotal>> = yearTotalDao.all()

    fun resources(type: DrinkType): Flow<List<Resource>> = resourceDao.all(type)

    suspend fun addResource(type: DrinkType, name: String, volumeL: Double, price: Double) {
        resourceDao.insert(validatedResource(0, type, name, volumeL, price))
    }

    suspend fun updateResource(id: Long, type: DrinkType, name: String, volumeL: Double, price: Double) {
        resourceDao.update(validatedResource(id, type, name, volumeL, price))
    }

    suspend fun deleteResource(id: Long) = resourceDao.delete(id)

    private fun validatedResource(id: Long, type: DrinkType, name: String, volumeL: Double, price: Double): Resource {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Укажи название" }
        require(volumeL > 0) { "Объём должен быть больше нуля" }
        require(price >= 0) { "Цена не может быть отрицательной" }
        return Resource(id = id, drinkType = type, name = clean, volumeL = volumeL, price = price)
    }

    /** Добавляет итог за год. Себестоимость за единицу потом считается как сумма / количество. */
    suspend fun addYearTotal(year: Int, name: String, quantity: Double, unit: String, totalCost: Double, icon: String?) {
        yearTotalDao.insert(validatedYearTotal(0, year, name, quantity, unit, totalCost, icon))
    }

    /** Меняет итог за год: название, количество, единицу, сумму и пиктограмму. */
    suspend fun updateYearTotal(id: Long, year: Int, name: String, quantity: Double, unit: String, totalCost: Double, icon: String?) {
        yearTotalDao.update(validatedYearTotal(id, year, name, quantity, unit, totalCost, icon))
    }

    private fun validatedYearTotal(
        id: Long,
        year: Int,
        name: String,
        quantity: Double,
        unit: String,
        totalCost: Double,
        icon: String?,
    ): YearTotal {
        val cleanName = name.trim()
        val cleanUnit = unit.trim()
        require(cleanName.isNotEmpty()) { "Укажи название" }
        require(cleanUnit.isNotEmpty()) { "Укажи единицу измерения" }
        require(quantity > 0) { "Количество должно быть больше нуля" }
        require(totalCost >= 0) { "Сумма не может быть отрицательной" }
        require(year in 2000..2100) { "Проверь год" }
        return YearTotal(
            id = id,
            year = year,
            name = cleanName,
            quantity = quantity,
            unit = cleanUnit,
            totalCost = totalCost,
            icon = icon?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun deleteYearTotal(id: Long) = yearTotalDao.delete(id)

    fun batch(batchId: Long): Flow<Batch?> = batchDao.batch(batchId)

    /**
     * Меняет дату постановки, объём и название партии.
     * Раньше самой поздней закупки, из которой партия уже брала, дату ставить нельзя.
     * Ещё не выполненные этапы сдвигаются вместе с датой. Возвращает null, если всё прошло, или текст ошибки.
     */
    suspend fun updateBatchBasics(batchId: Long, name: String, startedAt: Long, volumeL: Double): String? {
        if (volumeL <= 0) return "Объём должен быть больше нуля"
        return db.withTransaction {
            val batch = batchDao.batchOnce(batchId) ?: return@withTransaction "Партия не найдена"
            if (startedAt != batch.startedAt) {
                val latest = batchDao.latestUsedPurchaseDate(batchId)
                if (latest != null && startedAt < latest) {
                    return@withTransaction "Часть ингредиентов куплена позже этой даты, раньше поставить нельзя"
                }
            }
            val delta = startedAt - batch.startedAt
            batchDao.updateBatch(batch.copy(name = name.trim().ifEmpty { batch.name }, startedAt = startedAt, volumeL = volumeL))
            if (delta != 0L) {
                for (stage in batchDao.stagesOnce(batchId)) {
                    val planned = stage.plannedAt
                    if (stage.doneAt == null && planned != null) {
                        batchDao.updateStage(stage.copy(plannedAt = planned + delta))
                    }
                }
            }
            null
        }
    }

    fun stages(batchId: Long): Flow<List<BatchStage>> = batchDao.stages(batchId)

    /** Общий процесс для напитка: шаги, из которых создаются этапы новых партий. */
    fun stageTemplates(type: DrinkType): Flow<List<StageTemplate>> = stageTemplateDao.all(type)

    suspend fun addStageTemplate(type: DrinkType, title: String, daysAfterPrevious: Int, icon: String) {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "Укажи название этапа" }
        require(daysAfterPrevious >= 0) { "Срок не может быть отрицательным" }
        db.withTransaction {
            val next = (stageTemplateDao.allOnce(type).maxOfOrNull { it.orderIndex } ?: -1) + 1
            stageTemplateDao.insert(
                StageTemplate(drinkType = type, orderIndex = next, title = clean, daysAfterPrevious = daysAfterPrevious, icon = icon.trim())
            )
            resyncBatches(type)
        }
    }

    suspend fun updateStageTemplate(id: Long, title: String, daysAfterPrevious: Int, icon: String) {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "Укажи название этапа" }
        require(daysAfterPrevious >= 0) { "Срок не может быть отрицательным" }
        db.withTransaction {
            val current = stageTemplateDao.get(id) ?: return@withTransaction
            stageTemplateDao.update(current.copy(title = clean, daysAfterPrevious = daysAfterPrevious, icon = icon.trim()))
            resyncBatches(current.drinkType)
        }
    }

    suspend fun deleteStageTemplate(id: Long) {
        db.withTransaction {
            val current = stageTemplateDao.get(id) ?: return@withTransaction
            stageTemplateDao.delete(id)
            resyncBatches(current.drinkType)
        }
    }

    /** Двигает шаг на одно место вверх или вниз. */
    suspend fun moveStageTemplate(id: Long, up: Boolean) {
        db.withTransaction {
            val current = stageTemplateDao.get(id) ?: return@withTransaction
            val list = stageTemplateDao.allOnce(current.drinkType).toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in list.indices) return@withTransaction
            list[index] = list[target].also { list[target] = list[index] }
            list.forEachIndexed { i, template ->
                if (template.orderIndex != i) stageTemplateDao.update(template.copy(orderIndex = i))
            }
            resyncBatches(current.drinkType)
        }
    }

    /** Запоминает, что этап занесён в календарь телефона (id события). */
    suspend fun setStageCalendarEvent(stageId: Long, eventId: Long?) = batchDao.setStageCalendarEvent(stageId, eventId)

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
                // Только закупки, сделанные к дате варки.
                val open = stockDao.openPurchasesUntil(ingredientId, startedAt)
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

            // Этапы партии берутся из общего процесса для этого напитка: у каждого свои название и срок.
            val steps = stageTemplateDao.allOnce(drinkType)
            val planned = StagePlanner.plan(steps.map { it.daysAfterPrevious }, startedAt)
            batchDao.insertStages(
                steps.mapIndexed { i, step ->
                    BatchStage(
                        batchId = batchId,
                        type = step.type,
                        plannedAt = planned[i],
                        title = step.title,
                        daysAfterPrevious = step.daysAfterPrevious,
                        templateId = step.id,
                        orderIndex = i,
                        icon = step.icon,
                    )
                }
            )

            BrewResult.Brewed(batchId = batchId, total = uses.sumOf { it.cost })
        }
    }

    /** Списания варки, по строкам: что и сколько взято на неё. */
    fun batchUses(batchId: Long): Flow<List<BatchIngredient>> = batchDao.uses(batchId)

    suspend fun batchUsesOnce(batchId: Long): List<BatchIngredient> = batchDao.usesOnce(batchId)

    suspend fun purchasesListOf(ingredientId: Long): List<Purchase> = stockDao.purchasesListOf(ingredientId)

    suspend fun writeOffsListOf(ingredientId: Long): List<WriteOff> = stockDao.writeOffsListOf(ingredientId)

    /** Сколько закупок и списаний в базе всего: для проверки, что данные на месте. */
    suspend fun countsForDiagnostics(): Pair<Int, Int> = stockDao.purchaseCountAll() to stockDao.writeOffCountAll()

    fun writeOffsOf(ingredientId: Long): Flow<List<WriteOff>> = stockDao.writeOffsOf(ingredientId)

    fun brewUsageOf(ingredientId: Long): Flow<List<BrewUsageRow>> = stockDao.brewUsageOf(ingredientId)

    /**
     * Ручное списание: берём закупки, сделанные не позже даты списания, как при варке.
     * Возвращает нехватку (0, если всё списано).
     */
    suspend fun writeOff(ingredientId: Long, at: Long, amount: Double, reason: String): Double {
        require(amount > 0) { "Количество должно быть больше нуля" }
        return db.withTransaction { allocateWriteOff(ingredientId, at, amount, reason) }
    }

    /**
     * Меняет ручное списание: старые строки возвращаются на закупки, затем списание считается заново.
     * Если не хватает, ничего не меняется, а нехватка возвращается (0, если всё в порядке).
     */
    suspend fun replaceWriteOff(ids: List<Long>, ingredientId: Long, at: Long, amount: Double, reason: String): Double {
        require(amount > 0) { "Количество должно быть больше нуля" }
        return try {
            db.withTransaction {
                for (id in ids) restoreWriteOff(id)
                val shortfall = allocateWriteOff(ingredientId, at, amount, reason)
                if (shortfall > 0) throw WriteOffShortfall(shortfall)
            }
            0.0
        } catch (e: WriteOffShortfall) {
            e.shortfall
        }
    }

    /** Внутри транзакции: раскладывает списание по закупкам. При нехватке ничего не пишет и возвращает её. */
    private suspend fun allocateWriteOff(ingredientId: Long, at: Long, amount: Double, reason: String): Double {
        val open = stockDao.openPurchasesUntil(ingredientId, at)
        val lots = open.map { Lot(it.id, it.remaining, it.totalPrice / it.quantity) }
        val result = FifoAllocator.allocate(lots, amount)
        if (result.shortfall > 0) return result.shortfall
        val byId = open.associateBy { it.id }
        for (allocation in result.allocations) {
            stockDao.insertWriteOff(
                WriteOff(
                    ingredientId = ingredientId,
                    purchaseId = allocation.purchaseId,
                    writtenOffAt = at,
                    amount = allocation.amount,
                    cost = allocation.cost,
                    reason = reason.trim(),
                )
            )
            val purchase = byId.getValue(allocation.purchaseId)
            stockDao.setRemaining(purchase.id, purchase.remaining - allocation.amount)
        }
        return 0.0
    }

    /** Возвращает количество одной строки списания на закупку и удаляет её. */
    private suspend fun restoreWriteOff(id: Long) {
        val writeOff = stockDao.writeOff(id) ?: return
        val purchase = stockDao.purchase(writeOff.purchaseId)
        if (purchase != null) stockDao.setRemaining(purchase.id, purchase.remaining + writeOff.amount)
        stockDao.deleteWriteOff(id)
    }

    /** Удаляет ручное списание и возвращает количество на закупку. */
    suspend fun deleteWriteOff(id: Long) {
        db.withTransaction { restoreWriteOff(id) }
    }

    /**
     * Удаляет партию. Всё, что она взяла со склада, возвращается на те же закупки,
     * откуда списывалось, а этапы и списания удаляются вместе с ней.
     */
    suspend fun deleteBatch(batchId: Long) {
        db.withTransaction {
            for (use in batchDao.usesOnce(batchId)) {
                val purchase = stockDao.purchase(use.purchaseId) ?: continue
                stockDao.setRemaining(purchase.id, purchase.remaining + use.amount)
            }
            batchDao.deleteUses(batchId)
            batchDao.deleteStages(batchId)
            batchDao.deleteBatch(batchId)
        }
    }

    /**
     * Задаёт, что взято на варку: ингредиент -> количество. Ингредиента нет в карте, значит списание снято.
     * Старые списания возвращаются на закупки, новые распределяются по закупкам, сделанным не позже даты варки.
     * Если чего-то не хватает, ничего не меняется, а нехватка возвращается.
     */
    suspend fun setBatchUsage(batchId: Long, targets: Map<Long, Double>): Map<Long, Double> {
        require(targets.values.all { it > 0 }) { "Количество должно быть больше нуля" }
        return try {
            db.withTransaction {
                val batch = batchDao.batchOnce(batchId) ?: return@withTransaction
                // Возвращаем старые списания на закупки.
                for (use in batchDao.usesOnce(batchId)) {
                    val purchase = stockDao.purchase(use.purchaseId) ?: continue
                    stockDao.setRemaining(purchase.id, purchase.remaining + use.amount)
                }
                batchDao.deleteUses(batchId)

                val purchases = mutableMapOf<Long, Purchase>()
                val shortfalls = mutableMapOf<Long, Double>()
                val plans = mutableListOf<Pair<Long, AllocationResult>>()
                for ((ingredientId, amount) in targets) {
                    val open = stockDao.openPurchasesUntil(ingredientId, batch.startedAt)
                    open.forEach { purchases[it.id] = it }
                    val lots = open.map { Lot(it.id, it.remaining, it.totalPrice / it.quantity) }
                    val result = FifoAllocator.allocate(lots, amount)
                    if (result.shortfall > 0) shortfalls[ingredientId] = result.shortfall
                    plans += ingredientId to result
                }
                // Нехватка отменяет всю транзакцию, включая возврат старых списаний.
                if (shortfalls.isNotEmpty()) throw BatchShortfall(shortfalls)

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
            }
            emptyMap()
        } catch (e: BatchShortfall) {
            e.shortfalls
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

            // Следующие этапы отсчитываются от фактической даты этого этапа.
            replanBatch(batch.id)
        }
    }

    /**
     * Пересчитывает плановые даты ещё не выполненных этапов партии: считаем от даты постановки,
     * а после каждого выполненного этапа заново от его фактической даты. Отменённые этапы не считаются.
     */
    private suspend fun replanBatch(batchId: Long) {
        val batch = batchDao.batchOnce(batchId) ?: return
        var cursor = batch.startedAt
        for (stage in batchDao.stagesOnce(batchId)) {
            if (stage.cancelled) continue
            cursor += stage.daysAfterPrevious * STAGE_DAY_MS
            val done = stage.doneAt
            if (done != null) {
                cursor = done
            } else if (stage.plannedAt != cursor) {
                batchDao.updateStage(stage.copy(plannedAt = cursor))
            }
        }
    }

    /** Отменяет этап в этой партии или возвращает его. Отменённый этап не ждём и в сроках не учитываем. */
    suspend fun setStageCancelled(stageId: Long, cancelled: Boolean) {
        db.withTransaction {
            val stage = batchDao.stage(stageId) ?: return@withTransaction
            batchDao.updateStage(stage.copy(cancelled = cancelled))
            replanBatch(stage.batchId)
        }
    }

    /** Снимает отметку о выполнении: этап снова ждёт, а прогноз пересчитывается. */
    suspend fun reopenStage(stageId: Long) {
        db.withTransaction {
            val stage = batchDao.stage(stageId) ?: return@withTransaction
            batchDao.updateStage(stage.copy(doneAt = null))
            replanBatch(stage.batchId)
        }
    }

    /**
     * Приводит этапы всех партий напитка в соответствие с общим процессом:
     * новые шаги появляются в партиях, названия и сроки обновляются, порядок повторяет процесс.
     * Удалённый из процесса шаг уходит из партий, где не выполнен; выполненный остаётся как история.
     */
    private suspend fun resyncBatches(type: DrinkType) {
        val template = stageTemplateDao.allOnce(type)
        val byId = template.associateBy { it.id }
        val indexOf = template.withIndex().associate { it.value.id to it.index }

        for (batch in batchDao.batchesOfType(type)) {
            val keep = mutableListOf<BatchStage>()
            for (stage in batchDao.stagesOnce(batch.id)) {
                val templateId = stage.templateId
                if (templateId != null && templateId !in byId) {
                    if (stage.doneAt == null) {
                        batchDao.deleteStage(stage.id)
                    } else {
                        keep += stage.copy(templateId = null)
                    }
                } else {
                    keep += stage
                }
            }

            val present = keep.mapNotNull { it.templateId }.toSet()
            val added = template.filter { it.id !in present }.map {
                BatchStage(batchId = batch.id, type = it.type, templateId = it.id)
            }

            // Порядок: шаги процесса идут как в процессе, а этапы без шага остаются после того, за которым шли.
            class Item(val stage: BatchStage, val key: Double, val seq: Int)
            var lastKey = -1.0
            val items = keep.mapIndexed { i, stage ->
                val templateId = stage.templateId
                val key = if (templateId != null) indexOf.getValue(templateId).toDouble().also { lastKey = it } else lastKey + 0.5
                Item(stage, key, i)
            } + added.mapIndexed { i, stage ->
                Item(stage, indexOf.getValue(stage.templateId!!).toDouble(), keep.size + i)
            }

            items.sortedWith(compareBy({ it.key }, { it.seq })).forEachIndexed { position, item ->
                val step = item.stage.templateId?.let { byId[it] }
                var stage = item.stage.copy(orderIndex = position)
                if (step != null) stage = stage.copy(title = step.title, daysAfterPrevious = step.daysAfterPrevious, icon = step.icon)
                if (stage.id == 0L) batchDao.insertStage(stage) else batchDao.updateStage(stage)
            }
            replanBatch(batch.id)
        }
    }

    /**
     * Меняет название, категорию или единицу позиции склада.
     * Если в новой категории уже есть позиция с таким названием, обе объединяются:
     * закупки, списания и рецепты переходят к ней (единица у них должна совпадать).
     * Единицу нельзя менять, если позиция уже используется в рецептах или варках.
     */
    suspend fun updateIngredient(id: Long, name: String, categoryId: Long, unit: String) {
        val cleanName = name.trim()
        val cleanUnit = unit.trim()
        require(cleanName.isNotEmpty()) { "Укажи название" }
        require(cleanUnit.isNotEmpty()) { "Укажи единицу измерения" }

        db.withTransaction {
            val current = stockDao.ingredient(id) ?: return@withTransaction
            if (cleanUnit != current.unit) {
                require(
                    stockDao.batchUsageOf(id) == 0 && stockDao.recipeUsageOf(id) == 0 && stockDao.writeOffUsageOf(id) == 0
                ) {
                    "Единицу нельзя менять: позиция уже используется в рецептах или варках"
                }
            }

            val twin = stockDao.findIngredient(cleanName, categoryId)?.takeIf { it.id != id }
            if (twin == null) {
                stockDao.updateIngredient(
                    current.copy(name = cleanName, categoryId = categoryId, unit = cleanUnit)
                )
            } else {
                require(twin.unit == cleanUnit) {
                    "В этой категории уже есть «$cleanName» в единицах «${twin.unit}»"
                }
                stockDao.movePurchases(from = id, to = twin.id)
                stockDao.moveBatchUsage(from = id, to = twin.id)
                stockDao.moveWriteOffs(from = id, to = twin.id)
                stockDao.moveRecipeItems(from = id, to = twin.id)
                stockDao.deleteRecipeItemsOf(id)
                stockDao.deleteIngredient(id)
            }
        }
    }

    /** Все закупки, новые сверху. */
    fun allPurchases(): Flow<List<Purchase>> = stockDao.allPurchases()

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
            // Нельзя отнести закупку на дату позже варки, где она уже списана.
            val firstUse = listOfNotNull(stockDao.earliestUsageOf(id), stockDao.earliestWriteOffOf(id)).minOrNull()
            require(firstUse == null || purchasedAt <= firstUse) {
                "Эта закупка уже списана в варку, которая была раньше новой даты"
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
        if (stockDao.usageCount(id) > 0 || stockDao.writeOffCountOf(id) > 0) return@withTransaction false
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
