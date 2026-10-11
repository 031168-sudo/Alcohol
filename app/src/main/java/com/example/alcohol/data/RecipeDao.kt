package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
