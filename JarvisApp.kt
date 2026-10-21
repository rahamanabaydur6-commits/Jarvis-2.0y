package com.jarvis.assistant

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.jarvis.assistant.ai.GeminiRepository
import com.jarvis.assistant.data.JarvisDb
import com.jarvis.assistant.data.SecureStore
import com.jarvis.assistant.data.SettingsStore
import com.jarvis.assistant.executor.ActionExecutor
import com.jarvis.assistant.search.SearchRepository
import com.jarvis.assistant.voice.VoiceManager

class JarvisApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(ctx: Context) {
    val secure = SecureStore(ctx)
    val settings = SettingsStore(ctx)
    val db: JarvisDb = Room.databaseBuilder(ctx, JarvisDb::class.java, "jarvis.db").build()
    val gemini = GeminiRepository(secure)
    val search = SearchRepository(secure)
    val executor = ActionExecutor(ctx)
    val voice by lazy { VoiceManager(ctx) }
}
