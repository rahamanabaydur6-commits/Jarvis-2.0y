package com.jarvis.assistant.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.settings.collectAsState()
            JarvisTheme(s.darkTheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { JarvisRoot(vm) }
            }
        }
    }
}

@Composable
fun JarvisRoot(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.micTapped() else vm.error.value = "Microphone permission denied. Control tab se allow karo."
    }
    val onMic: () -> Unit = {
        if (has(ctx, Manifest.permission.RECORD_AUDIO)) vm.micTapped() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val tabs = listOf(
        "Home" to Icons.Default.Home, "Chat" to Icons.Default.Face, "Control" to Icons.Default.Build,
        "Code" to Icons.Default.Create, "Tools" to Icons.Default.List, "Settings" to Icons.Default.Settings
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm, onMic)
                1 -> ChatScreen(vm, onMic)
                2 -> ControlScreen(vm)
                3 -> CodeScreen(vm)
                4 -> ToolsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
