package com.jarvis.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(vm: JarvisViewModel, onMic: () -> Unit) {
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val msgs by vm.messages.collectAsState()
    val partial by vm.partial.collectAsState()
    val pending by vm.pending.collectAsState()
    val err by vm.error.collectAsState()
    val keySet by vm.keySet.collectAsState()
    var now by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val f = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm")
        while (true) { now = LocalDateTime.now().format(f); online = isOnline(ctx); delay(20_000) }
    }
    val last = msgs.lastOrNull { it.role == "assistant" }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(now, color = cs.onSurfaceVariant)
            Text(
                "● ${status.name.uppercase()}  |  ${if (online) "ONLINE" else "OFFLINE"}  |  ${if (keySet) "AI READY" else "NO API KEY"}",
                color = cs.primary, fontSize = 12.sp
            )
            JarvisCore(status, vm.level)
            if (partial.isNotBlank()) Text("“$partial”", color = cs.onSurfaceVariant)
            if (status == Status.Thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status == Status.Speaking) OutlinedButton(onClick = vm::stopSpeaking) { Text("Stop speaking") }
            pending?.let { PlanCard(it, vm::approve, vm::cancelPlan) }
            if (status == Status.Executing) GlassCard {
                Text("Executing approved actions…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancelPlan) { Text("Cancel") }
            }
            err?.let {
                GlassCard {
                    Text(it, color = cs.error)
                    Row {
                        TextButton(onClick = vm::retry) { Text("Retry") }
                        TextButton(onClick = { vm.error.value = null }) { Text("Dismiss") }
                    }
                }
            }
            last?.let { GlassCard { RichText(it.text) } }
            if (last == null && err == null) Text("Mic dabao ya type karo. Example: “Jarvis, YouTube kholo”", color = cs.onSurfaceVariant)
        }
        InputBar(status, onSend = { vm.send(it) }, onMic = onMic)
    }
}
