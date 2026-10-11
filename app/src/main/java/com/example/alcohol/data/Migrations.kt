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

/** Итоги за год, введённые вручную. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `year_totals` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`year` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`totalCost` REAL NOT NULL)"
        )
    }
}

/** У пива остаются только первичное брожение и розлив: остальные этапы убираем у всех партий. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM batch_stages WHERE type NOT IN ('PRIMARY', 'BOTTLING') " +
                "AND batchId IN (SELECT id FROM batches WHERE drinkType = 'BEER')"
        )
    }
}

/** У итогов появилась необязательная пиктограмма. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `year_totals` ADD COLUMN `icon` TEXT")
    }
}

/** Розлив в пиве планируется через 8 дней после первичного; пересчитываем ещё не выполненные розливы. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE batch_stages SET plannedAt = " +
                "(SELECT COALESCE(p.doneAt, p.plannedAt) FROM batch_stages p " +
                "WHERE p.batchId = batch_stages.batchId AND p.type = 'PRIMARY') + 8 * 86400000 " +
                "WHERE type = 'BOTTLING' AND doneAt IS NULL " +
                "AND batchId IN (SELECT id FROM batches WHERE drinkType = 'BEER') " +
                "AND EXISTS (SELECT 1 FROM batch_stages p WHERE p.batchId = batch_stages.batchId AND p.type = 'PRIMARY')"
        )
    }
}
