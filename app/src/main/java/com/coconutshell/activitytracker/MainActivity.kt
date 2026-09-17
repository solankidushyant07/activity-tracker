package com.coconutshell.activitytracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.core.content.FileProvider
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.coconutshell.activitytracker.domain.model.*
import com.coconutshell.activitytracker.export.ExportService
import com.coconutshell.activitytracker.viewmodel.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val Primary = Color(0xFF5E5CE6)
private val Background = Color(0xFFF8F7FC)

class MainActivity : ComponentActivity() {
    lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppContainer(applicationContext)
        setContent { ActivityTrackerApp(container) }
    }
}

@Composable
fun ActivityTrackerApp(container: AppContainer) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Primary,
            background = Background,
            surface = Color.White,
            secondary = Color(0xFF00897B)
        )
    ) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    container = container,
                    onThing = { nav.navigate("thing/$it") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("thing/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                ThingDetailsScreen(
                    container = container,
                    id = back.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(container = container, onBack = { nav.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    container: AppContainer,
    onThing: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeFactory(container.things))
    val allThings by vm.thingsState.collectAsState()
    val libraries by container.libraries.observeLibraries().collectAsState(initial = emptyList())
    var selectedLibrary by rememberSaveable { mutableStateOf<Long?>(null) }
    val libraryThingIds by container.libraries.observeThingIds(selectedLibrary ?: -1L).collectAsState(initial = emptyList())
    val things = if (selectedLibrary == null) allThings else allThings.filter { it.id in libraryThingIds }
    val query by vm.query.collectAsState()
    val scope = rememberCoroutineScope()
    var createOpen by remember { mutableStateOf(false) }
    var recordThing by remember { mutableStateOf<Thing?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Activity Tracker", fontWeight = FontWeight.Bold)
                        Text(
                            "Capture the moment. Keep the history.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createOpen = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New thing") },
                containerColor = Primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your things") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ vm.setQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            if (libraries.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLibrary == null,
                            onClick = { selectedLibrary = null },
                            label = { Text("All") }
                        )
                        libraries.take(3).forEach { library ->
                            FilterChip(
                                selected = selectedLibrary == library.id,
                                onClick = { selectedLibrary = library.id },
                                label = { Text(library.name) }
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your things", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${things.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (things.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "Start with one thing" else "Nothing matches",
                        body = if (query.isBlank()) "Create anything you want to remember. There are no predefined habits." else "Try a different name."
                    )
                }
            } else {
                items(things, key = { it.id }) { thing ->
                    ThingCard(
                        thing = thing,
                        onOpen = { onThing(thing.id) },
                        onRecord = { recordThing = thing }
                    )
                }
            }
        }
    }

    if (createOpen) {
        CreateThingDialog(
            onDismiss = { createOpen = false },
            onCreate = { name ->
                vm.create(name) { id ->
                    createOpen = false
                    onThing(id)
                }
            }
        )
    }
    recordThing?.let { thing ->
        QuickRecordSheet(
            thing = thing,
            onDismiss = { recordThing = null },
            onRecord = { at, qty ->
                scope.launch {
                    container.occurrences.record(thing.id, at, qty)
                    recordThing = null
                }
            }
        )
    }
}

@Composable
private fun ThingCard(thing: Thing, onOpen: () -> Unit, onRecord: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Primary.copy(alpha = .10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, null, tint = Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(thing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Tap to see history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = onRecord,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Add, "Record")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✦", fontSize = 42.sp, color = Primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateThingDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a thing") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Read, Walk, Journal") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickRecordSheet(thing: Thing, onDismiss: () -> Unit, onRecord: (Long, Double) -> Unit) {
    var whenMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var quantity by remember { mutableStateOf("1") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp).navigationBarsPadding()) {
            Text("Record ${thing.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Save the actual time it happened.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = { whenMillis = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(formatDateTime(whenMillis))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = quantity,
                onValueChange = { if (it.matches(Regex("\\d*(\\.\\d*)?"))) quantity = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onRecord(whenMillis, quantity.toDoubleOrNull() ?: 1.0) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Record now", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ThingDetailsScreen(container: AppContainer, id: Long, onBack: () -> Unit) {
    val vm: ThingDetailsViewModel = viewModel(factory = ThingDetailsFactory(container.things, container.occurrences, container.libraries, id))
    val thing by vm.thing.collectAsState()
    val history by vm.history.collectAsState()
    val libraryIds by vm.libraryIds.collectAsState()
    val libraries by vm.allLibraries.collectAsState()
    var recordOpen by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Occurrence?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(thing?.name ?: "Thing", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, "Delete thing") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { recordOpen = true }, containerColor = Primary, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Record")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = .08f)), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${history.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("recorded occurrences", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Timeline, null, tint = Primary, modifier = Modifier.size(40.dp))
                    }
                }
            }
            if (libraries.isNotEmpty()) {
                item {
                    Text("Libraries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        libraries.forEach { library ->
                            FilterChip(
                                selected = library.id in libraryIds,
                                onClick = { vm.toggleLibrary(library.id, library.id !in libraryIds) },
                                label = { Text(library.name) }
                            )
                        }
                    }
                }
            }
            item {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (history.isEmpty()) {
                item { EmptyState("Nothing recorded yet", "Tap + whenever this happens.") }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        onEdit = { edit = item },
                        onDelete = { vm.deleteOccurrence(item) }
                    )
                }
            }
        }
    }

    if (recordOpen && thing != null) {
        QuickRecordSheet(
            thing = thing!!,
            onDismiss = { recordOpen = false },
            onRecord = { at, qty -> vm.record(at, qty) { recordOpen = false } }
        )
    }
    edit?.let { item ->
        EditOccurrenceDialog(item, onDismiss = { edit = null }) { updated ->
            vm.updateOccurrence(updated)
            edit = null
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete ${thing?.name ?: "thing"}?") },
            text = { Text("Its recorded occurrences and library links will also be removed.") },
            confirmButton = {
                Button(onClick = { vm.deleteThing(onBack); deleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryRow(item: Occurrence, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(item.occurredAt), fontWeight = FontWeight.SemiBold)
                if (item.quantity != 1.0) Text("Quantity ${item.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete") }
        }
    }
}

@Composable
private fun EditOccurrenceDialog(item: Occurrence, onDismiss: () -> Unit, onSave: (Occurrence) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var quantity by remember { mutableStateOf(item.quantity.toString()) }
    var occurredAt by remember { mutableLongStateOf(item.occurredAt) }
    val initial = remember(item.occurredAt) { java.util.Calendar.getInstance().apply { timeInMillis = item.occurredAt } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit occurrence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = occurredAt }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val dateCal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = occurredAt
                                    set(year, month, day)
                                }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        dateCal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                                        dateCal.set(java.util.Calendar.MINUTE, minute)
                                        dateCal.set(java.util.Calendar.SECOND, 0)
                                        dateCal.set(java.util.Calendar.MILLISECOND, 0)
                                        occurredAt = dateCal.timeInMillis
                                    },
                                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                                    cal.get(java.util.Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            initial.get(java.util.Calendar.YEAR),
                            initial.get(java.util.Calendar.MONTH),
                            initial.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDateTime(occurredAt))
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(item.copy(occurredAt = occurredAt, quantity = quantity.toDoubleOrNull() ?: item.quantity))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsFactory(com.coconutshell.activitytracker.settings.SettingsStore(context), com.coconutshell.activitytracker.reminder.ReminderScheduler(context)))
    val settings by vm.settings.collectAsState()
    val morning = settings.morningEnabled
    val night = settings.nightEnabled
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                SettingRow("Morning reminder", "Start the day with a quick check-in.", morning) { vm.toggleMorning(it) }
            }
            item {
                SettingRow("Night reminder", "Close the day while it is fresh.", night) { vm.toggleNight(it) }
            }
            item { Spacer(Modifier.height(10.dp)); Text("Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Export activity") },
                        supportingContent = { Text("Creates a ZIP containing readable Markdown history.") },
                        leadingContent = { Icon(Icons.Default.FileDownload, null) },
                        trailingContent = {
                            TextButton(onClick = {
    scope.launch {
        runCatching {
            ExportService(context, container.things, container.occurrences).export()
        }.onSuccess { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Export activity"))
            exportMessage = "ActivityTracker.zip is ready to share."
        }.onFailure { error ->
            exportMessage = "Export failed: ${error.message ?: "unknown error"}"
        }
    }
}) { Text("Export") }
                        }
                    )
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("About Activity Tracker") },
                        supportingContent = { Text("Local-first. Your vocabulary. Your history.") },
                        leadingContent = { Icon(Icons.Default.FavoriteBorder, null, tint = Primary) }
                    )
                }
            }
            exportMessage?.let { msg ->
                item { Text(msg, color = Primary, modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(body) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = onChange) }
        )
    }
}

private fun formatDateTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
}
