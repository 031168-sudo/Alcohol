package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Строка склада: ингредиент и сколько его осталось по всем закупкам. */
data class StockRow(
    val ingredientId: Long,
    val name: String,
    val kind: IngredientKind,
    val unit: String,
    val remaining: Double,
)

@Dao
interface StockDao {

    @Query(
        """
        SELECT i.id AS ingredientId, i.name AS name, i.kind AS kind, i.unit AS unit,
               COALESCE(SUM(p.remaining), 0) AS remaining
        FROM ingredients i
        LEFT JOIN purchases p ON p.ingredientId = i.id
        GROUP BY i.id
        ORDER BY i.kind, i.name
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
