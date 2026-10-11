package com.example.alcohol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class DrinkType { BEER, CIDER }

enum class IngredientKind { MALT, HOP, YEAST, ADJUNCT, PACKAGING, OTHER }

enum class StageType { MASHING, BOIL, COOLING, PRIMARY, RACKING, SECONDARY, CONDITIONING, BOTTLING, KEGGING }

@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: IngredientKind,
    /** Единица измерения: г, мл, шт. */
    val unit: String,
    /** Цена за одну единицу, в рублях. */
    val pricePerUnit: Double,
    /** Альфа-кислоты в процентах, только для хмеля. */
    val alphaAcid: Double? = null,
)

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val drinkType: DrinkType,
    /** Объём готового напитка, в литрах. */
    val batchVolumeL: Double,
    val notes: String = "",
)

@Entity(
    tableName = "recipe_items",
    primaryKeys = ["recipeId", "ingredientId"],
    foreignKeys = [
        ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["ingredientId"]),
    ],
)
data class RecipeItem(
    val recipeId: Long,
    val ingredientId: Long,
    /** Количество в единицах ингредиента (г, мл, шт.). */
    val amount: Double,
    /** Момент добавления, например «60 мин» для хмеля. */
    val timing: String = "",
)

@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"]),
    ],
)
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long?,
    val name: String,
    val drinkType: DrinkType,
    /** Дата постановки, эпоха в миллисекундах. */
    val startedAt: Long,
    val volumeL: Double,
    val startGravity: Double? = null,
    val notes: String = "",
)

@Entity(
    tableName = "batch_stages",
    foreignKeys = [
        ForeignKey(entity = Batch::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BatchStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val type: StageType,
    /** Фактическая дата этапа, эпоха в миллисекундах. Null, если ещё не выполнен. */
    val doneAt: Long? = null,
    /** Плановая дата, которую считает приложение по шаблону. */
    val plannedAt: Long? = null,
    val temperatureC: Double? = null,
    val gravity: Double? = null,
    val notes: String = "",
)
