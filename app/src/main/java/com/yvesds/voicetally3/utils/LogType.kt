package com.yvesds.voicetally3.utils

/**
 * Type van logbericht, bepaalt onder andere kleur in de UI.
 *
 * - FINAL: Einde van een herkende spraakzin (wit)
 * - PARTIAL: Tussentijds herkend fragment (grijs)
 * - PARSED_BLOCK: Herkenning van een compleet blok tekst na parsing (blauw)
 * - TALLY_UPDATE: Verhoging van een telling (groen)
 * - ERROR: Foutmelding (rood)
 * - WARNING: Waarschuwing (oranje)
 * - INFO: Algemene informatie (secundaire tekstkleur)
 */
enum class LogType {
    FINAL,
    PARTIAL,
    PARSED_BLOCK,
    TALLY_UPDATE,
    ERROR,
    WARNING,
    INFO
}
