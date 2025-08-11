package com.yvesds.voicetally3.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * Adapter voor het weergeven van spraaklogregels.
 *
 * Verbeteringen:
 * - Sterke types voor RecyclerView.Adapter<LogViewHolder>
 * - Stabiele IDs voor vloeiende recyclage (setHasStableIds + getItemId)
 * - Vermijdt dubbele items (op basis van text + type)
 * - Filtert standaard op showInUi of belangrijke types (TALLY_UPDATE, WARNING)
 * - Export in chronologische volgorde
 */
class SpeechLogAdapter : RecyclerView.Adapter<SpeechLogAdapter.LogViewHolder>() {

    private val logs: MutableList<LogEntry> = mutableListOf()

    init {
        // Stabiele IDs helpen tegen onnodig hertekenen/flikkeren
        setHasStableIds(true)
    }

    /**
     * Voeg een logregel toe. Wordt enkel getoond als:
     * - entry.showInUi == true, of
     * - het een belangrijk type is (TALLY_UPDATE, WARNING)
     * Voorkomt duplicates met dezelfde (text + type).
     */
    fun addLine(entry: LogEntry) {
        val mustShow = entry.type == LogType.TALLY_UPDATE || entry.type == LogType.WARNING
        val exists = logs.any { it.text == entry.text && it.type == entry.type }
        if ((entry.showInUi || mustShow) && !exists) {
            logs.add(0, entry)
            notifyItemInserted(0)
        }
    }

    /**
     * Vervang de volledige lijst. Toont enkel relevante regels.
     * Nieuwe weergave zet het meest recente bovenaan.
     */
    fun setLogs(newLogs: List<LogEntry>) {
        logs.clear()
        logs.addAll(
            newLogs
                .filter { it.showInUi || it.type == LogType.TALLY_UPDATE || it.type == LogType.WARNING }
                .reversed() // oudste eerst, zodat laatste bovenaan komt na add(0, ...)
        )
        notifyDataSetChanged()
    }

    /**
     * Exporteer alle logs die aangevinkt staan voor export, in chronologische volgorde.
     */
    fun exportAllLogs(): String {
        return logs
            .filter { it.includeInExport }
            .reversed() // export in volgorde van ontstaan
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

    override fun getItemId(position: Int): Long {
        val entry = logs[position]
        // Combineer text + type tot een stabiele (best effort) ID
        val prime = 31L
        return (prime * entry.text.hashCode() + entry.type.ordinal).toLong()
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
}
