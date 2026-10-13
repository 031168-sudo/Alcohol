package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Строка склада: ингредиент, его категория и сколько осталось по всем закупкам. */
data class StockRow(
    val ingredientId: Long,
    val name: String,
    val categoryId: Long,
    val category: String,
    val unit: String,
    val remaining: Double,
)

@Dao
interface StockDao {

    @Query(
        """
        SELECT i.id AS ingredientId, i.name AS name, i.categoryId AS categoryId,
               COALESCE(c.name, 'Без категории') AS category, i.unit AS unit,
               COALESCE(SUM(p.remaining), 0) AS remaining
        FROM ingredients i
        LEFT JOIN categories c ON c.id = i.categoryId
        LEFT JOIN purchases p ON p.ingredientId = i.id
        GROUP BY i.id
        ORDER BY c.id, i.name
        """
    )
    fun stock(): Flow<List<StockRow>>

    /** Позиция склада определяется парой «название + категория»: одинаковое название в другой категории — другая позиция. */
    @Query("SELECT * FROM ingredients WHERE name = :name AND categoryId = :categoryId LIMIT 1")
    suspend fun findIngredient(name: String, categoryId: Long): Ingredient?

    @Insert
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Query("SELECT COUNT(*) FROM purchases WHERE ingredientId = :ingredientId")
    suspend fun purchaseCountOf(ingredientId: Long): Int

    @Query("SELECT COUNT(*) FROM recipe_items WHERE ingredientId = :ingredientId")
    suspend fun recipeUsageOf(ingredientId: Long): Int

    @Query("DELETE FROM ingredients WHERE id = :ingredientId")
    suspend fun deleteIngredient(ingredientId: Long)

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun ingredient(id: Long): Ingredient?

    @Update
    suspend fun updateIngredient(ingredient: Ingredient)

    /** Сколько раз позиция уже списана в варки. */
    @Query("SELECT COUNT(*) FROM batch_ingredients WHERE ingredientId = :ingredientId")
    suspend fun batchUsageOf(ingredientId: Long): Int

    @Query("UPDATE purchases SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun movePurchases(from: Long, to: Long)

    @Query("UPDATE batch_ingredients SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun moveBatchUsage(from: Long, to: Long)

    /** Строки рецептов, которые дублируются у целевой позиции, пропускаются и удаляются ниже. */
    @Query("UPDATE OR IGNORE recipe_items SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun moveRecipeItems(from: Long, to: Long)

    @Query("DELETE FROM recipe_items WHERE ingredientId = :ingredientId")
    suspend fun deleteRecipeItemsOf(ingredientId: Long)

    @Insert
    suspend fun insertPurchase(purchase: Purchase): Long

    /** Открытые закупки ингредиента, от старых к новым: это порядок списания FIFO. */
    @Query(
        "SELECT * FROM purchases WHERE ingredientId = :ingredientId AND remaining > 0 " +
            "ORDER BY purchasedAt, id"
    )
    suspend fun openPurchases(ingredientId: Long): List<Purchase>

    /** Открытые закупки, сделанные не позже даты варки: более поздние в эту варку не попадают. */
    @Query(
        "SELECT * FROM purchases WHERE ingredientId = :ingredientId AND remaining > 0 " +
            "AND purchasedAt <= :until ORDER BY purchasedAt, id"
    )
    suspend fun openPurchasesUntil(ingredientId: Long, until: Long): List<Purchase>

    /** Самая ранняя варка, в которой списана закупка. Null, если не списывалась. */
    @Query(
        "SELECT MIN(b.startedAt) FROM batch_ingredients bi " +
            "JOIN batches b ON b.id = bi.batchId WHERE bi.purchaseId = :purchaseId"
    )
    suspend fun earliestUsageOf(purchaseId: Long): Long?

    @Query("UPDATE purchases SET remaining = :remaining WHERE id = :purchaseId")
    suspend fun setRemaining(purchaseId: Long, remaining: Double)

    /** Все закупки, новые сверху. Нужны для подсказок при вводе названия. */
    @Query("SELECT * FROM purchases ORDER BY purchasedAt DESC, id DESC")
    fun allPurchases(): Flow<List<Purchase>>

    /** Все закупки ингредиента, новые сверху. */
    @Query("SELECT * FROM purchases WHERE ingredientId = :ingredientId ORDER BY purchasedAt DESC, id DESC")
    fun purchasesOf(ingredientId: Long): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun purchase(id: Long): Purchase?

    @Update
    suspend fun updatePurchase(purchase: Purchase)

    @Query("SELECT COUNT(*) FROM write_offs WHERE purchaseId = :purchaseId")
    suspend fun writeOffCountOf(purchaseId: Long): Int

    @Query("SELECT COUNT(*) FROM write_offs WHERE ingredientId = :ingredientId")
    suspend fun writeOffUsageOf(ingredientId: Long): Int

    @Query("SELECT MIN(writtenOffAt) FROM write_offs WHERE purchaseId = :purchaseId")
    suspend fun earliestWriteOffOf(purchaseId: Long): Long?

    @Query("UPDATE write_offs SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun moveWriteOffs(from: Long, to: Long)

    @Insert
    suspend fun insertWriteOff(writeOff: WriteOff): Long

    @Query("SELECT * FROM write_offs WHERE ingredientId = :ingredientId ORDER BY writtenOffAt DESC, id DESC")
    fun writeOffsOf(ingredientId: Long): Flow<List<WriteOff>>

    @Query("SELECT * FROM write_offs WHERE id = :id")
    suspend fun writeOff(id: Long): WriteOff?

    @Query("DELETE FROM write_offs WHERE id = :id")
    suspend fun deleteWriteOff(id: Long)

    /** Списания варками по ингредиенту: по одной строке на варку. */
    @Query(
        "SELECT b.name AS batchName, b.startedAt AS startedAt, SUM(bi.amount) AS amount, SUM(bi.cost) AS cost " +
            "FROM batch_ingredients bi JOIN batches b ON b.id = bi.batchId " +
            "WHERE bi.ingredientId = :ingredientId GROUP BY bi.batchId ORDER BY b.startedAt DESC"
    )
    fun brewUsageOf(ingredientId: Long): Flow<List<BrewUsageRow>>

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    /** Сколько раз закупка уже списана в варки. */
    @Query("SELECT COUNT(*) FROM batch_ingredients WHERE purchaseId = :purchaseId")
    suspend fun usageCount(purchaseId: Long): Int
}
