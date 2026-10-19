package com.abhineet.appointments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent { AppTheme { AppScreen(store) } }
    }
}

// ---------- Look and feel ----------

private val Teal = Color(0xFF1D5E52)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF5FBFA8), onPrimary = Color(0xFF0D1916),
        background = Color(0xFF111816), surface = Color(0xFF1A2421),
        surfaceVariant = Color(0xFF243330), secondaryContainer = Color(0xFF1F3631),
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White,
        background = Color(0xFFEEF2F0), surface = Color.White,
        surfaceVariant = Color(0xFFE3EAE7), secondaryContainer = Color(0xFFDCEBE6),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

fun categoryColor(category: String): Color = when (category) {
    "Health" -> Color(0xFFC2410C)
    "Car" -> Color(0xFF2563A8)
    "Home" -> Color(0xFF8A6D16)
    "Admin & money" -> Color(0xFF6B4AA0)
    "Family" -> Color(0xFFB03A6B)
    else -> Color(0xFF3F7A3A)
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
private fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(date: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return when (diff) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern(
            if (date.year == LocalDate.now().year) "EEEE d MMMM" else "EEEE d MMMM yyyy", Locale.getDefault()))
    }
}

// ---------- Main screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var appts by remember { mutableStateOf(store.load()) }
    var calendarId by remember { mutableLongStateOf(store.calendarId) }
    var calendarName by remember { mutableStateOf(store.calendarName) }
    var hasPermission by remember { mutableStateOf(CalendarSync.hasPermission(ctx)) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) } // 0 upcoming, 1 past & done

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        if (hasPermission) showPicker = true
    }

    fun persist(list: List<Appointment>) {
        appts = list
        scope.launch(Dispatchers.IO) { store.save(list) }
    }

    /** Sends one appointment to the calendar (if set up) and returns the updated copy. */
    suspend fun sync(a: Appointment): Appointment = withContext(Dispatchers.IO) {
        if (!hasPermission || calendarId < 0) return@withContext a
        try {
            val eventId = CalendarSync.upsert(ctx, calendarId, a)
            if (eventId != null) a.copy(eventId = eventId, calendarId = calendarId) else a
        } catch (e: Exception) {
            a
        }
    }

    fun saveAppointment(a: Appointment) {
        editing = null
        scope.launch {
            val synced = sync(a)
            persist(appts.filter { it.id != a.id } + synced)
            snackbar.showSnackbar(
                if (synced.eventId != null) "Saved and added to $calendarName" else "Saved on this phone"
            )
        }
    }

    fun deleteAppointment(a: Appointment) {
        editing = null
        persist(appts.filter { it.id != a.id })
        val eventId = a.eventId
        if (eventId != null && hasPermission) {
            scope.launch(Dispatchers.IO) { runCatching { CalendarSync.delete(ctx, eventId) } }
        }
        scope.launch { snackbar.showSnackbar("Deleted") }
    }

    fun chooseCalendar(cal: DeviceCalendar) {
        showPicker = false
        calendarId = cal.id
        calendarName = cal.name
        store.calendarId = cal.id
        store.calendarName = cal.name
        // Send everything that isn't in this calendar yet.
        scope.launch {
            val updated = appts.map { if (it.calendarId != cal.id && !it.done) sync(it) else it }
            persist(updated)
            snackbar.showSnackbar("Syncing to ${cal.name}")
        }
    }

    // The editor replaces the list while it's open.
    editing?.let { a ->
        Editor(
            initial = a,
            isNew = isNew,
            onSave = ::saveAppointment,
            onDelete = { deleteAppointment(a) },
            onClose = { editing = null },
        )
        return
    }

    if (showPicker) {
        CalendarPicker(
            calendars = remember { runCatching { CalendarSync.calendars(ctx) }.getOrDefault(emptyList()) },
            selectedId = calendarId,
            onPick = ::chooseCalendar,
            onDismiss = { showPicker = false },
        )
    }

    val now = System.currentTimeMillis()
    val isPast = { a: Appointment -> a.done || a.end < now }
    val shown = (if (tab == 0) appts.filterNot(isPast) else appts.filter(isPast))
        .sortedBy { it.start }
        .let { if (tab == 1) it.reversed() else it }
    val groups = shown.groupBy { it.start.toLocal().toLocalDate() }
    val upcomingCount = appts.count { !isPast(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Appointments", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        if (hasPermission) showPicker = true
                        else permissionLauncher.launch(CalendarSync.PERMISSIONS)
                    }) { Icon(Icons.Filled.Settings, contentDescription = "Choose calendar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val start = LocalDate.now().plusDays(1).atTime(10, 0).toMillis()
                    isNew = true
                    editing = Appointment(start = start)
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New appointment") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Upcoming · $upcomingCount") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Past & done") })
                }
            }
            if (!hasPermission || calendarId < 0) {
                item {
                    SetupBanner(
                        needsPermission = !hasPermission,
                        onClick = {
                            if (hasPermission) showPicker = true
                            else permissionLauncher.launch(CalendarSync.PERMISSIONS)
                        },
                    )
                }
            }
            if (shown.isEmpty()) {
                item { EmptyState(firstRun = appts.isEmpty()) }
            }
            groups.forEach { (date, dayItems) ->
                item(key = "h-$date") {
                    Text(
                        dayLabel(date).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(dayItems, key = { it.id }) { a ->
                    AppointmentCard(
                        a = a,
                        onEdit = { isNew = false; editing = a },
                        onToggleDone = { persist(appts.map { if (it.id == a.id) it.copy(done = !it.done) else it }) },
                        onOpenCalendar = { openInCalendar(ctx, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupBanner(needsPermission: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connect Google Calendar", fontWeight = FontWeight.SemiBold)
            Text(
                if (needsPermission) "Tap to let the app add your appointments to your calendar, with reminders."
                else "Tap to choose which calendar your appointments go into.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyState(firstRun: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (firstRun) "No appointments yet" else "Nothing here", style = MaterialTheme.typography.titleMedium)
        Text(
            if (firstRun) "Tap “New appointment” to add a doctor visit, the car service or a call with the Finanzamt."
            else "No appointments in this view.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppointmentCard(
    a: Appointment,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    onOpenCalendar: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val start = a.start.toLocal()
    val catColor = categoryColor(a.category)
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tear-off calendar block
            Column(
                Modifier.width(54.dp)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(catColor, RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(start.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("${start.dayOfMonth}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(start.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(a.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${start.format(timeFmt)}–${a.end.toLocal().format(timeFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(a.category, catColor)
                    if (a.eventId != null) Pill("✓ In calendar", MaterialTheme.colorScheme.primary)
                    if (a.reminderMin > 0) Icon(Icons.Filled.Notifications, contentDescription = "Reminder set",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                }
                if (a.location.isNotBlank()) {
                    DetailLine(Icons.Filled.LocationOn, a.location) {
                        ctx.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.location))))
                    }
                }
                val who = listOf(a.contact, a.phone).filter { it.isNotBlank() }.joinToString(" · ")
                if (who.isNotBlank()) {
                    DetailLine(Icons.Filled.Person, who, onClick = if (a.phone.isNotBlank()) {
                        { ctx.startSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.phone))) }
                    } else null)
                }
                if (a.notes.isNotBlank()) {
                    Text(a.notes, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    a.eventId?.let { id -> TextButton(onClick = { onOpenCalendar(id) }) { Text("Open in calendar") } }
                    TextButton(onClick = onToggleDone) { Text(if (a.done) "Not done" else "Mark done") }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }
}

private fun openInCalendar(ctx: Context, eventId: Long) {
    ctx.startSafely(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)))
}

// ---------- Calendar picker ----------

@Composable
private fun CalendarPicker(
    calendars: List<DeviceCalendar>,
    selectedId: Long,
    onPick: (DeviceCalendar) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync to which calendar?") },
        text = {
            if (calendars.isEmpty()) {
                Text("No calendars found. Open the Google Calendar app once, check your Google account is added in phone Settings, then try again.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendars.forEach { cal ->
                        Surface(
                            onClick = { onPick(cal) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (cal.id == selectedId) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(cal.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (cal.isGoogle) "Google · ${cal.account}" else cal.account,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ---------- Add / edit screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(
    initial: Appointment,
    isNew: Boolean,
    onSave: (Appointment) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    var start by remember { mutableStateOf(initial.start.toLocal()) }
    var duration by remember { mutableIntStateOf(initial.durationMin) }
    var reminder by remember { mutableIntStateOf(initial.reminderMin) }
    var location by remember { mutableStateOf(initial.location) }
    var contact by remember { mutableStateOf(initial.contact) }
    var phone by remember { mutableStateOf(initial.phone) }
    var notes by remember { mutableStateOf(initial.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    fun submit() {
        if (title.isBlank()) { showError = true; return }
        onSave(initial.copy(
            title = title.trim(), category = category, start = start.toMillis(),
            durationMin = duration, reminderMin = reminder,
            location = location.trim(), contact = contact.trim(), phone = phone.trim(), notes = notes.trim(),
        ))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this appointment?") },
            text = { Text("It will also be removed from your calendar.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New appointment" else "Edit appointment") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                    TextButton(onClick = ::submit) { Text("Save", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; showError = false },
                label = { Text("What is it?") }, placeholder = { Text("e.g. Dentist check-up") },
                isError = showError,
                supportingText = { if (showError) Text("Give the appointment a name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Category") {
                ChipRow(CATEGORIES.map { it to it }, category) { category = it }
            }

            Section("When") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(ctx, { _, y, m, d -> start = LocalDate.of(y, m + 1, d).atTime(start.toLocalTime()) },
                            start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(start.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())))
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, min -> start = start.withHour(h).withMinute(min) },
                            start.hour, start.minute, true).show()
                    }) { Text(start.format(timeFmt)) }
                }
            }

            Section("Duration") { ChipRow(DURATIONS, duration) { duration = it } }
            Section("Remind me") { ChipRow(REMINDERS, reminder) { reminder = it } }

            OutlinedTextField(value = location, onValueChange = { location = it },
                label = { Text("Location") }, placeholder = { Text("Address or place") },
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = contact, onValueChange = { contact = it },
                    label = { Text("Contact person") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Notes & documents") }, placeholder = { Text("What to bring, prep, follow-ups") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
