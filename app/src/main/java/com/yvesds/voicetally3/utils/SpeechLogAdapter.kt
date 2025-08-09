package com.yvesds.voicetally3.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

class SpeechLogAdapter : RecyclerView.Adapter<SpeechLogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<LogEntry>()

    fun addLine(entry: LogEntry) {
        val mustShow = entry.type == LogType.TALLY_UPDATE || entry.type == LogType.WARNING
        val exists = logs.any { it.text == entry.text && it.type == entry.type }

        if ((entry.showInUi || mustShow) && !exists) {
            logs.add(0, entry)
            notifyItemInserted(0)
        }
    }

    fun setLogs(newLogs: List<LogEntry>) {
        logs.clear()
        logs.addAll(
            newLogs
                .filter { it.showInUi || it.type == LogType.TALLY_UPDATE || it.type == LogType.WARNING }
                .reversed()
        )
        notifyDataSetChanged()
    }

    fun exportAllLogs(): String {
        return logs
            .filter { it.includeInExport }
            .reversed()
            .joinToString("\n") { it.text }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_speech_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logText: TextView = itemView.findViewById(R.id.textLog)

        fun bind(entry: LogEntry) {
            logText.text = entry.text
            logText.setTextColor(getColorForType(entry.type, itemView))
        }

        private fun getColorForType(type: LogType, view: View): Int {
            val context = view.context
            return when (type) {
                LogType.FINAL -> ContextCompat.getColor(context, android.R.color.white)
                LogType.PARTIAL -> ContextCompat.getColor(context, android.R.color.darker_gray)
                LogType.PARSED_BLOCK -> ContextCompat.getColor(context, android.R.color.holo_blue_light)
                LogType.TALLY_UPDATE -> ContextCompat.getColor(context, android.R.color.holo_green_light)
                LogType.ERROR -> ContextCompat.getColor(context, android.R.color.holo_red_light)
                LogType.WARNING -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
                LogType.INFO -> ContextCompat.getColor(context, android.R.color.secondary_text_dark)
            }
        }
    }
}
