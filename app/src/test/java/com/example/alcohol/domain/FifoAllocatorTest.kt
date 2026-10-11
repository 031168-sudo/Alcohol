package com.example.alcohol.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FifoAllocatorTest {

    private val lots = listOf(
        Lot(purchaseId = 1, remaining = 100.0, unitPrice = 0.1),
        Lot(purchaseId = 2, remaining = 200.0, unitPrice = 0.2),
    )

    @Test
    fun takesOldestLotFirstAndSplitsAcrossLots() {
        val result = FifoAllocator.allocate(lots, need = 150.0)

        assertEquals(2, result.allocations.size)
        assertEquals(100.0, result.allocations[0].amount, 1e-9)
        assertEquals(10.0, result.allocations[0].cost, 1e-9)
        assertEquals(50.0, result.allocations[1].amount, 1e-9)
        assertEquals(10.0, result.allocations[1].cost, 1e-9)
        assertEquals(0.0, result.shortfall, 1e-9)
    }

    @Test
    fun reportsShortfallWhenStockIsNotEnough() {
        val result = FifoAllocator.allocate(lots, need = 400.0)

        assertEquals(100.0, result.shortfall, 1e-9)
        assertEquals(300.0, result.allocations.sumOf { it.amount }, 1e-9)
    }

    @Test
    fun skipsEmptyLots() {
        val withEmpty = listOf(Lot(3, 0.0, 9.0)) + lots
        val result = FifoAllocator.allocate(withEmpty, need = 50.0)

        assertEquals(1, result.allocations.size)
        assertEquals(1L, result.allocations[0].purchaseId)
    }
}
