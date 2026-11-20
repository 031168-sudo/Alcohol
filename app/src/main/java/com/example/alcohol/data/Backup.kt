package com.example.alcohol.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Сколько записей в каждой таблице: показывается на экране копии и при подтверждении загрузки. */
data class BackupCounts(val rows: Map<String, Int>) {
    val total: Int get() = rows.values.sum()
}

/**
 * Резервная копия всей базы в одном JSON-файле и загрузка её обратно.
 *
 * Формат простой: имя таблицы → массив строк, в строке — колонки как есть.
 * Id сохраняются, поэтому после загрузки связи между таблицами остаются прежними.
 * Таблицы перечислены от родителей к детям: в этом порядке вставляем, в обратном — удаляем.
 * Всё идёт одной транзакцией: при любой ошибке старые данные остаются на месте.
 */
object Backup {
    const val FORMAT = 1
    private const val APP = "moi-napitki"

    val TABLES = listOf(
        "categories",
        "ingredients",
        "purchases",
        "recipes",
        "recipe_items",
        "batches",
        "batch_ingredients",
        "batch_stages",
        "stage_templates",
        "resources",
        "year_totals",
        "write_offs",
    )

    /** Собирает копию всей базы в JSON. Вызывать из корутины: работа с базой. */
    suspend fun export(db: AppDatabase): String = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        val root = JSONObject()
        root.put("app", APP)
        root.put("format", FORMAT)
        root.put("schema", sql.version)
        root.put("exportedAt", System.currentTimeMillis())
        val tables = JSONObject()
        for (table in TABLES) tables.put(table, readTable(sql, table))
        root.put("tables", tables)
        root.toString(2)
    }

    /** Заменяет все данные содержимым копии. Возвращает, сколько записей загружено. */
    suspend fun restore(db: AppDatabase, json: String): BackupCounts = db.withTransaction {
        val root = JSONObject(json)
        require(root.optString("app") == APP && root.optInt("format") == FORMAT) {
            "Это не файл резервной копии «Моих напитков»"
        }
        val tables = root.optJSONObject("tables")
            ?: throw IllegalArgumentException("В файле нет данных")

        val sql = db.openHelper.writableDatabase
        // Сначала чистим детей, потом родителей: так не мешают внешние ключи.
        for (table in TABLES.asReversed()) sql.execSQL("DELETE FROM $table")

        val inserted = mutableMapOf<String, Int>()
        for (table in TABLES) {
            val rows = tables.optJSONArray(table) ?: JSONArray()
            for (i in 0 until rows.length()) {
                val values = contentValues(rows.getJSONObject(i))
                val id = sql.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                require(id != -1L) {
                    "Таблица $table не загрузилась: похоже, копия сделана другой версией приложения"
                }
            }
            inserted[table] = rows.length()
        }
        BackupCounts(inserted)
    }

    /** Сколько записей сейчас в базе. */
    suspend fun counts(db: AppDatabase): BackupCounts = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        BackupCounts(TABLES.associateWith { table -> countRows(sql, table) })
    }

    /** Проверяет файл и считает записи по таблицам, ничего не меняя. */
    fun countsOf(json: String): BackupCounts {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Файл не похож на резервную копию", e)
        }
        require(root.optString("app") == APP && root.optInt("format") == FORMAT) {
            "Это не файл резервной копии «Моих напитков»"
        }
        val tables = root.optJSONObject("tables")
            ?: throw IllegalArgumentException("В файле нет данных")
        return BackupCounts(TABLES.associateWith { tables.optJSONArray(it)?.length() ?: 0 })
    }

    private fun readTable(db: SupportSQLiteDatabase, table: String): JSONArray {
        val rows = JSONArray()
        db.query("SELECT * FROM $table").use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val column = cursor.getColumnName(i)
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(column, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(column, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(column, cursor.getDouble(i))
                        else -> row.put(column, cursor.getString(i))
                    }
                }
                rows.put(row)
            }
        }
        return rows
    }

    private fun countRows(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun contentValues(row: JSONObject): ContentValues {
        val values = ContentValues()
        val keys = row.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Числа кладём тем же типом, что пришли: целые — INTEGER, дробные — REAL.
            when (val value = row.get(key)) {
                null, JSONObject.NULL -> values.putNull(key)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Double -> values.put(key, value)
                is Boolean -> values.put(key, if (value) 1 else 0)
                else -> values.put(key, value.toString())
            }
        }
        return values
    }
}
