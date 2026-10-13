package com.example.alcohol.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ручное списание со склада (испортилось, разбилось и т.п.).
 * Одна строка — это списание из одной закупки; общее списание может состоять из нескольких строк.
 */
@Entity(tableName = "write_offs")
data class WriteOff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val purchaseId: Long,
    val writtenOffAt: Long,
    val amount: Double,
    val cost: Double,
    val reason: String,
)

/** Одна варка, которая списала этот ингредиент: сумма по варке. */
data class BrewUsageRow(
    val batchName: String,
    val startedAt: Long,
    val amount: Double,
    val cost: Double,
)
