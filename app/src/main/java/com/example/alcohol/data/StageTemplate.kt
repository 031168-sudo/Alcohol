package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Шаг общего процесса для пива или сидра: из этих шагов создаются этапы каждой новой партии.
 * Порядок задаёт orderIndex, срок — daysAfterPrevious (для первого шага — дней после даты постановки).
 */
@Entity(tableName = "stage_templates")
data class StageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drinkType: DrinkType,
    val orderIndex: Int,
    val title: String,
    val daysAfterPrevious: Int,
    /** Тип нужен для отметки «разлито» (BOTTLING, KEGGING). У добавленных вручную шагов — CUSTOM. */
    val type: StageType = StageType.CUSTOM,
)

@Dao
interface StageTemplateDao {

    @Query("SELECT * FROM stage_templates WHERE drinkType = :type ORDER BY orderIndex, id")
    fun all(type: DrinkType): Flow<List<StageTemplate>>

    @Query("SELECT * FROM stage_templates WHERE drinkType = :type ORDER BY orderIndex, id")
    suspend fun allOnce(type: DrinkType): List<StageTemplate>

    @Query("SELECT * FROM stage_templates WHERE id = :id")
    suspend fun get(id: Long): StageTemplate?

    @Insert
    suspend fun insert(template: StageTemplate): Long

    @Update
    suspend fun update(template: StageTemplate)

    @Query("DELETE FROM stage_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
