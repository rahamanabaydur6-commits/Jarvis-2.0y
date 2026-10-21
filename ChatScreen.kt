package com.jarvis.assistant.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ai.InlineData
import com.jarvis.assistant.util.ImageUtil

@Composable
fun ChatScreen(vm: JarvisViewModel, onMic: () -> Unit) {
    val ctx = LocalContext.current
    val msgs by vm.messages.collectAsState()
    val status by vm.status.collectAsState()
    val err by vm.error.collectAsState()
    var query by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<InlineData?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) image = ImageUtil.toInline(ctx, uri)
    }
    val shown = if (query.isBlank()) msgs else msgs.filter { it.text.contains(query, true) }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (query.isBlank() && msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Search conversation…") }
            )
            IconButton(onClick = {
                val txt = msgs.joinToString("\n\n") { "[${it.role}] ${it.text}" }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Export chat"))
            }) { Icon(Icons.Default.Share, "Export") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when (m.role) {
                            "user" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                            "tool" -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) { Box(Modifier.padding(10.dp)) { RichText(m.text) } }
                }
            }
        }
        err?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::retry) { Text("Retry") }
            }
        }
        if (status == Status.Thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
        InputBar(
            status,
            onSend = { t ->
                vm.send(t.ifBlank { "Is image mein kya hai? Simple language mein batao." }, image)
                image = null
            },
            onMic = onMic, onAttach = { picker.launch("image/*") }, attached = image != null
        )
    }
}
