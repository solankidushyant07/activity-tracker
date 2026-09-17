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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.coconutshell.activitytracker.domain.model.*
import com.coconutshell.activitytracker.export.ExportService
import com.coconutshell.activitytracker.viewmodel.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util.Calendar
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
            composable(
                "thing/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { back ->
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
    val vm: HomeViewModel = viewModel(factory = HomeFactory(container.things, container.occurrences, container.libraries))
    val allThings by vm.thingsState.collectAsState()
    val libraries by vm.librariesState.collectAsState()
    val selectedLibrary by vm.selectedLibrary.collectAsState()
    val things = allThings
    val query by vm.query.collectAsState()
    var createOpen by remember { mutableStateOf(false) }
    var recordThing by remember { mutableStateOf<Thing?>(null) }
    var recordError by remember { mutableStateOf<String?>(null) }

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
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createOpen = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New thing") },
                containerColor = Primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your things") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            if (libraries.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLibrary == null,
                            onClick = { vm.setLibrary(null) },
                            label = { Text("All") }
                        )
                        libraries.forEach { library ->
                            FilterChip(
                                selected = selectedLibrary == library.id,
                                onClick = { vm.setLibrary(library.id) },
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
                    Text(
                        "Your things",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${things.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (things.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "Nothing here yet." else "No things found.",
                        body = if (query.isBlank()) {
                            "Create your first Thing to start keeping a history."
                        } else {
                            "Nothing matches \"$query\"."
                        }
                    )
                }
            } else {
                items(things, key = { it.id }) { thing ->
                    ThingCard(
                        thing = thing,
                        onOpen = { onThing(thing.id) },
                        onRecord = {
                            recordError = null
                            recordThing = thing
                        }
                    )
                }
            }

            recordError?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    if (createOpen) {
        CreateThingDialog(
            onDismiss = { createOpen = false },
            onCreate = { name ->
                vm.create(name) {
                    // Creation returns to Home; it does not unexpectedly open Details.
                    createOpen = false
                }
            }
        )
    }

    recordThing?.let { thing ->
        QuickRecordSheet(
            thing = thing,
            onDismiss = { recordThing = null },
            onRecord = { at, qty ->
                vm.record(thing.id, at, qty)
                recordThing = null
            }
        )
    }
}

@Composable
private fun ThingCard(
    thing: Thing,
    onOpen: () -> Unit,
    onRecord: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Primary.copy(alpha = .10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thing.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    "Tap to see history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onRecord,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record ${thing.name}")
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
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateThingDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Thing") },
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
            Button(
                onClick = { onCreate(name) },
                enabled = name.trim().isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickRecordSheet(
    thing: Thing,
    onDismiss: () -> Unit,
    onRecord: suspend (Long, Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var occurredAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var quantity by remember { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedQuantity = quantity.toDoubleOrNull()
    val quantityValid = parsedQuantity != null && parsedQuantity.isFinite() && parsedQuantity > 0.0

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Record ${thing.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Save when it actually happened.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            OutlinedButton(
                onClick = {
                    showDateTimePicker(
                        context = context,
                        initialMillis = occurredAt,
                        onSelected = { occurredAt = it }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(formatDateTime(occurredAt))
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    if (it.matches(Regex("""\d*(\.\d*)?"""))) quantity = it
                },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = quantity.isNotEmpty() && !quantityValid
            )

            Spacer(Modifier.height(18.dp))

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = {
                    parsedQuantity?.let { value ->
                        scope.launch {
                            saving = true
                            error = null
                            runCatching { onRecord(occurredAt, value) }
                                .onFailure { error = "Couldn't record this. Try again." }
                            saving = false
                        }
                    }
                },
                enabled = quantityValid && !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (saving) "Saving..." else "Record", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThingDetailsScreen(
    container: AppContainer,
    id: Long,
    onBack: () -> Unit
) {
    val vm: ThingDetailsViewModel = viewModel(
        factory = ThingDetailsFactory(
            container.things,
            container.occurrences,
            container.libraries,
            id
        )
    )
    val thing by vm.thing.collectAsState()
    val history by vm.history.collectAsState()
    val libraryIds by vm.libraryIds.collectAsState()
    val libraries by vm.allLibraries.collectAsState()
    val scope = rememberCoroutineScope()

    var recordOpen by remember { mutableStateOf(false) }
    var editOccurrence by remember { mutableStateOf<Occurrence?>(null) }
    var deleteOccurrence by remember { mutableStateOf<Occurrence?>(null) }
    var editThingOpen by remember { mutableStateOf(false) }
    var deleteThingOpen by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(thing?.name ?: "Thing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Thing") },
                                onClick = {
                                    menuOpen = false
                                    editThingOpen = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete Thing",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    deleteThingOpen = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { recordOpen = true },
                containerColor = Primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record ${thing?.name ?: "thing"}")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Primary.copy(alpha = .08f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "${history.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "recorded occurrences",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (libraries.isNotEmpty()) {
                item {
                    Text(
                        "Libraries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        libraries.forEach { library ->
                            FilterChip(
                                selected = library.id in libraryIds,
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            vm.toggleLibrary(library.id, library.id !in libraryIds)
                                        }.onFailure {
                                            operationError = "Couldn't update libraries."
                                        }
                                    }
                                },
                                label = { Text(library.name) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (history.isEmpty()) {
                item {
                    EmptyState(
                        "Nothing recorded yet",
                        "Tap + whenever this happens."
                    )
                }
            } else {
                history
                    .sortedByDescending { it.occurredAt }
                    .groupBy {
                        Instant.ofEpochMilli(it.occurredAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    .forEach { (date, occurrences) ->
                        item(key = "date-$date") {
                            Text(
                                formatHistoryDate(date),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(
                            occurrences.sortedByDescending { it.occurredAt },
                            key = { it.id }
                        ) { occurrence ->
                            HistoryRow(
                                item = occurrence,
                                onEdit = { editOccurrence = occurrence },
                                onDelete = { deleteOccurrence = occurrence }
                            )
                        }
                    }
            }

            operationError?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (recordOpen && thing != null) {
        QuickRecordSheet(
            thing = thing!!,
            onDismiss = { recordOpen = false },
            onRecord = { at, qty ->
                vm.record(at, qty)
                recordOpen = false
            }
        )
    }

    editOccurrence?.let { item ->
        EditOccurrenceDialog(
            item = item,
            onDismiss = { editOccurrence = null }
        ) { updated ->
            scope.launch {
                runCatching { vm.updateOccurrence(updated) }
                    .onSuccess { editOccurrence = null }
                    .onFailure { operationError = "Couldn't save this occurrence. Try again." }
            }
        }
    }

    deleteOccurrence?.let { item ->
        ConfirmDeleteOccurrenceDialog(
            item = item,
            onDismiss = { deleteOccurrence = null }
        ) {
            scope.launch {
                runCatching { vm.deleteOccurrence(item) }
                    .onSuccess { deleteOccurrence = null }
                    .onFailure { operationError = "Couldn't delete this occurrence. Try again." }
            }
        }
    }

    if (editThingOpen && thing != null) {
        EditThingDialog(
            initialName = thing!!.name,
            onDismiss = { editThingOpen = false }
        ) { name ->
            scope.launch {
                runCatching { vm.updateThing(name) }
                    .onSuccess { editThingOpen = false }
                    .onFailure { operationError = "Couldn't save this Thing. Try again." }
            }
        }
    }

    if (deleteThingOpen) {
        AlertDialog(
            onDismissRequest = { deleteThingOpen = false },
            title = { Text("Delete ${thing?.name ?: "Thing"}?") },
            text = {
                Text(
                    "This will permanently delete the Thing, its recorded occurrences, " +
                        "and its Library associations."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { vm.deleteThing() }
                                .onSuccess {
                                    deleteThingOpen = false
                                    onBack()
                                }
                                .onFailure {
                                    operationError = "Couldn't delete this Thing. Try again."
                                    deleteThingOpen = false
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteThingOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    item: Occurrence,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatTime(item.occurredAt), fontWeight = FontWeight.SemiBold)
                if (item.quantity != 1.0) {
                    Text(
                        "Quantity ${formatQuantity(item.quantity)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit occurrence")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete occurrence")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditOccurrenceDialog(
    item: Occurrence,
    onDismiss: () -> Unit,
    onSave: (Occurrence) -> Unit
) {
    val context = LocalContext.current
    var quantity by remember { mutableStateOf(formatQuantity(item.quantity)) }
    var occurredAt by remember { mutableLongStateOf(item.occurredAt) }
    val validQuantity = quantity.toDoubleOrNull()?.let {
        it.isFinite() && it > 0.0
    } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit occurrence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        showDateTimePicker(
                            context = context,
                            initialMillis = occurredAt,
                            onSelected = { occurredAt = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDateTime(occurredAt))
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = {
                        if (it.matches(Regex("""\d*(\.\d*)?"""))) quantity = it
                    },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = quantity.isNotEmpty() && !validQuantity
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    quantity.toDoubleOrNull()?.let {
                        onSave(item.copy(occurredAt = occurredAt, quantity = it))
                    }
                },
                enabled = validQuantity
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteOccurrenceDialog(
    item: Occurrence,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete occurrence?") },
        text = { Text("This recorded activity will be permanently removed.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditThingDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Thing") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.trim().isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = SettingsFactory(
            com.coconutshell.activitytracker.settings.SettingsStore(context),
            com.coconutshell.activitytracker.reminder.ReminderScheduler(context)
        )
    )
    val settings by vm.settings.collectAsState()
    val reminderError by vm.error.collectAsState()
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                ReminderSettingRow(
                    title = "Morning reminder",
                    body = "Start the day with a quick check-in.",
                    enabled = settings.morningEnabled,
                    time = settings.morningTime,
                    onEnabledChange = vm::toggleMorning,
                    onTimeChange = vm::setMorning
                )
            }

            reminderError?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            item {
                ReminderSettingRow(
                    title = "Night reminder",
                    body = "Close the day while it is fresh.",
                    enabled = settings.nightEnabled,
                    time = settings.nightTime,
                    onEnabledChange = vm::toggleNight,
                    onTimeChange = vm::setNight
                )
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Export activity") },
                        supportingContent = {
                            Text("Creates a ZIP containing readable Markdown history.")
                        },
                        leadingContent = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            ExportService(
                                                context,
                                                container.things,
                                                container.occurrences
                                            ).export()
                                        }.onSuccess { file ->
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(share, "Export activity")
                                            )
                                            exportMessage = "ActivityTracker.zip is ready to share."
                                        }.onFailure {
                                            exportMessage = "Couldn't export your activity. Try again."
                                        }
                                    }
                                }
                            ) { Text("Export") }
                        }
                    )
                }
            }

            exportMessage?.let { message ->
                item {
                    Text(
                        message,
                        color = if (message.startsWith("Couldn't")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Primary
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("About Activity Tracker") },
                        supportingContent = {
                            Text("Local-first. Your vocabulary. Your history.")
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Primary
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSettingRow(
    title: String,
    body: String,
    enabled: Boolean,
    time: String,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val parsed = parseTime(time)

    Card(shape = RoundedCornerShape(20.dp)) {
        Column {
            ListItem(
                headlineContent = {
                    Text(title, fontWeight = FontWeight.SemiBold)
                },
                supportingContent = { Text(body) },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange
                    )
                }
            )

            if (enabled) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onTimeChange("%02d:%02d".format(Locale.US, hour, minute))
                            },
                            parsed.first,
                            parsed.second,
                            false
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatClockTime(parsed.first, parsed.second))
                }
            }
        }
    }
}

private fun showDateTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }

    DatePickerDialog(
        context,
        { _, year, month, day ->
            val date = Calendar.getInstance().apply {
                timeInMillis = initialMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    date.set(Calendar.HOUR_OF_DAY, hour)
                    date.set(Calendar.MINUTE, minute)
                    date.set(Calendar.SECOND, 0)
                    date.set(Calendar.MILLISECOND, 0)
                    onSelected(date.timeInMillis)
                },
                date.get(Calendar.HOUR_OF_DAY),
                date.get(Calendar.MINUTE),
                false
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun parseTime(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    return Pair(
        parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
        parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    )
}

private fun formatClockTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .format(cal.toInstant().atZone(ZoneId.systemDefault()))
}

private fun formatDateTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatHistoryDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
        ).uppercase(Locale.getDefault())
    }
}

private fun formatQuantity(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
