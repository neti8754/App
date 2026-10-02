package com.neti8754.stockcheck

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingLogicTest {
    @Test fun missingItemsAreDeduplicatedAndKeepSources() {
        val a = Template(id = "a", name = "שבת", items = listOf("חלב", "לחם"))
        val b = Template(id = "b", name = "בית", items = listOf("חלב", "ביצים"))
        val data = AppData(templates = listOf(a, b), checks = mapOf("a:0" to ShoppingLogic.MISSING, "b:0" to ShoppingLogic.MISSING, "b:1" to ShoppingLogic.MISSING))
        val result = ShoppingLogic.rebuildShopping(data)
        assertEquals(2, result.size)
        assertEquals(setOf("שבת", "בית"), result.first { it.name == "חלב" }.sources.toSet())
    }
    @Test fun presentItemsDoNotEnterShoppingList() {
        val t = Template(id = "a", name = "בית", items = listOf("חלב"))
        val data = AppData(templates = listOf(t), checks = mapOf("a:0" to ShoppingLogic.PRESENT))
        assertEquals(0, ShoppingLogic.rebuildShopping(data).size)
    }
}
