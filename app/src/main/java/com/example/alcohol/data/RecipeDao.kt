package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE drinkType = :type ORDER BY name")
    fun recipes(type: DrinkType): Flow<List<Recipe>>

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun items(recipeId: Long): List<RecipeItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RecipeItem>)

    @Update
    suspend fun update(recipe: Recipe)

    @Query("DELETE FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteItems(recipeId: Long)

    /** Сваренные по рецепту партии остаются, но теряют ссылку на него. */
    @Query("UPDATE batches SET recipeId = NULL WHERE recipeId = :recipeId")
    suspend fun detachBatches(recipeId: Long)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Long)
}
