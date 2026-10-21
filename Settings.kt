package com.jarvis.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.ai.Prompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val model: String = "gemini-2.5-flash",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val persona: String = Prompts.DEFAULT_PERSONA,
    val memory: Boolean = true,
    val voiceReplies: Boolean = true,
    val darkTheme: Boolean = true,
    val continuous: Boolean = false,
    val lang: String = "hi-IN",
    val master: Boolean = true,
    val capApps: Boolean = true,
    val capDevice: Boolean = true,
    val capAccess: Boolean = false,
    val capComms: Boolean = true,
    val capMedia: Boolean = true,
    val searchCx: String = ""
)

private val Context.dataStore by preferencesDataStore("jarvis_settings")

class SettingsStore(private val ctx: Context) {
    private val MODEL = stringPreferencesKey("model")
    private val TEMP = floatPreferencesKey("temp")
    private val MAXT = intPreferencesKey("max_tokens")
    private val PERSONA = stringPreferencesKey("persona")
    private val MEMORY = booleanPreferencesKey("memory")
    private val VOICE = booleanPreferencesKey("voice")
    private val DARK = booleanPreferencesKey("dark")
    private val CONT = booleanPreferencesKey("continuous")
    private val LANG = stringPreferencesKey("lang")
    private val MASTER = booleanPreferencesKey("master")
    private val C_APPS = booleanPreferencesKey("cap_apps")
    private val C_DEV = booleanPreferencesKey("cap_dev")
    private val C_ACC = booleanPreferencesKey("cap_acc")
    private val C_COMMS = booleanPreferencesKey("cap_comms")
    private val C_MEDIA = booleanPreferencesKey("cap_media")
    private val CX = stringPreferencesKey("search_cx")

    val flow: Flow<Settings> = ctx.dataStore.data.map { p ->
        val d = Settings()
        d.copy(
            model = p[MODEL] ?: d.model, temperature = p[TEMP] ?: d.temperature,
            maxTokens = p[MAXT] ?: d.maxTokens, persona = p[PERSONA] ?: d.persona,
            memory = p[MEMORY] ?: d.memory, voiceReplies = p[VOICE] ?: d.voiceReplies,
            darkTheme = p[DARK] ?: d.darkTheme, continuous = p[CONT] ?: d.continuous,
            lang = p[LANG] ?: d.lang, master = p[MASTER] ?: d.master,
            capApps = p[C_APPS] ?: d.capApps, capDevice = p[C_DEV] ?: d.capDevice,
            capAccess = p[C_ACC] ?: d.capAccess, capComms = p[C_COMMS] ?: d.capComms,
            capMedia = p[C_MEDIA] ?: d.capMedia, searchCx = p[CX] ?: d.searchCx
        )
    }

    suspend fun update(f: (Settings) -> Settings) {
        val n = f(flow.first())
        ctx.dataStore.edit { p ->
            p[MODEL] = n.model; p[TEMP] = n.temperature; p[MAXT] = n.maxTokens
            p[PERSONA] = n.persona; p[MEMORY] = n.memory; p[VOICE] = n.voiceReplies
            p[DARK] = n.darkTheme; p[CONT] = n.continuous; p[LANG] = n.lang
            p[MASTER] = n.master; p[C_APPS] = n.capApps; p[C_DEV] = n.capDevice
            p[C_ACC] = n.capAccess; p[C_COMMS] = n.capComms; p[C_MEDIA] = n.capMedia
            p[CX] = n.searchCx
        }
    }
}
