package com.yvesds.voicetally3.utils

data class LogEntry(
    val text: String,
    val showInUi: Boolean = true,
    val includeInExport: Boolean = true, // 👈 afgeleid van showInUi
    val type: LogType = LogType.INFO
)
