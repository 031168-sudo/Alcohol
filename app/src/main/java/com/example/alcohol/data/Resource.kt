package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Ресурс производства: электричество, вода и т.п. Для каждого напитка свой список.
 * Хранится только название, объём напитка в литрах и цена: «на volumeL литров уходит ресурса на price рублей».
 * К каждому литру напитка добавляется price / volumeL.
 */
@Entity(tableName = "resources")
data class Resource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drinkType: DrinkType,
    val name: String,
    val volumeL: Double,
    val price: Double,
) {
    /** Сколько рублей этот ресурс добавляет к стоимости одного литра. */
    val perLiter: Double get() = if (volumeL > 0) price / volumeL else 0.0
}

@Dao
interface ResourceDao {

    @Query("SELECT * FROM resources WHERE drinkType = :type ORDER BY id")
    fun all(type: DrinkType): Flow<List<Resource>>

    @Insert
    suspend fun insert(resource: Resource): Long

    @Update
    suspend fun update(resource: Resource)

    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun delete(id: Long)
}
