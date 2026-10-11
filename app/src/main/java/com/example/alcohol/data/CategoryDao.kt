package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY id")
    fun all(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("SELECT COUNT(*) FROM ingredients WHERE categoryId = :categoryId")
    suspend fun ingredientsInCategory(categoryId: Long): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}
