package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Итог за год, который добавляется вручную: например, 40 бутылок сидра или 12 кг солода.
 * Себестоимость за единицу считается как totalCost / quantity.
 */
@Entity(tableName = "year_totals")
data class YearTotal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val totalCost: Double,
)

@Dao
interface YearTotalDao {

    @Query("SELECT * FROM year_totals ORDER BY year DESC, id")
    fun all(): Flow<List<YearTotal>>

    @Insert
    suspend fun insert(total: YearTotal): Long

    @Query("DELETE FROM year_totals WHERE id = :id")
    suspend fun delete(id: Long)
}
