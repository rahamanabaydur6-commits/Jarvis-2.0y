package com.jarvis.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.ai.*
import com.jarvis.assistant.data.*
import com.jarvis.assistant.executor.*
import com.jarvis.assistant.util.ReminderReceiver
import com.jarvis.assistant.util.speakable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Status { Idle, Listening, Thinking, Speaking, Executing }

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as JarvisApp).container

    val settings: StateFlow<Settings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val messages: StateFlow<List<ChatMsg>> = c.db.chat().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<Note>> = c.db.notes().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs: StateFlow<List<LogEntry>> = c.db.logs().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status
    val pending = MutableStateFlow<List<Action>?>(null)
    val error = MutableStateFlow<String?>(null)
    val partial = MutableStateFlow("")
    val tokens = MutableStateFlow(0)
    val models = MutableStateFlow<List<String>>(emptyList())
    val apiMsg = MutableStateFlow<String?>(null)
    val keySet = MutableStateFlow(c.secure.get(SecureStore.GEMINI) != null)
    val searchKeySet = MutableStateFlow(c.secure.get(SecureStore.SEARCH) != null)
    val toolOut = MutableStateFlow<String?>(null)   // AI helper result (Tools tab)
    val webOut = MutableStateFlow<String?>(null)    // search / weather result (Tools tab)
    val codeOut = MutableStateFlow<String?>(null)   // Code tab result
    val busy = MutableStateFlow(false)
    val level: StateFlow<Float> get() = c.voice.level

    private var lastInput: Pair<String, InlineData?>? = null
    private var session = false
    private var execJob: Job? = null

    init {
        c.voice.onSpeakDone = { viewModelScope.launch { onSpeakFinished() } }
    }

    fun update(f: (Settings) -> Settings) { viewModelScope.launch { c.settings.update(f) } }

    // ---------- Chat / planning ----------
    fun send(text: String, image: InlineData? = null, record: Boolean = true) {
        val t = text.trim()
        if (t.isEmpty() && image == null) return
        lastInput = t to image
        error.value = null
        c.voice.stopSpeaking()
        viewModelScope.launch {
            if (record) c.db.chat().add(ChatMsg(role = "user", text = if (image != null) "📷 $t" else t))
            _status.value = Status.Thinking
            val s = settings.value
            try {
                val turns = mutableListOf<Content>()
                if (s.memory) {
                    c.db.chat().last(21).reversed().dropLast(1)
                        .filter { it.role == "user" || it.role == "assistant" }
                        .dropWhile { it.role != "user" }
                        .forEach { turns += Content(role = if (it.role == "user") "user" else "model", parts = listOf(Part(text = it.text))) }
                }
                turns += Content(
                    role = "user",
                    parts = listOfNotNull(if (t.isNotEmpty()) Part(text = t) else null, image?.let { Part(inlineData = it) })
                )
                val (raw, tok) = c.gemini.generate(s.model, Prompts.system(s.persona), turns, s.temperature, s.maxTokens, true)
                tokens.value = tok
                val (reply, actions) = Planner.parse(raw)
                c.db.chat().add(ChatMsg(role = "assistant", text = reply.ifBlank { "(no text)" }))
                if (actions.isNotEmpty()) pending.value = actions
                if (s.voiceReplies && reply.isNotBlank()) {
                    _status.value = Status.Speaking
                    c.voice.speak(speakable(reply), s.lang)
                } else {
                    _status.value = Status.Idle
                    continueSession()
                }
            } catch (e: Exception) {
                error.value = e.message ?: "Unknown error"
                session = false
                _status.value = Status.Idle
            }
        }
    }

    fun retry() { lastInput?.let { send(it.first, it.second, record = false) } }

    private fun onSpeakFinished() {
        if (_status.value == Status.Speaking) _status.value = Status.Idle
        continueSession()
    }

    private fun continueSession() { if (session && pending.value == null && _status.value == Status.Idle) listen() }

    // ---------- Voice ----------
    fun micTapped() {
        if (_status.value == Status.Listening) {
            c.voice.stopListening(); session = false; _status.value = Status.Idle
        } else {
            session = settings.value.continuous
            listen()
        }
    }

    private fun listen() {
        c.voice.stopSpeaking()
        _status.value = Status.Listening
        partial.value = ""
        c.voice.listen(
            settings.value.lang,
            onPartial = { partial.value = it },
            onResult = { partial.value = ""; send(it) },
            onFail = { msg -> _status.value = Status.Idle; session = false; partial.value = ""; error.value = msg }
        )
    }

    fun stopSpeaking() { c.voice.stopSpeaking(); if (_status.value == Status.Speaking) _status.value = Status.Idle; session = false }

    // ---------- Approval + execution ----------
    fun approve() {
        val plan = pending.value ?: return
        pending.value = null
        execJob = viewModelScope.launch {
            _status.value = Status.Executing
            val s = settings.value
            for (a in plan) {
                val out = runAction(a, s)
                c.db.logs().add(LogEntry(text = "${a.describe()} -> ${out.msg}", ok = out.ok))
                c.db.chat().add(ChatMsg(role = "tool", text = "${if (out.ok) "✅" else "❌"} ${a.describe()}\n${out.msg}"))
                if (!out.ok) break // ask the user what to do instead of blindly continuing
                delay(700)
            }
            _status.value = Status.Idle
        }
    }

    fun cancelPlan() {
        execJob?.cancel(); pending.value = null; _status.value = Status.Idle
    }

    private suspend fun runAction(a: Action, s: Settings): Outcome = when (a.type) {
        ActionType.WEB_SEARCH -> try {
            val r = c.search.search(a.arg, s.searchCx)
            Outcome(true, if (r.isEmpty()) "No results." else
                r.joinToString("\n\n") { "${it.title}\n${it.snippet}\n${it.link}" } + "\n\n(Source: web search results, not AI-generated)")
        } catch (e: Exception) { Outcome(false, e.message ?: "Search failed") }
        ActionType.WEATHER -> try { Outcome(true, c.search.weather(a.arg)) } catch (e: Exception) { Outcome(false, e.message ?: "Weather failed") }
        ActionType.ADD_NOTE -> { c.db.notes().add(Note(text = a.arg)); Outcome(true, "Note saved.") }
        ActionType.ADD_TODO -> { c.db.notes().add(Note(text = a.arg, todo = true)); Outcome(true, "To-do added.") }
        else -> c.executor.run(a, s)
    }

    // ---------- Settings / API ----------
    fun saveKey(k: String) { c.secure.put(SecureStore.GEMINI, k.trim()); keySet.value = c.secure.get(SecureStore.GEMINI) != null }
    fun saveSearchKey(k: String) { c.secure.put(SecureStore.SEARCH, k.trim()); searchKeySet.value = c.secure.get(SecureStore.SEARCH) != null }

    fun testApi() {
        viewModelScope.launch {
            apiMsg.value = "Testing…"
            try {
                val m = c.gemini.listModels()
                models.value = m
                apiMsg.value = "✅ Connected. ${m.size} models support generateContent."
            } catch (e: Exception) { apiMsg.value = "❌ ${e.message}" }
        }
    }

    // ---------- Tools / Code helpers ----------
    private fun helper(target: MutableStateFlow<String?>, system: String, prompt: String, minTokens: Int = 0) {
        viewModelScope.launch {
            busy.value = true; target.value = "…"
            try {
                val s = settings.value
                val (t, tok) = c.gemini.generate(
                    s.model, system, listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
                    s.temperature, maxOf(s.maxTokens, minTokens), false
                )
                tokens.value = tok; target.value = t
            } catch (e: Exception) { target.value = "❌ ${e.message}" }
            busy.value = false
        }
    }

    fun runCode(mode: String, prompt: String) {
        val extra = when (mode) {
            "Explain" -> "Task: explain this code in simple language."
            "Debug" -> "Task: find the bug(s) in the code/error below, explain the cause, give the fixed code."
            "Improve" -> "Task: improve this code (readability, performance, safety) and explain the changes."
            "Android project" -> "Task: produce a complete multi-file Android (Kotlin + Compose) project structure for this idea, one block per file."
            else -> "Task: generate the requested code."
        }
        helper(codeOut, Prompts.CODER + " " + extra, prompt, 4096)
    }

    fun runHelper(mode: String, text: String) = helper(toolOut, Prompts.HELPERS[mode] ?: Prompts.DEFAULT_PERSONA, text)

    fun webSearch(q: String) {
        viewModelScope.launch {
            webOut.value = "…"
            webOut.value = try {
                val r = c.search.search(q, settings.value.searchCx)
                if (r.isEmpty()) "No results." else r.joinToString("\n\n") { "${it.title}\n${it.snippet}\n${it.link}" } +
                    "\n\n(Source: web search results, not AI-generated)"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
    }

    fun weather(city: String) {
        viewModelScope.launch {
            webOut.value = "…"
            webOut.value = try { c.search.weather(city) } catch (e: Exception) { "❌ ${e.message}" }
        }
    }

    fun addNote(text: String, todo: Boolean, remindMin: Int) {
        if (text.isBlank()) return
        viewModelScope.launch {
            c.db.notes().add(Note(text = text.trim(), todo = todo))
            if (remindMin > 0) ReminderReceiver.schedule(getApplication(), text.trim(), remindMin)
        }
    }
    fun setDone(id: Long, done: Boolean) { viewModelScope.launch { c.db.notes().setDone(id, done) } }
    fun deleteNote(id: Long) { viewModelScope.launch { c.db.notes().delete(id) } }

    // ---------- Privacy ----------
    fun clearChat() { viewModelScope.launch { c.db.chat().clear() } }
    fun clearNotes() { viewModelScope.launch { c.db.notes().clear() } }
    fun clearLogs() { viewModelScope.launch { c.db.logs().clear() } }
    fun clearKeys() { c.secure.clearAll(); keySet.value = false; searchKeySet.value = false }

    override fun onCleared() { c.voice.shutdown() }
}
