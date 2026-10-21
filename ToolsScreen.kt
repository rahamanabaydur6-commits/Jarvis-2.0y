package com.jarvis.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.Prompts
import com.jarvis.assistant.util.Calc
import com.jarvis.assistant.util.Units

@Composable
fun ToolsScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val notes by vm.notes.collectAsState()
    val toolOut by vm.toolOut.collectAsState()
    val webOut by vm.webOut.collectAsState()
    val busy by vm.busy.collectAsState()

    var noteText by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf("") }
    var calc by remember { mutableStateOf("") }
    var calcRes by remember { mutableStateOf("") }
    var conv by remember { mutableStateOf("") }
    var convRes by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(Prompts.HELPERS.keys.first()) }
    var helperIn by remember { mutableStateOf("") }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) helperIn = try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(30_000) } ?: ""
        } catch (e: Exception) { "Could not read file: ${e.message}" }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard {
                SectionTitle("AI helpers (needs Gemini key)")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Prompts.HELPERS.keys.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
                }
                OutlinedTextField(helperIn, { helperIn = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp), placeholder = { Text("Text / question / paste document") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.runHelper(mode, helperIn) }, enabled = !busy && helperIn.isNotBlank()) { Text(if (busy) "Working…" else "Run") }
                    OutlinedButton(onClick = { docPicker.launch("text/*") }) { Text("Load .txt/.md") }
                }
                Text("Document summary supports plain-text files only (txt, md).", style = MaterialTheme.typography.bodySmall)
                toolOut?.let { RichText(it); TextButton(onClick = { clip.setText(AnnotatedString(it)) }) { Text("Copy result") } }
            }
        }
        item {
            GlassCard {
                SectionTitle("Notes, to-dos & reminders (offline)")
                OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth(), label = { Text("Note / task") })
                OutlinedTextField(
                    remind, { remind = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
                    label = { Text("Remind me in (minutes, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.addNote(noteText, false, remind.toIntOrNull() ?: 0); noteText = ""; remind = "" }) { Text("Add note") }
                    OutlinedButton(onClick = { vm.addNote(noteText, true, remind.toIntOrNull() ?: 0); noteText = ""; remind = "" }) { Text("Add to-do") }
                }
                Text("Reminders are approximate (can be a few minutes late) and need the Notifications permission.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(notes, key = { it.id }) { n ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (n.todo) Checkbox(n.done, { vm.setDone(n.id, it) })
                Text(if (n.todo) n.text else "📝 ${n.text}", Modifier.weight(1f))
                IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
        item {
            GlassCard {
                SectionTitle("Calculator & converter (offline)")
                OutlinedTextField(calc, { calc = it }, Modifier.fillMaxWidth(), label = { Text("e.g. (12+3)*4/5 or sqrt(81)") }, singleLine = true)
                Button(onClick = { calcRes = try { Calc.format(Calc.eval(calc)) } catch (e: Exception) { "Error: ${e.message}" } }) { Text("=") }
                if (calcRes.isNotEmpty()) Text(calcRes)
                OutlinedTextField(conv, { conv = it }, Modifier.fillMaxWidth(), label = { Text("e.g. 10 km to mi") }, singleLine = true)
                Button(onClick = { convRes = Units.parse(conv) }) { Text("Convert") }
                if (convRes.isNotEmpty()) Text(convRes)
            }
        }
        item {
            GlassCard {
                SectionTitle("Weather & web search")
                OutlinedTextField(city, { city = it }, Modifier.fillMaxWidth(), label = { Text("City") }, singleLine = true)
                Button(onClick = { vm.weather(city) }, enabled = city.isNotBlank()) { Text("Get weather (no key needed)") }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search the web / find AI tools") }, singleLine = true)
                Button(onClick = { vm.webSearch(q) }, enabled = q.isNotBlank()) { Text("Search (needs search key in Settings)") }
                webOut?.let { RichText(it) }
            }
        }
    }
}
