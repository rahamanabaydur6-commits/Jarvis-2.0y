package com.jarvis.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.Prompts

@Composable
fun SettingsScreen(vm: JarvisViewModel) {
    val s by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val apiMsg by vm.apiMsg.collectAsState()
    val tokens by vm.tokens.collectAsState()
    val keySet by vm.keySet.collectAsState()
    val searchKeySet by vm.searchKeySet.collectAsState()

    var key by remember { mutableStateOf("") }
    var skey by remember { mutableStateOf("") }
    var cx by remember(s.searchCx) { mutableStateOf(s.searchCx) }
    var modelText by remember(s.model) { mutableStateOf(s.model) }
    var temp by remember(s.temperature) { mutableFloatStateOf(s.temperature) }
    var maxT by remember(s.maxTokens) { mutableFloatStateOf(s.maxTokens.toFloat()) }
    var persona by remember(s.persona) { mutableStateOf(s.persona) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard {
                SectionTitle("Gemini AI")
                Text(if (keySet) "API key saved (encrypted with Android Keystore) ✅" else "No API key yet")
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
                    OutlinedButton(onClick = vm::testApi) { Text("Test connection") }
                }
                apiMsg?.let { Text(it) }
                OutlinedTextField(modelText, { modelText = it }, Modifier.fillMaxWidth(), label = { Text("Model name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.update { it.copy(model = modelText.trim()) } }) { Text("Set model") }
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = models.isNotEmpty()) { Text(if (models.isEmpty()) "Pick (test first)" else "Pick from list") }
                        DropdownMenu(menu, { menu = false }) {
                            models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { vm.update { it.copy(model = m) }; menu = false }) }
                        }
                    }
                }
                Text("Active model: ${s.model}")
                Text("Temperature: ${"%.1f".format(temp)}")
                Slider(temp, { temp = it }, valueRange = 0f..2f, onValueChangeFinished = { vm.update { it.copy(temperature = temp) } })
                Text("Max response tokens: ${maxT.toInt()}")
                Slider(maxT, { maxT = it }, valueRange = 256f..8192f, onValueChangeFinished = { vm.update { it.copy(maxTokens = maxT.toInt()) } })
                Text("Tokens used by last request: $tokens")
                Text("Streaming is not implemented in this version; replies appear when complete.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            GlassCard {
                SectionTitle("Personality")
                OutlinedTextField(persona, { persona = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("System instruction") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.update { it.copy(persona = persona) } }) { Text("Save") }
                    OutlinedButton(onClick = { vm.update { it.copy(persona = Prompts.DEFAULT_PERSONA) } }) { Text("Reset") }
                }
                SwitchRow("Conversation memory", s.memory) { v -> vm.update { it.copy(memory = v) } }
            }
        }
        item {
            GlassCard {
                SectionTitle("Voice & look")
                SwitchRow("Speak replies", s.voiceReplies) { v -> vm.update { it.copy(voiceReplies = v) } }
                SwitchRow("Continuous conversation (while app is open)", s.continuous) { v -> vm.update { it.copy(continuous = v) } }
                SwitchRow("Dark theme", s.darkTheme) { v -> vm.update { it.copy(darkTheme = v) } }
                Text("Voice language")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hindi" to "hi-IN", "English" to "en-IN", "Bengali" to "bn-IN").forEach { (n, code) ->
                        FilterChip(selected = s.lang == code, onClick = { vm.update { it.copy(lang = code) } }, label = { Text(n) })
                    }
                }
                Text("Recognition/voice quality depends on the speech services installed on your phone.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            GlassCard {
                SectionTitle("Web search (optional)")
                Text("Uses Google Programmable Search JSON API. Create a key + search engine ID (cx) in Google's console.")
                Text(if (searchKeySet) "Search key saved ✅" else "No search key")
                OutlinedTextField(skey, { skey = it }, Modifier.fillMaxWidth(), label = { Text("Search API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(cx, { cx = it }, Modifier.fillMaxWidth(), label = { Text("Search engine ID (cx)") }, singleLine = true)
                Button(onClick = { if (skey.isNotBlank()) vm.saveSearchKey(skey); skey = ""; vm.update { it.copy(searchCx = cx.trim()) } }) { Text("Save") }
            }
        }
        item {
            GlassCard {
                SectionTitle("Privacy & data")
                Text(
                    "Chats, notes and logs stay on this phone. Your messages (and images you attach) are sent to Google's Gemini API " +
                        "only when you send them. Contacts, screenshots and screen content are never uploaded automatically. " +
                        "Microphone is used only while the status shows LISTENING."
                )
                OutlinedButton(onClick = { confirm = "Delete chat history?" to vm::clearChat }) { Text("Delete chat history") }
                OutlinedButton(onClick = { confirm = "Delete notes & to-dos?" to vm::clearNotes }) { Text("Delete notes & to-dos") }
                OutlinedButton(onClick = { confirm = "Delete execution log?" to vm::clearLogs }) { Text("Delete execution log") }
                OutlinedButton(onClick = { confirm = "Delete all saved API keys?" to vm::clearKeys }) { Text("Delete API keys") }
            }
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(msg) }, text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}
