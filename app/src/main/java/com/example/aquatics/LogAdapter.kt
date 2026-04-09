package com.example.aquatics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private var logs: List<LogEntry>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logLevel: TextView = view.findViewById(R.id.logLevel)
        val logTime: TextView = view.findViewById(R.id.logTime)
        val logTag: TextView = view.findViewById(R.id.logTag)
        val logMessage: TextView = view.findViewById(R.id.logMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.logLevel.text = log.level
        holder.logTag.text = log.tag
        holder.logMessage.text = log.message
        
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        holder.logTime.text = sdf.format(Date(log.timestamp))

        // Basic coloring based on level
        val context = holder.itemView.context
        when (log.level) {
            "ERROR" -> holder.logLevel.setBackgroundColor(context.getColor(android.R.color.holo_red_light))
            "WARN" -> holder.logLevel.setBackgroundColor(context.getColor(android.R.color.holo_orange_light))
            else -> holder.logLevel.setBackgroundColor(context.getColor(android.R.color.darker_gray))
        }
    }

    override fun getItemCount() = logs.size

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
