package com.neti8754.stockcheck

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

@Serializable
data class Template(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<ChecklistItem> = emptyList()
)

@Serializable
data class ShoppingItem(
    val name: String,
    val sources: List<String> = emptyList(),
    val purchased: Boolean = false,
    val manual: Boolean = false
)

@Serializable
data class AppData(
    val templates: List<Template> = defaultTemplates(),
    val checks: Map<String, String> = emptyMap(),
    val shopping: List<ShoppingItem> = emptyList()
)

fun defaultTemplates() = listOf(
    Template(name = "מקלחת", items = listOf("שמפו", "סבון", "משחת שיניים", "נייר טואלט").map(::ChecklistItem)),
    Template(name = "שבת", items = listOf("חלה", "יין", "נרות שבת", "דגים", "שתייה").map(::ChecklistItem)),
    Template(name = "קניות לבית", items = listOf("לחם", "חלב", "ביצים", "ירקות", "פירות").map(::ChecklistItem)),
    Template(name = "נסיעה", items = listOf("מטען", "בקבוק מים", "תעודה מזהה", "תרופות").map(::ChecklistItem)),
    Template(name = "ניקיון", items = listOf("נוזל רצפות", "ספוגים", "שקיות אשפה", "נייר מגבת").map(::ChecklistItem))
)

object ShoppingLogic {
    const val NOT_CHECKED = "not_checked"
    const val PRESENT = "present"
    const val MISSING = "missing"

    private fun key(templateId: String, itemId: String) = "$templateId:$itemId"

    fun normalizeName(name: String) = name.trim().replace(Regex("\s+"), " ").lowercase()

    fun rebuildShopping(data: AppData): List<ShoppingItem> {
        val missing = linkedMapOf<String, MutableSet<String>>()

        data.templates.forEach { template ->
            template.items.forEach { item ->
                if (data.checks[key(template.id, item.id)] == MISSING) {
                    val cleanName = item.name.trim()
                    if (cleanName.isNotEmpty()) {
                        missing.getOrPut(normalizeName(cleanName)) { linkedSetOf() }.add(template.name)
                    }
                }
            }
        }

        val existing = data.shopping.associateBy { normalizeName(it.name) }
        val rebuilt = missing.map { (normalizedName, sources) ->
            val previous = existing[normalizedName]
            ShoppingItem(
                name = previous?.name ?: normalizedName,
                sources = sources.toList(),
                purchased = previous?.purchased ?: false,
                manual = previous?.manual ?: false
            )
        }

        val autoNames = missing.keys
        val manualOnly = data.shopping.filter { it.manual && normalizeName(it.name) !in autoNames }
        return rebuilt + manualOnly
    }

    fun checkKey(templateId: String, itemId: String) = key(templateId, itemId)
}
