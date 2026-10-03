package com.neti8754.stockcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLogicTest {
    @Test
    fun missingItemsAreDeduplicatedAndKeepSources() {
        val aItem = ChecklistItem(id = "a1", name = "חלב")
        val a = Template(id = "a", name = "שבת", items = listOf(aItem, ChecklistItem(id = "a2", name = "לחם")))
        val b = Template(id = "b", name = "בית", items = listOf(
            ChecklistItem(id = "b1", name = "חלב"),
            ChecklistItem(id = "b2", name = "ביצים")
        ))
        val data = AppData(
            templates = listOf(a, b),
            checks = mapOf(
                ShoppingLogic.checkKey("a", "a1") to ShoppingLogic.MISSING,
                ShoppingLogic.checkKey("b", "b1") to ShoppingLogic.MISSING,
                ShoppingLogic.checkKey("b", "b2") to ShoppingLogic.MISSING
            )
        )

        val result = ShoppingLogic.rebuildTasks(data)

        assertEquals(2, result.size)
        assertEquals(setOf("שבת", "בית"), result.first { ShoppingLogic.normalizeName(it.name) == "חלב" }.sources.toSet())
    }

    @Test
    fun presentItemsDoNotEnterTasks() {
        val item = ChecklistItem(id = "a1", name = "חלב")
        val t = Template(id = "a", name = "בית", items = listOf(item))
        val data = AppData(
            templates = listOf(t),
            checks = mapOf(ShoppingLogic.checkKey("a", "a1") to ShoppingLogic.PRESENT)
        )
        assertEquals(0, ShoppingLogic.rebuildTasks(data).size)
    }

    @Test
    fun manualTaskItemsSurviveAutomaticRefresh() {
        val item = ChecklistItem(id = "a1", name = "חלב")
        val t = Template(id = "a", name = "בית", items = listOf(item))
        val data = AppData(
            templates = listOf(t),
            checks = mapOf(ShoppingLogic.checkKey("a", "a1") to ShoppingLogic.MISSING),
            tasks = listOf(TaskItem(name = "בטריות", manual = true))
        )

        val result = ShoppingLogic.rebuildTasks(data)

        assertTrue(result.any { ShoppingLogic.normalizeName(it.name) == "בטריות" && it.manual })
        assertTrue(result.any { ShoppingLogic.normalizeName(it.name) == "חלב" })
    }

    @Test
    fun removingAnEarlierItemDoesNotChangeAnotherItemsCheck() {
        val first = ChecklistItem(id = "first", name = "לחם")
        val second = ChecklistItem(id = "second", name = "חלב")
        val template = Template(id = "t", name = "בית", items = listOf(first, second))
        val data = AppData(
            templates = listOf(template),
            checks = mapOf(ShoppingLogic.checkKey("t", "second") to ShoppingLogic.MISSING)
        )

        val afterRemoval = template.copy(items = listOf(second))
        val result = ShoppingLogic.rebuildTasks(data.copy(templates = listOf(afterRemoval)))

        assertEquals(listOf("חלב"), result.map { it.name })
    }
}
