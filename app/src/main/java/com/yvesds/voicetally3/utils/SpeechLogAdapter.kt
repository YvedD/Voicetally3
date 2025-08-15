package com.yvesds.voicetally3.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * Adapter voor het weergeven van spraaklogregels.
 *
 * Verbeteringen:
 * - ListAdapter + DiffUtil (efficiëntere updates)
 * - Stabiele IDs (text + type)
 * - Filtert standaard op showInUi of belangrijke types (TALLY_UPDATE, WARNING)
 * - Export in chronologische volgorde
 */
class SpeechLogAdapter :
    ListAdapter<LogEntry, SpeechLogAdapter.LogViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    /** Voeg één logregel toe. */
    fun addLine(entry: LogEntry) {
        val mustShow = entry.type == LogType.TALLY_UPDATE || entry.type == LogType.WARNING
        if (!entry.showInUi && !mustShow) return

        val current = currentList.toMutableList()
        // voorkom duplicates op basis van (text + type)
        val exists = current.any { it.text == entry.text && it.type == entry.type }
        if (exists) return

        current.add(0, entry)
        submitList(current)
    }

    /** Vervang de volledige lijst (filtert op relevantie). */
    fun setLogs(newLogs: List<LogEntry>) {
        val filtered = newLogs
            .filter { it.showInUi || it.type == LogType.TALLY_UPDATE || it.type == LogType.WARNING }
            .reversed() // oudste eerst, zodat laatste bovenaan komt na add(0,...)
        submitList(filtered)
    }

    /** Exporteer alle logs die aangevinkt staan voor export, in chronologische volgorde. */
    fun exportAllLogs(): String {
        return currentList
            .filter { it.includeInExport }
            .reversed() // export in volgorde van ontstaan
            .joinToString("\n") { it.text }
    }

    override fun getItemId(position: Int): Long {
        val entry = getItem(position)
        return (31L * entry.text.hashCode() + entry.type.ordinal).toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_speech_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

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

    private object Diff : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            // zelfde (text + type) behandelen we als hetzelfde item
            return oldItem.text == newItem.text && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
