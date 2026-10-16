package com.example.alcohol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DrinkType { BEER, CIDER }

enum class StageType { MASHING, BOIL, COOLING, PRIMARY, RACKING, SECONDARY, CONDITIONING, BOTTLING, KEGGING }

/** Категория ингредиентов, например «Солод» или «Хмель». Редактируется пользователем. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Что есть на складе: категория, единица измерения. Цена и остатки живут в закупках. */
@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    /** Единица измерения: г, мл, шт. */
    val unit: String,
    /** Альфа-кислоты в процентах, только для хмеля. */
    val alphaAcid: Double? = null,
)

/**
 * Закупка (партия на складе). Остаток уменьшается, когда ингредиент уходит в варку.
 * Цена за единицу = totalPrice / quantity, она фиксируется на момент закупки.
 */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["ingredientId"]),
    ],
    indices = [Index("ingredientId")],
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    /** Дата закупки, эпоха в миллисекундах. */
    val purchasedAt: Long,
    /** Купленное количество в единицах ингредиента. */
    val quantity: Double,
    /** Сумма покупки, в рублях. */
    val totalPrice: Double,
    /** Сколько осталось на складе. */
    val remaining: Double,
    val note: String = "",
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

/** Состав рецепта: сколько ингредиента нужно на одну партию. Это шаблон, фактическая варка может отличаться. */
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
    indices = [Index("recipeId")],
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

/**
 * Что реально списано со склада в конкретную партию.
 * Стоимость партии = сумма cost по всем строкам, цена берётся из конкретной закупки (FIFO).
 */
@Entity(
    tableName = "batch_ingredients",
    foreignKeys = [
        ForeignKey(entity = Batch::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"]),
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["ingredientId"]),
    ],
    indices = [Index("batchId"), Index("purchaseId"), Index("ingredientId")],
)
data class BatchIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val purchaseId: Long,
    val ingredientId: Long,
    /** Списано, в единицах ингредиента. */
    val amount: Double,
    /** Стоимость списанного, в рублях. */
    val cost: Double,
)

@Entity(
    tableName = "batch_stages",
    foreignKeys = [
        ForeignKey(entity = Batch::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("batchId")],
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
    /** Id события в календаре телефона, если этап уже занесён туда. Null, если нет. */
    val calendarEventId: Long? = null,
)
