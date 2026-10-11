package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Строка склада: ингредиент, его категория и сколько осталось по всем закупкам. */
data class StockRow(
    val ingredientId: Long,
    val name: String,
    val category: String,
    val unit: String,
    val remaining: Double,
)

@Dao
interface StockDao {

    @Query(
        """
        SELECT i.id AS ingredientId, i.name AS name, c.name AS category, i.unit AS unit,
               COALESCE(SUM(p.remaining), 0) AS remaining
        FROM ingredients i
        JOIN categories c ON c.id = i.categoryId
        LEFT JOIN purchases p ON p.ingredientId = i.id
        GROUP BY i.id
        ORDER BY c.id, i.name
        """
    )
    fun stock(): Flow<List<StockRow>>

    @Query("SELECT * FROM ingredients WHERE name = :name LIMIT 1")
    suspend fun findIngredientByName(name: String): Ingredient?

    @Insert
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Insert
    suspend fun insertPurchase(purchase: Purchase): Long

    /** Открытые закупки ингредиента, от старых к новым: это порядок списания FIFO. */
    @Query(
        "SELECT * FROM purchases WHERE ingredientId = :ingredientId AND remaining > 0 " +
            "ORDER BY purchasedAt, id"
    )
    suspend fun openPurchases(ingredientId: Long): List<Purchase>

    @Query("UPDATE purchases SET remaining = :remaining WHERE id = :purchaseId")
    suspend fun setRemaining(purchaseId: Long, remaining: Double)
}
