package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.automation.JarvisAccessibilityService
import com.jarvis.assistant.util.fmtTime
import kotlinx.coroutines.delay

@Composable
fun ControlScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val logs by vm.logs.collectAsState()
    var a11y by remember { mutableStateOf(JarvisAccessibilityService.instance != null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { a11y = JarvisAccessibilityService.instance != null; delay(2000) } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val perms = buildList {
        add("Microphone (voice)" to Manifest.permission.RECORD_AUDIO)
        add("Contacts (call/SMS by name)" to Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add("Notifications (reminders)" to Manifest.permission.POST_NOTIFICATIONS)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard {
                SectionTitle("Automation control")
                SwitchRow("Master automation switch", s.master) { v -> vm.update { it.copy(master = v) } }
                SwitchRow("Open apps & links", s.capApps) { v -> vm.update { it.copy(capApps = v) } }
                SwitchRow("Device (flashlight, volume)", s.capDevice) { v -> vm.update { it.copy(capDevice = v) } }
                SwitchRow("Accessibility (back, home, scroll, tap)", s.capAccess) { v -> vm.update { it.copy(capAccess = v) } }
                SwitchRow("Calls & SMS drafts", s.capComms) { v -> vm.update { it.copy(capComms = v) } }
                SwitchRow("Media keys", s.capMedia) { v -> vm.update { it.copy(capMedia = v) } }
                Text("Every action needs your approval first. Calls/SMS only open the dialer/composer; you press Call/Send.", fontSize = 12.sp)
            }
        }
        item {
            GlassCard {
                SectionTitle("Accessibility permission")
                Text(
                    "Back/Home/Scroll/Tap in other apps works only through Android's AccessibilityService. " +
                        "JARVIS uses it solely to run actions you approved. It may read on-screen text to find a button you asked to tap. " +
                        "Some apps (banking, etc.) block it. You can turn it off any time in Android settings."
                )
                Text(if (a11y) "Status: ENABLED ✅" else "Status: not enabled")
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text("Open Accessibility settings")
                }
            }
        }
        item {
            GlassCard {
                SectionTitle("Permissions")
                perms.forEach { (label, perm) ->
                    val ok = tick >= 0 && has(ctx, perm)
                    Row(Modifier.fillMaxWidth()) {
                        Text("$label: ${if (ok) "granted ✅" else "not granted"}", Modifier.weight(1f))
                        if (!ok) TextButton(onClick = { permLauncher.launch(perm) }) { Text("Grant") }
                    }
                }
            }
        }
        item { SectionTitle("Execution log") }
        if (logs.isEmpty()) item { Text("No actions executed yet.") }
        items(logs) { Text("${if (it.ok) "✅" else "❌"} ${fmtTime(it.ts)}  ${it.text}", fontSize = 12.sp) }
    }
}
