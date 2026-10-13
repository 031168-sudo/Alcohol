package com.example.alcohol.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun drinkTypeToString(value: DrinkType): String = value.name

    @TypeConverter
    fun stringToDrinkType(value: String): DrinkType = DrinkType.valueOf(value)

    @TypeConverter
    fun stageTypeToString(value: StageType): String = value.name

    @TypeConverter
    fun stringToStageType(value: String): StageType = StageType.valueOf(value)
}

@Database(
    entities = [
        Category::class,
        Ingredient::class,
        Purchase::class,
        Recipe::class,
        RecipeItem::class,
        Batch::class,
        BatchIngredient::class,
        BatchStage::class,
        YearTotal::class,
        WriteOff::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun stockDao(): StockDao
    abstract fun categoryDao(): CategoryDao
    abstract fun batchDao(): BatchDao
    abstract fun yearTotalDao(): YearTotalDao

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
                    // При любом изменении таблиц поднимаем версию и добавляем миграцию.
                    // Без миграции Room не откроет базу, но данные при этом не теряются.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(Categories.SEED_SQL)
                        }
                    })
                    .build()
                    .also { instance = it }
            }
    }
}
