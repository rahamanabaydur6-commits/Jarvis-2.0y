package com.jarvis.assistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat")
data class ChatMsg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // user | assistant | tool
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val todo: Boolean = false,
    val done: Boolean = false,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ok: Boolean,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY id") fun all(): Flow<List<ChatMsg>>
    @Query("SELECT * FROM chat ORDER BY id DESC LIMIT :n") suspend fun last(n: Int): List<ChatMsg>
    @Insert suspend fun add(m: ChatMsg)
    @Query("DELETE FROM chat") suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY id DESC") fun all(): Flow<List<Note>>
    @Insert suspend fun add(n: Note)
    @Query("UPDATE notes SET done = :done WHERE id = :id") suspend fun setDone(id: Long, done: Boolean)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM notes") suspend fun clear()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT 100") fun all(): Flow<List<LogEntry>>
    @Insert suspend fun add(l: LogEntry)
    @Query("DELETE FROM logs") suspend fun clear()
}

@Database(entities = [ChatMsg::class, Note::class, LogEntry::class], version = 1, exportSchema = false)
abstract class JarvisDb : RoomDatabase() {
    abstract fun chat(): ChatDao
    abstract fun notes(): NoteDao
    abstract fun logs(): LogDao
}
