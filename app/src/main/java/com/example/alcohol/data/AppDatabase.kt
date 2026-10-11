package com.example.alcohol.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun drinkTypeToString(value: DrinkType): String = value.name

    @TypeConverter
    fun stringToDrinkType(value: String): DrinkType = DrinkType.valueOf(value)

    @TypeConverter
    fun ingredientKindToString(value: IngredientKind): String = value.name

    @TypeConverter
    fun stringToIngredientKind(value: String): IngredientKind = IngredientKind.valueOf(value)

    @TypeConverter
    fun stageTypeToString(value: StageType): String = value.name

    @TypeConverter
    fun stringToStageType(value: String): StageType = StageType.valueOf(value)
}

@Database(
    entities = [
        Ingredient::class,
        Purchase::class,
        Recipe::class,
        RecipeItem::class,
        Batch::class,
        BatchIngredient::class,
        BatchStage::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun stockDao(): StockDao
    abstract fun batchDao(): BatchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alcohol.db",
                )
                    // Пока версия 1: приложение ещё не ставилось на телефон, схему можно менять.
                    // После первой установки любое изменение таблиц требует новой версии и миграции,
                    // иначе Room не откроет базу, а данные пользователя могут пропасть.
                    .build()
                    .also { instance = it }
            }
    }
}
