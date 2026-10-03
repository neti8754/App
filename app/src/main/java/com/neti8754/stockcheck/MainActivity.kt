@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neti8754.stockcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neti8754.stockcheck.ui.components.*
import com.neti8754.stockcheck.ui.theme.AppSpacing
import com.neti8754.stockcheck.ui.theme.StockCheckTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private fun update(transform: (AppData) -> AppData) = update(transform, null)

    private fun update(transform: (AppData) -> AppData, message: String?) = scope.launch {
        updateMutex.withLock {
            val next = transform(data.first())
            repo.save(next)
            if (message != null) eventChannel.send(message)
        }
    }

    fun setCheck(templateId: String, itemId: String, status: String) = update({ old ->
        val checks = old.checks.toMutableMap()
        checks[ShoppingLogic.checkKey(templateId, itemId)] = status
        val next = old.copy(checks = checks)
        next.copy(tasks = ShoppingLogic.rebuildTasks(next))
    }, when (status) {
        ShoppingLogic.PRESENT -> "סומן כקיים"
        ShoppingLogic.MISSING -> "נוסף למשימות"
        else -> "הסימון אופס"
    })

    fun addTemplate(name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old else old.copy(templates = old.templates + Template(name = clean))
    }

    fun renameTemplate(id: String, name: String) = update({ old ->
        val clean = name.trim()
        if (clean.isEmpty()) old
        else {
            val next = old.copy(templates = old.templates.map { if (it.id == id) it.copy(name = clean) else it })
            next.copy(tasks = ShoppingLogic.rebuildTasks(next))
        }
    }, "הרשימה נשמרה")

    fun duplicateTemplate(templateId: String) = update { old ->
        val source = old.templates.firstOrNull { it.id == templateId } ?: return@update old
        old.copy(
            templates = old.templates + Template(
                name = source.name + " — עותק",
                items = source.items.map { ChecklistItem(name = it.name) }
            )
        )
    }

    fun deleteTemplate(id: String) = update({ old ->
        val next = old.copy(
            templates = old.templates.filterNot { it.id == id },
            checks = old.checks.filterKeys { !it.startsWith(id + ":") }
        )
        next.copy(tasks = ShoppingLogic.rebuildTasks(next))
    }, "הרשימה נמחקה")

    fun addItem(templateId: String, name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old else old.copy(
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
                else template.copy(items = template.items.map { item ->
                    if (item.id == itemId) item.copy(name = clean) else item
                })
            })
            next.copy(tasks = ShoppingLogic.rebuildTasks(next))
        }
    }

    fun removeItem(templateId: String, itemId: String) = update({ old ->
        val next = old.copy(
            templates = old.templates.map {
                if (it.id == templateId) it.copy(items = it.items.filterNot { item -> item.id == itemId }) else it
            },
            checks = old.checks - ShoppingLogic.checkKey(templateId, itemId)
        )
        next.copy(tasks = ShoppingLogic.rebuildTasks(next))
    }, "הפריט נמחק")

    fun addTask(name: String) = update { old ->
        val clean = name.trim()
        if (clean.isEmpty()) old
        else {
            val normalized = ShoppingLogic.normalizeName(clean)
            val exists = old.tasks.any { ShoppingLogic.normalizeName(it.name) == normalized }
            if (exists) old
            else old.copy(tasks = old.tasks + TaskItem(name = clean, manual = true))
        }
    }

    fun toggleTask(name: String) = update { old ->
        val normalized = ShoppingLogic.normalizeName(name)
        old.copy(tasks = old.tasks.map {
            if (ShoppingLogic.normalizeName(it.name) == normalized) it.copy(completed = !it.completed) else it
        })
    }

    fun removeTask(name: String) = update({ old ->
        val normalized = ShoppingLogic.normalizeName(name)
        old.copy(tasks = old.tasks.filterNot {
            it.manual && ShoppingLogic.normalizeName(it.name) == normalized
        })
    }, "המשימה נמחקה")

    fun setThemeMode(mode: String) = update { old -> old.copy(themeMode = mode) }

    companion object {
        fun factory(context: android.content.Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StockViewModel(Repository(context)) as T
        }
    }

    override fun onCleared() {
        eventChannel.close()
        scope.cancel()
    }
}

private enum class AppTab { HOME, LISTS, TASKS }

@Composable
fun StockCheckApp(vm: StockViewModel) {
    val data by vm.data.collectAsStateWithLifecycle(initialValue = AppData())
    val snackbarHostState = remember { SnackbarHostState() }
    var tabName by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val tab = AppTab.valueOf(tabName)
    val selectedTemplate = data.templates.firstOrNull { it.id == selectedTemplateId }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        StockCheckTheme(data.themeMode) {
            when {
                settingsOpen -> SettingsScreen(
                    themeMode = data.themeMode,
                    onBack = { settingsOpen = false },
                    onThemeSelected = vm::setThemeMode
                )
                selectedTemplate != null -> ChecklistScreen(
                    data = data,
                    template = selectedTemplate,
                    vm = vm,
                    onBack = { selectedTemplateId = null }
                )
                else -> Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        ScreenTopBar(
                            title = when (tab) {
                                AppTab.HOME -> "בדיקת מלאי"
                                AppTab.LISTS -> "רשימות"
                                AppTab.TASKS -> "משימות"
                            },
                            action = {
                                if (tab == AppTab.HOME) {
                                    IconButton(onClick = { settingsOpen = true }) {
                                        Icon(Icons.Default.Settings, "הגדרות")
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == AppTab.HOME,
                                onClick = { tabName = AppTab.HOME.name },
                                icon = { Icon(Icons.Default.Home, null) },
                                label = { Text("ראשי") }
                            )
                            NavigationBarItem(
                                selected = tab == AppTab.LISTS,
                                onClick = { tabName = AppTab.LISTS.name },
                                icon = { Icon(Icons.Default.List, null) },
                                label = { Text("רשימות") }
                            )
                            NavigationBarItem(
                                selected = tab == AppTab.TASKS,
                                onClick = { tabName = AppTab.TASKS.name },
                                icon = { Icon(Icons.Default.CheckCircle, null) },
                                label = { Text("משימות") }
                            )
                        }
                    }
                ) { padding ->
                    when (tab) {
                        AppTab.HOME -> HomeScreen(data, vm, { id ->
                            selectedTemplateId = id
                        }, { tabName = AppTab.TASKS.name }, Modifier.padding(padding))
                        AppTab.LISTS -> ListsScreen(data, vm, { id ->
                            selectedTemplateId = id
                        }, Modifier.padding(padding))
                        AppTab.TASKS -> TasksScreen(data, vm, Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    data: AppData,
    vm: StockViewModel,
    openTemplate: (String) -> Unit,
    openTasks: () -> Unit,
    modifier: Modifier
) {
    val openCount = data.tasks.count { !it.completed }
    val primaryTemplate = data.templates.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("מה צריך לעשות?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (openCount == 0) "אפשר להתחיל בדיקה חדשה."
                    else openCount.toString() + " משימות פתוחות.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            PrimaryActionCard(
                title = "התחל בדיקת מלאי",
                subtitle = "בדיקה רציפה, סימון מהיר, ושמירה אוטומטית.",
                icon = { Icon(Icons.Default.PlayArrow, null) },
                onClick = { primaryTemplate?.let { openTemplate(it.id) } }
            )
        }

        item {
            AppSectionTitle(
                title = "משימות פתוחות",
                actionLabel = if (openCount > 0) "הצג הכול" else null,
                onAction = openTasks
            )
        }

        val tasks = data.tasks.filter { !it.completed }.take(4)
        if (tasks.isEmpty()) {
            item { EmptyState("אין משימות פתוחות", "פריטים שיסומנו כחסרים יופיעו כאן.") }
        } else {
            items(tasks, key = { ShoppingLogic.normalizeName(it.name) }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { vm.toggleTask(task.name) },
                    compact = true
                )
            }
        }

        item { AppSectionTitle("רשימות מהירות") }

        if (data.templates.isEmpty()) {
            item { EmptyState("אין רשימות עדיין", "צרו רשימה ראשונה כדי להתחיל.") }
        } else {
            items(data.templates.take(3), key = { it.id }) { template ->
                TemplateRow(
                    template = template,
                    onClick = { openTemplate(template.id) },
                    onEdit = {},
                    onDuplicate = {},
                    onDelete = {}
                )
            }
        }
    }
}

@Composable
private fun ListsScreen(
    data: AppData,
    vm: StockViewModel,
    openTemplate: (String) -> Unit,
    modifier: Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf<Template?>(null) }
    var deleteTemplate by remember { mutableStateOf<Template?>(null) }

    val filtered = data.templates.filter { it.name.contains(search.trim(), ignoreCase = true) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTextField(
                label = "חיפוש",
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = 8.dp),
                placeholder = "חיפוש רשימה"
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        onClick = { openTemplate(template.id) },
                        onEdit = { renameTemplate = template },
                        onDuplicate = { vm.duplicateTemplate(template.id) },
                        onDelete = { deleteTemplate = template }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            if (search.isBlank()) "אין רשימות עדיין" else "אין תוצאות",
                            if (search.isBlank()) "צרו רשימה ראשונה כדי להתחיל." else "נסו מונח חיפוש אחר."
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { addDialog = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, "הוספת רשימה")
        }
    }

    if (addDialog) {
        TextInputDialog("רשימה חדשה", "שם הרשימה", "", {
            if (it.isNotBlank()) vm.addTemplate(it)
            addDialog = false
        }, { addDialog = false })
    }

    renameTemplate?.let { template ->
        TextInputDialog("שינוי שם הרשימה", "שם הרשימה", template.name, {
            if (it.isNotBlank()) vm.renameTemplate(template.id, it)
            renameTemplate = null
        }, { renameTemplate = null })
    }

    deleteTemplate?.let { template ->
        ConfirmDialog(
            "למחוק את הרשימה?",
            "הפריטים והסימונים של הרשימה יימחקו.",
            "מחיקה",
            {
                vm.deleteTemplate(template.id)
                deleteTemplate = null
            },
            { deleteTemplate = null }
        )
    }
}

@Composable
private fun ChecklistScreen(
    data: AppData,
    template: Template,
    vm: StockViewModel,
    onBack: () -> Unit
) {
    var addDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    val checked = template.items.count {
        data.checks[ShoppingLogic.checkKey(template.id, it.id)] != null &&
            data.checks[ShoppingLogic.checkKey(template.id, it.id)] != ShoppingLogic.NOT_CHECKED
    }
    val progress = if (template.items.isEmpty()) 0f else checked.toFloat() / template.items.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "חזרה") }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "פעולות")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("שינוי שם") },
                            onClick = { menu = false; renameDialog = true },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("שכפול") },
                            onClick = { menu = false; vm.duplicateTemplate(template.id) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("מחיקה") },
                            onClick = { menu = false; deleteDialog = true },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpacing.screen, vertical = 12.dp)
            ) {
                Button(
                    onClick = { addDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("הוסף פריט")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = AppSpacing.screen, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        checked.toString() + "/" + template.items.size + " נבדקו",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text((progress * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (template.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("הרשימה ריקה", "הוסיפו פריט כדי להתחיל.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(template.items, key = { it.id }) { item ->
                        val status = data.checks[
                            ShoppingLogic.checkKey(template.id, item.id)
                        ] ?: ShoppingLogic.NOT_CHECKED

                        InventoryItemRow(
                            item = item,
                            status = status,
                            onStatus = { vm.setCheck(template.id, item.id, it) },
                            onEdit = { editItem = item },
                            onDelete = { vm.removeItem(template.id, item.id) }
                        )
                    }
                }
            }
        }
    }

    if (addDialog) {
        TextInputDialog("פריט חדש", "שם הפריט", "", {
            if (it.isNotBlank()) vm.addItem(template.id, it)
            addDialog = false
        }, { addDialog = false })
    }

    if (renameDialog) {
        TextInputDialog("שינוי שם הרשימה", "שם הרשימה", template.name, {
            if (it.isNotBlank()) vm.renameTemplate(template.id, it)
            renameDialog = false
        }, { renameDialog = false })
    }

    editItem?.let { item ->
        TextInputDialog("עריכת פריט", "שם הפריט", item.name, {
            if (it.isNotBlank()) vm.renameItem(template.id, item.id, it)
            editItem = null
        }, { editItem = null })
    }

    if (deleteDialog) {
        ConfirmDialog(
            "למחוק את הרשימה?",
            "הפריטים והסימונים של הרשימה יימחקו.",
            "מחיקה",
            {
                vm.deleteTemplate(template.id)
                deleteDialog = false
                onBack()
            },
            { deleteDialog = false }
        )
    }
}

@Composable
private fun InventoryItemRow(
    item: ChecklistItem,
    status: String,
    onStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "עריכת פריט") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "מחיקת פריט") }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip("יש", status == ShoppingLogic.PRESENT) {
                    onStatus(ShoppingLogic.PRESENT)
                }
                StatusChip("חסר", status == ShoppingLogic.MISSING) {
                    onStatus(ShoppingLogic.MISSING)
                }
                StatusChip("לא נבדק", status == ShoppingLogic.NOT_CHECKED) {
                    onStatus(ShoppingLogic.NOT_CHECKED)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun TasksScreen(
    data: AppData,
    vm: StockViewModel,
    modifier: Modifier
) {
    var newTask by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    val query = search.trim()
    val filtered = data.tasks.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.sources.any { source -> source.contains(query, ignoreCase = true) }
    }
    val open = data.tasks.count { !it.completed }
    val done = data.tasks.count { it.completed }

    Column(modifier.fillMaxSize()) {
        AppTextField(
            label = "הוספת משימה",
            value = newTask,
            onValueChange = { newTask = it },
            modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = 8.dp),
            placeholder = "מה צריך לעשות?"
        )

        Row(
            Modifier.padding(horizontal = AppSpacing.screen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (newTask.isNotBlank()) {
                        vm.addTask(newTask)
                        newTask = ""
                    }
                },
                enabled = newTask.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("הוספה")
            }
            Spacer(Modifier.width(10.dp))
            Text(open.toString() + " פתוחות", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (done > 0) {
                Spacer(Modifier.width(10.dp))
                Text(done.toString() + " הושלמו", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AppTextField(
            label = "חיפוש",
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = 8.dp),
            placeholder = "חיפוש משימה"
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (query.isBlank()) "אין משימות" else "אין תוצאות",
                    if (query.isBlank()) "פריטים שיסומנו כחסרים יופיעו כאן." else "נסו חיפוש אחר."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered, key = { ShoppingLogic.normalizeName(it.name) }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { vm.toggleTask(task.name) },
                        onDelete = { vm.removeTask(task.name) },
                        showDelete = task.manual
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    compact: Boolean = false,
    onDelete: () -> Unit = {},
    showDelete: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (task.completed) 0.55f else 1f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() }
            )
            Column(Modifier.weight(1f)) {
                Text(task.name, style = MaterialTheme.typography.titleMedium)
                if (task.sources.size > 1) {
                    Text(
                        "מגיע מ־" + task.sources.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "מחיקת משימה")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    onBack: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "חזרה")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("ערכת נושא", style = MaterialTheme.typography.titleLarge)
            ThemeChoice("מערכת", ThemeMode.SYSTEM, themeMode, onThemeSelected)
            ThemeChoice("בהיר", ThemeMode.LIGHT, themeMode, onThemeSelected)
            ThemeChoice("כהה", ThemeMode.DARK, themeMode, onThemeSelected)
            HorizontalDivider()
            Text("אודות", style = MaterialTheme.typography.titleLarge)
            Text(
                "בדיקת מלאי\nגרסה 1.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    Surface(
        onClick = { onSelected(value) },
        shape = RoundedCornerShape(14.dp),
        color = if (selected == value) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected == value,
                onClick = { onSelected(value) }
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
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
            AppTextField(
                label = label,
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth()
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

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
