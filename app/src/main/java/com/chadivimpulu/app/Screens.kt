package com.chadivimpulu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun EditScreen(
    initial: Entry?,
    fast: Boolean,
    entries: List<Entry>,
    onSave: (Entry) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isNew = initial == null || initial.id == 0L
    var received by remember { mutableStateOf(initial?.received ?: true) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var place by remember { mutableStateOf(initial?.place ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var gift by remember { mutableStateOf(initial?.gift ?: "") }
    var function by remember { mutableStateOf(initial?.function ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: today()) }
    var savedCount by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val people = remember(entries) { entries.people() }

    val suggestions = if (name.isBlank()) emptyList() else people.filter {
        it.name.contains(name.trim(), ignoreCase = true) && it.key != personKeyOf(name, place)
    }.take(4)

    fun save() {
        val amt = amount.trim().toLongOrNull() ?: 0L
        if (name.isBlank()) { toast(context, "పేరు రాయండి"); return }
        if (amt <= 0 && gift.isBlank()) { toast(context, "మొత్తం లేదా బహుమతి రాయండి"); return }
        val e = Entry(
            id = if (isNew) System.currentTimeMillis() else initial!!.id,
            name = name.trim(),
            place = place.trim(),
            amount = amt,
            gift = gift.trim(),
            function = function.trim(),
            date = date.trim(),
            received = received,
        )
        onSave(e)
        if (fast) {
            savedCount++
            toast(context, "${e.name} సేవ్ అయింది")
            name = ""; place = ""; amount = ""; gift = ""
        } else {
            onBack()
        }
    }

    val title = when {
        fast -> "ఫంక్షన్ రోజు Fast mode"
        isNew -> "కొత్త entry"
        else -> "Entry మార్చండి"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(title, onBack = onBack) {
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "తొలగించు", tint = Red)
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (fast) {
                Text("ఫంక్షన్ పేరు, తేదీ ఒకసారి రాస్తే చాలు. తర్వాత పేరు + మొత్తం మాత్రమే.", color = Muted, fontSize = 13.sp)
                Field("ఫంక్షన్ (ఉదా. మా అమ్మాయి పెళ్ళి)", function) { function = it }
                Field("తేదీ", date) { date = it }
                if (savedCount > 0) {
                    Text("ఇప్పటికి $savedCount entries సేవ్ అయ్యాయి ✅", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sand).padding(4.dp)
            ) {
                ToggleItem("మనకు వచ్చింది", received, Green, Modifier.weight(1f)) { received = true }
                ToggleItem("మనం ఇచ్చాం", !received, Red, Modifier.weight(1f)) { received = false }
            }
            Field("పేరు *", name) { name = it }
            suggestions.forEach { p ->
                Text(
                    "👉 ${p.name}" + if (p.place.isNotBlank()) " (${p.place})" else "",
                    color = Maroon,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Sand)
                        .clickable { name = p.name; place = p.place }.padding(10.dp),
                )
            }
            Field("ఊరు / బంధుత్వం", place) { place = it }
            Field("మొత్తం (₹)", amount, KeyboardType.Number) { v -> amount = v.filter { it.isDigit() } }
            Field("బహుమతి (ఉంటే: చీర, వెండి…)", gift) { gift = it }
            if (!fast) {
                Field("ఫంక్షన్", function) { function = it }
                Field("తేదీ (dd-MM-yyyy)", date) { date = it }
            }
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Maroon),
            ) {
                Text("సేవ్ చేయండి", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ఈ entry తొలగించాలా?") },
            text = { Text("తొలగించిన తర్వాత ఇది తిరిగి రాదు.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial.id) }) {
                    Text("తొలగించు", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("వద్దు") }
            },
        )
    }
}

@Composable
fun PersonScreen(
    key: String,
    entries: List<Entry>,
    onBack: () -> Unit,
    onEdit: (Entry) -> Unit,
    onAddGiven: (Entry) -> Unit,
) {
    val list = entries.filter { it.personKey == key }.sortedByDescending { it.id }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val first = list.first()
    val t = list.totals()
    val balance = t.received - t.given
    val (label, value) = when {
        balance > 0 -> "ఇంకా తిరిగి ఇవ్వాల్సింది" to rupees(balance)
        balance == 0L -> "లెక్క సరిపోయింది" to "✓"
        else -> "మనం ఎక్కువ ఇచ్చాం" to rupees(-balance)
    }

    Column(Modifier.fillMaxSize()) {
        Header(first.name, subtitle = first.place.ifBlank { null }, onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Maroon)) {
                Column(Modifier.padding(20.dp)) {
                    Text(label, color = Color.White, fontSize = 14.sp)
                    Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountCard("మనకు ఇచ్చారు", t.received, Green, Modifier.weight(1f))
                AmountCard("మనం ఇచ్చాం", t.given, Red, Modifier.weight(1f))
            }
            Button(
                onClick = {
                    onAddGiven(Entry(0L, first.name, first.place, 0L, "", "", today(), received = false))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Maroon),
            ) {
                Text("మనం ఇచ్చింది జోడించు", fontWeight = FontWeight.Bold)
            }
            Text("చరిత్ర (నొక్కితే మార్చవచ్చు)", color = Muted, fontSize = 13.sp)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.function.ifBlank { "ఫంక్షన్" }, fontWeight = FontWeight.SemiBold)
                        val extra = listOf(e.date, e.gift).filter { it.isNotBlank() }.joinToString(" · ")
                        if (extra.isNotEmpty()) Text(extra, fontSize = 13.sp, color = Muted)
                    }
                    Text(
                        (if (e.received) "+ " else "− ") + rupees(e.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (e.received) Green else Red,
                    )
                }
            }
        }
    }
}

@Composable
fun FunctionsScreen(entries: List<Entry>, onBack: () -> Unit) {
    val groups = entries.groupBy { it.function.ifBlank { "ఇతరాలు" } }
        .map { (name, list) -> name to list.totals() }
        .sortedByDescending { it.second.received + it.second.given }

    Column(Modifier.fillMaxSize()) {
        Header("ఫంక్షన్ వారీగా లెక్క", onBack = onBack)
        if (groups.isEmpty()) {
            Text("ఇంకా entries లేవు", Modifier.padding(24.dp), color = Muted)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.first }) { (name, t) ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${t.count} entries", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        if (t.received > 0) Text("వచ్చింది: ${rupees(t.received)}", color = Green, fontWeight = FontWeight.Bold)
                        if (t.given > 0) Text("ఇచ్చాం: ${rupees(t.given)}", color = Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
