package com.example.alcohol.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Категории ингредиентов теперь хранятся в таблице, а не в перечислении.
 * Идентификаторы стандартных категорий фиксированы: миграция и первая установка используют одни и те же.
 */
object Categories {
    const val SEED_SQL =
        "INSERT INTO categories (id, name) VALUES " +
            "(1, 'Солод'), (2, 'Хмель'), (3, 'Дрожжи'), (4, 'Добавки'), " +
            "(5, 'Сахар'), (6, 'Упаковка'), (7, 'Прочее')"
}

/**
 * Версия 1 → 2: вместо enum kind у ингредиента появляется categoryId.
 * Существующие ингредиенты переходят в стандартные категории по прежнему виду.
 * defer_foreign_keys откладывает проверку ссылок до конца транзакции: таблица ingredients
 * пересоздаётся, а purchases и batch_ingredients на неё ссылаются.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = ON")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL)"
        )
        db.execSQL(Categories.SEED_SQL)

        db.execSQL(
            "CREATE TABLE `ingredients_new` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`alphaAcid` REAL)"
        )
        db.execSQL(
            "INSERT INTO ingredients_new (id, name, categoryId, unit, alphaAcid) " +
                "SELECT id, name, " +
                "CASE kind WHEN 'MALT' THEN 1 WHEN 'HOP' THEN 2 WHEN 'YEAST' THEN 3 " +
                "WHEN 'ADJUNCT' THEN 4 WHEN 'SUGAR' THEN 5 WHEN 'PACKAGING' THEN 6 ELSE 7 END, " +
                "unit, alphaAcid FROM ingredients"
        )
        db.execSQL("DROP TABLE ingredients")
        db.execSQL("ALTER TABLE ingredients_new RENAME TO ingredients")
    }
}
