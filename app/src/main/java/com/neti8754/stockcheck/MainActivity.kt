package com.neti8754.stockcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, StockViewModel.factory(applicationContext))[StockViewModel::class.java]
        setContent { StockCheckApp(vm) }
    }
}

class StockViewModel(private val repo: Repository) : ViewModel() {
    val data = repo.data
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    private fun update(transform: (AppData) -> AppData) = scope.launch {
        updateMutex.withLock {
            repo.save(transform(data.first()))
        }
    }

    fun setCheck(templateId: String, itemId: String, status: String) = update { old ->
        val checks = old.checks.toMutableMap()
        checks[ShoppingLogic.checkKey(templateId, itemId)] = status
        val next = old.copy(checks = checks)
        next.copy(shopping = ShoppingLogic.rebuildShopping(next))
    }

    fun addTemplate(name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old else old.copy(templates = old.templates + Template(name = clean))
    }

    fun renameTemplate(id: String, name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old
        else {
            val next = old.copy(templates = old.templates.map { if (it.id == id) it.copy(name = clean) else it })
            next.copy(shopping = ShoppingLogic.rebuildShopping(next))
        }
    }

    fun duplicateTemplate(templateId: String) = update { old ->
        val source = old.templates.firstOrNull { it.id == templateId } ?: return@update old
        val duplicate = Template(
            name = "${source.name} — עותק",
            items = source.items.map { ChecklistItem(name = it.name) }
        )
        old.copy(templates = old.templates + duplicate)
    }

    fun deleteTemplate(id: String) = update { old ->
        val checks = old.checks.filterKeys { !it.startsWith("${id}:") }
        val next = old.copy(
            templates = old.templates.filterNot { it.id == id },
            checks = checks
        )
        next.copy(shopping = ShoppingLogic.rebuildShopping(next))
    }

    fun addItem(templateId: String, item: String) = update { old ->
        val clean = item.trim()
        if (clean.isEmpty()) old
        else old.copy(
            templates = old.templates.map {
                if (it.id == templateId) it.copy(items = it.items + ChecklistItem(name = clean)) else it
            }
        )
    }

    fun renameItem(templateId: String, itemId: String, name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old
        else {
            val next = old.copy(templates = old.templates.map { template ->
                if (template.id != templateId) template
                else template.copy(items = template.items.map { item -> if (item.id == itemId) item.copy(name = clean) else item })
            })
            next.copy(shopping = ShoppingLogic.rebuildShopping(next))
        }
    }

    fun removeItem(templateId: String, itemId: String) = update { old ->
        val checks = old.checks.toMutableMap().apply { remove(ShoppingLogic.checkKey(templateId, itemId)) }
        val next = old.copy(
            templates = old.templates.map {
                if (it.id == templateId) it.copy(items = it.items.filterNot { item -> item.id == itemId }) else it
            },
            checks = checks
        )
        next.copy(shopping = ShoppingLogic.rebuildShopping(next))
    }

    fun addShopping(name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old
        else {
            val normalized = ShoppingLogic.normalizeName(clean)
            val existing = old.shopping.firstOrNull { ShoppingLogic.normalizeName(it.name) == normalized }
            val shopping = if (existing == null) {
                old.shopping + ShoppingItem(name = clean, manual = true)
            } else {
                old.shopping.map { item ->
                    if (ShoppingLogic.normalizeName(item.name) == normalized) item.copy(manual = true) else item
                }
            }
            old.copy(shopping = shopping)
        }
    }

    fun togglePurchased(name: String) = update { old ->
        val normalized = ShoppingLogic.normalizeName(name)
        old.copy(shopping = old.shopping.map {
            if (ShoppingLogic.normalizeName(it.name) == normalized) it.copy(purchased = !it.purchased) else it
        })
    }

    fun removeShopping(name: String) = update { old ->
        val normalized = ShoppingLogic.normalizeName(name)
        old.copy(shopping = old.shopping.filterNot { ShoppingLogic.normalizeName(it.name) == normalized })
    }

    fun clearPurchased() = update { old ->
        old.copy(shopping = old.shopping.filterNot { it.purchased })
    }

    fun refreshShopping() = update { old ->
        old.copy(shopping = ShoppingLogic.rebuildShopping(old))
    }

    companion object {
        fun factory(context: android.content.Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StockViewModel(Repository(context)) as T
        }
    }

    override fun onCleared() {
        scope.cancel()
    }
}

@Composable
fun StockCheckApp(vm: StockViewModel) {
    val data by vm.data.collectAsStateWithLifecycle(initialValue = AppData())
    var tab by rememberSaveable { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Scaffold(
                topBar = { TopAppBar(title = { Text("בדיקת מלאי") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.List, contentDescription = null) },
                            label = { Text("רשימות") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                            label = { Text("קניות") }
                        )
                    }
                }
            ) { padding ->
                if (tab == 0) InventoryScreen(data, vm, Modifier.padding(padding))
                else ShoppingScreen(data, vm, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun InventoryScreen(data: AppData, vm: StockViewModel, modifier: Modifier) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val selected = data.templates.firstOrNull { it.id == selectedId }
    if (selected != null) {
        InventoryDetail(data, selected, vm, { selectedId = null }, modifier)
        return
    }

    val filtered = data.templates.filter { it.name.contains(search.trim(), ignoreCase = true) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                label = { Text("חיפוש רשימה") },
                singleLine = true,
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, "ניקוי חיפוש") } }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "הוספת רשימה")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { template ->
                Card(onClick = { selectedId = template.id }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, fontSize = 18.sp)
                            Text(
                                "${template.items.size} פריטים",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = "רשימה חדשה",
            label = "שם הרשימה",
            initial = "",
            onConfirm = { value ->
                if (value.isNotBlank()) vm.addTemplate(value)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun InventoryDetail(
    data: AppData,
    template: Template,
    vm: StockViewModel,
    onBack: () -> Unit,
    modifier: Modifier
) {
    var newItem by rememberSaveable(template.id) { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ChecklistItem?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("חזרה") }
            Spacer(Modifier.width(4.dp))
            Text(template.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, contentDescription = "שינוי שם הרשימה")
            }
            IconButton(onClick = { vm.duplicateTemplate(template.id) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "שכפול רשימה")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(template.items, key = { it.id }) { item ->
                val status = data.checks[ShoppingLogic.checkKey(template.id, item.id)] ?: ShoppingLogic.NOT_CHECKED
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editingItem = item }) {
                                Icon(Icons.Default.Edit, contentDescription = "שינוי שם פריט")
                            }
                            IconButton(onClick = { vm.removeItem(template.id, item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "מחיקת פריט")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusButton("יש", status == ShoppingLogic.PRESENT) {
                                vm.setCheck(template.id, item.id, ShoppingLogic.PRESENT)
                            }
                            StatusButton("חסר", status == ShoppingLogic.MISSING) {
                                vm.setCheck(template.id, item.id, ShoppingLogic.MISSING)
                            }
                            StatusButton("לא נבדק", status == ShoppingLogic.NOT_CHECKED) {
                                vm.setCheck(template.id, item.id, ShoppingLogic.NOT_CHECKED)
                            }
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                label = { Text("פריט חדש") },
                singleLine = true
            )
            IconButton(onClick = {
                if (newItem.isNotBlank()) {
                    vm.addItem(template.id, newItem)
                    newItem = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "הוספת פריט")
            }
        }

        TextButton(onClick = { vm.deleteTemplate(template.id); onBack() }) {
            Text("מחיקת רשימה")
        }
    }

    if (showRename) {
        TextInputDialog(
            title = "שינוי שם הרשימה",
            label = "שם הרשימה",
            initial = template.name,
            onConfirm = { value ->
                if (value.isNotBlank()) vm.renameTemplate(template.id, value)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    editingItem?.let { item ->
        TextInputDialog(
            title = "שינוי שם פריט",
            label = "שם הפריט",
            initial = item.name,
            onConfirm = { value ->
                if (value.isNotBlank()) vm.renameItem(template.id, item.id, value)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }
}

@Composable
private fun StatusButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ShoppingScreen(data: AppData, vm: StockViewModel, modifier: Modifier) {
    var newItem by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    val filtered = data.shopping.filter {
        it.name.contains(search.trim(), ignoreCase = true) ||
            it.sources.any { source -> source.contains(search.trim(), ignoreCase = true) }
    }
    val openCount = data.shopping.count { !it.purchased }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("רשימת קניות", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("${openCount} לקנייה", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                label = { Text("הוספת פריט") },
                singleLine = true
            )
            IconButton(onClick = {
                if (newItem.isNotBlank()) {
                    vm.addShopping(newItem)
                    newItem = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "הוספת פריט")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                label = { Text("חיפוש") },
                singleLine = true,
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, "ניקוי חיפוש") } }
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = vm::refreshShopping) {
                Icon(Icons.Default.Refresh, contentDescription = "רענון")
            }
            TextButton(onClick = vm::clearPurchased) {
                Text("נקה שנקנו")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.shopping.filter { item ->
                item.name.contains(search.trim(), ignoreCase = true) ||
                    item.sources.any { source -> source.contains(search.trim(), ignoreCase = true) }
            }, key = { ShoppingLogic.normalizeName(it.name) }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.purchased,
                            onCheckedChange = { vm.togglePurchased(item.name) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            if (item.sources.isNotEmpty()) {
                                Text(
                                    "מקור: ${item.sources.joinToString(", ")}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.manual) {
                            Text("ידני", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                        }
                        IconButton(onClick = { vm.removeShopping(item.name) }) {
                            Icon(Icons.Default.Delete, contentDescription = "מחיקת פריט")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("שמירה") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}
