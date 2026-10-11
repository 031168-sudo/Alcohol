package com.example.alcohol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.alcohol.domain.CostLine
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE drinkType = :type ORDER BY name")
    fun recipes(type: DrinkType): Flow<List<Recipe>>

    /** Все строки рецептов с ценами ингредиентов, для расчёта себестоимости. */
    @Query(
        """
        SELECT ri.recipeId AS recipeId, ri.amount AS amount,
               i.kind AS kind, i.pricePerUnit AS pricePerUnit
        FROM recipe_items ri
        JOIN ingredients i ON i.id = ri.ingredientId
        """
    )
    fun costLines(): Flow<List<CostLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeItem(item: RecipeItem)
}
