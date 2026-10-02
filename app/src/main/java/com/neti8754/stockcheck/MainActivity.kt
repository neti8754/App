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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

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
    fun update(transform: (AppData) -> AppData) = scope.launch {
        val current = data.first()
        repo.save(transform(current))
    }
    fun setCheck(template: Template, index: Int, status: String) = update { old ->
        val checks = old.checks.toMutableMap()
        checks["${template.id}:${index}"] = status
        val next = old.copy(checks = checks)
        next.copy(shopping = ShoppingLogic.rebuildShopping(next))
    }
    fun addTemplate(name: String) = update { it.copy(templates = it.templates + Template(name = name.trim())) }
    fun deleteTemplate(id: String) = update { it.copy(templates = it.templates.filterNot { t -> t.id == id }) }
    fun addItem(template: Template, item: String) = update { old ->
        old.copy(templates = old.templates.map { t -> if (t.id == template.id) t.copy(items = t.items + item.trim()) else t })
    }
    fun removeItem(template: Template, item: String) = update { old ->
        old.copy(templates = old.templates.map { t -> if (t.id == template.id) t.copy(items = t.items.filterNot { x -> x == item }) else t })
    }
    fun addShopping(name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old else old.copy(shopping = (old.shopping + ShoppingItem(clean)).distinctBy { it.name.lowercase() })
    }
    fun togglePurchased(name: String) = update { old -> old.copy(shopping = old.shopping.map { if (it.name == name) it.copy(purchased = !it.purchased) else it }) }
    fun removeShopping(name: String) = update { old -> old.copy(shopping = old.shopping.filterNot { it.name == name }) }
    fun clearPurchased() = update { old -> old.copy(shopping = old.shopping.filterNot { it.purchased }) }
    fun refreshShopping() = update { old -> old.copy(shopping = ShoppingLogic.rebuildShopping(old)) }
    companion object {
        fun factory(context: android.content.Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StockViewModel(Repository(context)) as T
        }
    }
    override fun onCleared() { scope.cancel() }
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
                        NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("רשימות") })
                        NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("קניות") })
                    }
                }
            ) { padding -> if (tab == 0) InventoryScreen(data, vm, Modifier.padding(padding)) else ShoppingScreen(data, vm, Modifier.padding(padding)) }
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
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("חיפוש רשימה") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            IconButton({ showAdd = true }) { Icon(Icons.Default.Add, "הוספת רשימה") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.templates.filter { it.name.contains(search, true) }, key = { it.id }) { template ->
                Card(onClick = { selectedId = template.id }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(template.name, fontSize = 18.sp); Text("${template.items.size} פריטים", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.Default.Edit, null)
                    }
                }
            }
        }
    }
    if (showAdd) TextInputDialog("רשימה חדשה", "שם הרשימה", "", { value -> if (value.isNotBlank()) vm.addTemplate(value); showAdd = false }, { showAdd = false })
}

@Composable
private fun InventoryDetail(data: AppData, template: Template, vm: StockViewModel, onBack: () -> Unit, modifier: Modifier) {
    var newItem by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("חזרה") }
            Text(template.name, style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(template.items.indices.toList(), key = { it }) { index ->
                val item = template.items[index]
                val status = data.checks["${template.id}:${index}"] ?: ShoppingLogic.NOT_CHECKED
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusButton("יש", status == ShoppingLogic.PRESENT) { vm.setCheck(template, index, ShoppingLogic.PRESENT) }
                            StatusButton("חסר", status == ShoppingLogic.MISSING) { vm.setCheck(template, index, ShoppingLogic.MISSING) }
                            StatusButton("לא נבדק", status == ShoppingLogic.NOT_CHECKED) { vm.setCheck(template, index, ShoppingLogic.NOT_CHECKED) }
                            IconButton({ vm.removeItem(template, item) }) { Icon(Icons.Default.Delete, "מחיקה") }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newItem, { newItem = it }, Modifier.weight(1f), label = { Text("פריט חדש") }, singleLine = true)
            IconButton({ if (newItem.isNotBlank()) { vm.addItem(template, newItem); newItem = "" } }) { Icon(Icons.Default.Add, "הוספה") }
        }
        TextButton(onClick = { vm.deleteTemplate(template.id); onBack() }) { Text("מחיקת רשימה") }
    }
}

@Composable private fun StatusButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick) { Text(label) } else OutlinedButton(onClick) { Text(label) }
}

@Composable
private fun ShoppingScreen(data: AppData, vm: StockViewModel, modifier: Modifier) {
    var newItem by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("רשימת קניות", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(vm::refreshShopping) { Icon(Icons.Default.Refresh, "רענון") }
            TextButton(vm::clearPurchased) { Text("נקה שנקנו") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newItem, { newItem = it }, Modifier.weight(1f), label = { Text("הוספת פריט") }, singleLine = true)
            IconButton({ if (newItem.isNotBlank()) { vm.addShopping(newItem); newItem = "" } }) { Icon(Icons.Default.Add, "הוספה") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.shopping, key = { it.name }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.purchased, { vm.togglePurchased(item.name) })
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            if (item.sources.isNotEmpty()) Text("מקור: ${item.sources.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.removeShopping(item.name) }) { Icon(Icons.Default.Delete, "מחיקה") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, label: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton({ onConfirm(value) }) { Text("שמירה") } }, dismissButton = { TextButton(onDismiss) { Text("ביטול") } })
}
