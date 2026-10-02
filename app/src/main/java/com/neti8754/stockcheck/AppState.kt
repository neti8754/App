package com.neti8754.stockcheck

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class Template(val id: String = UUID.randomUUID().toString(), val name: String, val items: List<String> = emptyList())
@Serializable data class ShoppingItem(val name: String, val sources: List<String> = emptyList(), val purchased: Boolean = false)
@Serializable data class AppData(val templates: List<Template> = defaultTemplates(), val checks: Map<String, String> = emptyMap(), val shopping: List<ShoppingItem> = emptyList())

fun defaultTemplates() = listOf(
    Template(name = "מקלחת", items = listOf("שמפו", "סבון", "משחת שיניים", "נייר טואלט")),
    Template(name = "שבת", items = listOf("חלה", "יין", "נרות שבת", "דגים", "שתייה")),
    Template(name = "קניות לבית", items = listOf("לחם", "חלב", "ביצים", "ירקות", "פירות")),
    Template(name = "נסיעה", items = listOf("מטען", "בקבוק מים", "תעודה מזהה", "תרופות")),
    Template(name = "ניקיון", items = listOf("נוזל רצפות", "ספוגים", "שקיות אשפה", "נייר מגבת"))
)

object ShoppingLogic {
    const val NOT_CHECKED = "not_checked"
    const val PRESENT = "present"
    const val MISSING = "missing"
    fun rebuildShopping(data: AppData): List<ShoppingItem> {
        val missing = linkedMapOf<String, MutableSet<String>>()
        data.templates.forEach { template ->
            template.items.forEachIndexed { index, item ->
                val key = "${template.id}:${index}"
                if (data.checks[key] == MISSING) missing.getOrPut(item.trim()) { linkedSetOf() }.add(template.name)
            }
        }
        return missing.map { (name, sources) -> ShoppingItem(name = name, sources = sources.toList()) }
    }
}
