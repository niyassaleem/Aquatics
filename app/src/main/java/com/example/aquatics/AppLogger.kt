package com.example.aquatics

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {
    fun log(context: Context, tag: String, message: String, level: String = "INFO") {
        val database = AppDatabase.getDatabase(context)
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = message,
            level = level
        )
        CoroutineScope(Dispatchers.IO).launch {
            database.logDao().insert(entry)
        }
    }

    fun info(context: Context, tag: String, message: String) = log(context, tag, message, "INFO")
    fun warn(context: Context, tag: String, message: String) = log(context, tag, message, "WARN")
    fun error(context: Context, tag: String, message: String) = log(context, tag, message, "ERROR")
}
