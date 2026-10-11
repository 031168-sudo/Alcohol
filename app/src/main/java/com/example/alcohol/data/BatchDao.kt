package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Партия со своей стоимостью, посчитанной по списанным закупкам. */
data class BatchSummary(
    @Embedded val batch: Batch,
    val total: Double,
)

@Dao
interface BatchDao {

    @Query(
        """
        SELECT b.*, COALESCE(SUM(bi.cost), 0) AS total
        FROM batches b
        LEFT JOIN batch_ingredients bi ON bi.batchId = b.id
        WHERE b.drinkType = :type
        GROUP BY b.id
        ORDER BY b.startedAt DESC
        """
    )
    fun summaries(type: DrinkType): Flow<List<BatchSummary>>

    @Insert
    suspend fun insertBatch(batch: Batch): Long

    @Insert
    suspend fun insertUses(uses: List<BatchIngredient>)
}
