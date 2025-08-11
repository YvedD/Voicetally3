package com.yvesds.voicetally3.utils

/**
 * Een enkele logregel voor gebruik in de SpeechLogAdapter en exportfunctionaliteit.
 *
 * @param text De weergegeven tekst.
 * @param type Het type logbericht (bepaalt kleur in de UI).
 * @param showInUi Of deze regel in de loglijst van de UI moet worden getoond.
 * @param includeInExport Of deze regel opgenomen wordt bij export van logs.
 */
data class LogEntry(
    val text: String,
    val type: LogType = LogType.INFO,
    val showInUi: Boolean = true,
    val includeInExport: Boolean = true
)
