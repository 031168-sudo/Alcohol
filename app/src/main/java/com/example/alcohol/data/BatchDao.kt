package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Партия со своей стоимостью: списанные закупки плюс ресурсы (электричество, вода и т.п.) на её объём. */
data class BatchSummary(
    @Embedded val batch: Batch,
    val total: Double,
    /** Розлив (в бутылки или кег) уже отмечен как выполненный. */
    val bottled: Boolean,
    /** Следующий невыполненный этап и его прогнозная дата. Null, если все этапы пройдены. */
    val nextType: StageType?,
    val nextPlannedAt: Long?,
)

/** Этап, который скоро наступает или уже просрочен, для напоминаний. */
data class DueStage(
    val stageId: Long,
    val batchName: String,
    val type: StageType,
    val plannedAt: Long,
)

@Dao
interface BatchDao {

    @Query(
        """
        SELECT b.*,
            COALESCE(SUM(bi.cost), 0) +
                b.volumeL * COALESCE((SELECT SUM(r.price / r.volumeL) FROM resources r
                    WHERE r.drinkType = b.drinkType AND r.volumeL > 0), 0) AS total,
            EXISTS (
                SELECT 1 FROM batch_stages s
                WHERE s.batchId = b.id AND s.type IN ('BOTTLING', 'KEGGING') AND s.doneAt IS NOT NULL
            ) AS bottled,
            (SELECT s.type FROM batch_stages s
                WHERE s.batchId = b.id AND s.doneAt IS NULL ORDER BY s.id LIMIT 1) AS nextType,
            (SELECT s.plannedAt FROM batch_stages s
                WHERE s.batchId = b.id AND s.doneAt IS NULL ORDER BY s.id LIMIT 1) AS nextPlannedAt
        FROM batches b
        LEFT JOIN batch_ingredients bi ON bi.batchId = b.id
        WHERE b.drinkType = :type
        GROUP BY b.id
        ORDER BY b.startedAt DESC
        """
    )
    fun summaries(type: DrinkType): Flow<List<BatchSummary>>

    @Query("SELECT * FROM batches WHERE id = :batchId")
    fun batch(batchId: Long): Flow<Batch?>

    @Query("SELECT * FROM batch_stages WHERE batchId = :batchId ORDER BY id")
    fun stages(batchId: Long): Flow<List<BatchStage>>

    @Query("SELECT * FROM batches WHERE id = :batchId")
    suspend fun batchOnce(batchId: Long): Batch?

    @Query("SELECT * FROM batch_stages WHERE batchId = :batchId ORDER BY id")
    suspend fun stagesOnce(batchId: Long): List<BatchStage>

    @Query("SELECT * FROM batch_stages WHERE id = :stageId")
    suspend fun stage(stageId: Long): BatchStage?

    @Query(
        """
        SELECT s.id AS stageId, b.name AS batchName, s.type AS type, s.plannedAt AS plannedAt
        FROM batch_stages s
        JOIN batches b ON b.id = s.batchId
        WHERE s.doneAt IS NULL AND s.plannedAt IS NOT NULL AND s.plannedAt <= :until
        ORDER BY s.plannedAt
        """
    )
    suspend fun dueStages(until: Long): List<DueStage>

    @Insert
    suspend fun insertBatch(batch: Batch): Long

    @Insert
    suspend fun insertUses(uses: List<BatchIngredient>)

    @Query("SELECT * FROM batch_ingredients WHERE batchId = :batchId")
    suspend fun usesOnce(batchId: Long): List<BatchIngredient>

    @Query("SELECT * FROM batch_ingredients WHERE batchId = :batchId")
    fun uses(batchId: Long): Flow<List<BatchIngredient>>

    @Query("DELETE FROM batch_ingredients WHERE batchId = :batchId")
    suspend fun deleteUses(batchId: Long)

    @Query("DELETE FROM batch_stages WHERE batchId = :batchId")
    suspend fun deleteStages(batchId: Long)

    @Query("DELETE FROM batches WHERE id = :batchId")
    suspend fun deleteBatch(batchId: Long)

    @Insert
    suspend fun insertStages(stages: List<BatchStage>)

    @Update
    suspend fun updateStage(stage: BatchStage)
}
