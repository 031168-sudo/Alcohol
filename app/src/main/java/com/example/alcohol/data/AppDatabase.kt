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
    entities = [Ingredient::class, Recipe::class, RecipeItem::class, Batch::class, BatchStage::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

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
                    // Версию схемы поднимаем при любом изменении таблиц и пишем миграцию.
                    // Без неё Room не откроет старую базу, и данные не пропадут молча.
                    .build()
                    .also { instance = it }
            }
    }
}
