package com.jarvis.assistant.ui

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val MODES = listOf("Generate", "Explain", "Debug", "Improve", "Android project")

@Composable
fun CodeScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val out by vm.codeOut.collectAsState()
    val busy by vm.busy.collectAsState()
    var mode by rememberSaveable { mutableStateOf(MODES[0]) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write((out ?: "").toByteArray()) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Coding workspace")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MODES.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
        }
        OutlinedTextField(
            prompt, { prompt = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text("Describe the code, or paste code / error here…") }
        )
        Button(onClick = { vm.runCode(mode, prompt) }, enabled = !busy && prompt.isNotBlank()) { Text(if (busy) "Working…" else "Run") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        out?.let { text ->
            RichText(text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text("Copy all") }
                OutlinedButton(onClick = { export.launch("jarvis_code.txt") }) { Text("Export .txt") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
                }) { Text("Share") }
                htmlBlock(text)?.let { html -> Button(onClick = { preview = html }) { Text("Preview HTML") } }
            }
            Text("Code is never executed on your device by JARVIS. HTML preview runs in an isolated WebView with network blocked.", style = MaterialTheme.typography.bodySmall)
        }
    }

    preview?.let { html ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    TextButton(onClick = { preview = null }) { Text("Close preview") }
                    AndroidView(
                        factory = { c ->
                            WebView(c).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.domStorageEnabled = false
                                settings.blockNetworkLoads = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                                }
                                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
