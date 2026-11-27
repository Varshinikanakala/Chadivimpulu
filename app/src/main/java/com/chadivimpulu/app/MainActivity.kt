package com.chadivimpulu.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Maroon = Color(0xFF7A1E2C)
val Green = Color(0xFF1E7A46)
val Red = Color(0xFFB3261E)
val Cream = Color(0xFFFFF8EE)
val Sand = Color(0xFFF3E6D0)
val Muted = Color(0xFF6B5A4C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = EntryStore(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Maroon,
                    onPrimary = Color.White,
                    background = Cream,
                    surface = Color.White,
                )
            ) {
                Box(Modifier.fillMaxSize().background(Cream).safeDrawingPadding()) {
                    App(store)
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Edit(val entry: Entry?, val fast: Boolean = false) : Screen
    data class PersonView(val key: String) : Screen
    data object Functions : Screen
}

fun toast(context: Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

@Composable
fun App(store: EntryStore) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    fun update(list: List<Entry>) {
        entries = list
        store.save(list)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(EntryStore.toJson(entries).toByteArray())
                }
                toast(context, "Backup సేవ్ అయింది (${entries.size} entries)")
            } catch (e: Exception) {
                toast(context, "Backup కాలేదు")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val imported = EntryStore.fromJson(text)
                update((entries + imported).associateBy { it.id }.values.toList())
                toast(context, "${imported.size} entries తెచ్చుకున్నాం")
            } catch (e: Exception) {
                toast(context, "ఇది సరైన backup file కాదు")
            }
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            entries = entries,
            onAdd = { screen = Screen.Edit(null) },
            onFast = { screen = Screen.Edit(null, fast = true) },
            onOpenPerson = { screen = Screen.PersonView(it) },
            onFunctions = { screen = Screen.Functions },
            onShare = { shareText(context, entries) },
            onBackup = { backupLauncher.launch("chadivimpulu-backup-${today()}.json") },
            onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
        is Screen.Edit -> EditScreen(
            initial = s.entry,
            fast = s.fast,
            entries = entries,
            onSave = { e -> update(entries.filterNot { it.id == e.id } + e) },
            onDelete = { id ->
                update(entries.filterNot { it.id == id })
                screen = Screen.Home
            },
            onBack = { screen = Screen.Home },
        )
        is Screen.PersonView -> PersonScreen(
            key = s.key,
            entries = entries,
            onBack = { screen = Screen.Home },
            onEdit = { screen = Screen.Edit(it) },
            onAddGiven = { screen = Screen.Edit(it) },
        )
        Screen.Functions -> FunctionsScreen(entries = entries, onBack = { screen = Screen.Home })
    }
}

fun shareText(context: Context, entries: List<Entry>) {
    val t = entries.totals()
    val sb = StringBuilder()
    sb.appendLine("చదివింపుల పుస్తకం")
    sb.appendLine("మనకు వచ్చింది: ${rupees(t.received)}")
    sb.appendLine("మనం ఇచ్చాం: ${rupees(t.given)}")
    sb.appendLine()
    entries.sortedBy { it.id }.forEach { e ->
        val sign = if (e.received) "+" else "−"
        val gift = if (e.gift.isNotBlank()) " + ${e.gift}" else ""
        val place = if (e.place.isNotBlank()) " (${e.place})" else ""
        sb.appendLine("$sign ${e.name}$place ${rupees(e.amount)}$gift · ${e.function} ${e.date}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "వెనక్కి") }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Maroon)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        actions()
    }
}

@Composable
fun AmountCard(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 13.sp, color = Muted)
            Text(rupees(value), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun Avatar(name: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Sand),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1), fontWeight = FontWeight.Bold, color = Maroon)
    }
}

@Composable
fun EntryRow(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(e.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            val details = listOf(e.place, e.function, e.gift).filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotEmpty()) Text(details, fontSize = 13.sp, color = Muted)
        }
        Text(
            (if (e.received) "+ " else "− ") + rupees(e.amount),
            fontWeight = FontWeight.Bold,
            color = if (e.received) Green else Red,
        )
    }
}

@Composable
fun PersonRow(p: Person, t: Totals, onClick: () -> Unit) {
    val balance = t.received - t.given
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (p.place.isNotBlank()) Text(p.place, fontSize = 13.sp, color = Muted)
        }
        Text(
            when {
                balance > 0 -> "ఇవ్వాలి ${rupees(balance)}"
                balance == 0L -> "సరిపోయింది"
                else -> "ఎక్కువ ఇచ్చాం"
            },
            fontWeight = FontWeight.Bold,
            color = if (balance > 0) Maroon else Muted,
        )
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ToggleItem(text: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else Muted, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HomeScreen(
    entries: List<Entry>,
    onAdd: () -> Unit,
    onFast: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onFunctions: () -> Unit,
    onShare: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val totals = entries.totals()

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Maroon,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("కొత్త entry", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header("చదివింపుల పుస్తకం") {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "మరిన్ని")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("ఫంక్షన్ వారీగా లెక్క") }, onClick = { menuOpen = false; onFunctions() })
                        DropdownMenuItem(text = { Text("Backup తీయండి") }, onClick = { menuOpen = false; onBackup() })
                        DropdownMenuItem(text = { Text("Backup నుండి తెచ్చుకోండి") }, onClick = { menuOpen = false; onRestore() })
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountCard("మనకు వచ్చింది", totals.received, Green, Modifier.weight(1f))
                AmountCard("మనం ఇచ్చాం", totals.given, Red, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onFast,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("🎉 ఫంక్షన్ రోజు Fast mode")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("పేరు లేదా ఊరుతో వెతకండి…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    "ఇంకా entries లేవు. కింద ఉన్న \"కొత్త entry\" నొక్కి మొదలుపెట్టండి.",
                    Modifier.padding(24.dp),
                    color = Muted,
                )
            } else if (query.isBlank()) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(entries.sortedByDescending { it.id }, key = { it.id }) { e ->
                        EntryRow(e) { onOpenPerson(e.personKey) }
                    }
                }
            } else {
                val q = query.trim()
                val matches = entries.people().filter {
                    it.name.contains(q, ignoreCase = true) || it.place.contains(q, ignoreCase = true)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(matches, key = { it.key }) { p ->
                        val t = entries.filter { it.personKey == p.key }.totals()
                        PersonRow(p, t) { onOpenPerson(p.key) }
                    }
                }
            }
        }
    }
}
