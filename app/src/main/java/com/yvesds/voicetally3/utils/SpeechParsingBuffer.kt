package com.yvesds.voicetally3.utils

/**
 * Houdt de meest recente PARTIAL en FINAL transcriptfragmenten bij tijdens spraakherkenning.
 *
 * Doelen:
 * - UI kan PARTIAL live tonen zonder dat FINAL al binnen is.
 * - Bij FINAL wordt PARTIAL automatisch opgeruimd.
 * - Consumers kunnen op elk moment veilig het laatste fragment ophalen of “consumeren”.
 *
 * Thread-safety:
 * - Alle muterende operaties zijn @Synchronized.
 * - Lezers die geen mutatie doen (peek) zijn lock-vrij en lezen @Volatile velden.
 */
class SpeechParsingBuffer {

    @Volatile
    private var lastPartial: String? = null

    @Volatile
    private var lastFinal: String? = null

    /**
     * Update/zet het laatste PARTIAL-fragment.
     * Lege of whitespace-only invoer wordt genegeerd (zet partial dan naar null).
     */
    @Synchronized
    fun updatePartial(text: String?) {
        lastPartial = normalize(text)
    }

    /**
     * Push een FINAL-fragment:
     * - Bewaart het FINAL-fragment
     * - Ruimt PARTIAL op (die is nu achterhaald)
     */
    @Synchronized
    fun pushFinal(text: String?) {
        lastFinal = normalize(text)
        lastPartial = null
    }

    /** Leeg beide buffers. */
    @Synchronized
    fun reset() {
        lastPartial = null
        lastFinal = null
    }

    /** Lees het huidige PARTIAL-fragment zonder te wijzigen. */
    fun peekPartial(): String? = lastPartial

    /** Lees het huidige FINAL-fragment zonder te wijzigen. */
    fun peekFinal(): String? = lastFinal

    /**
     * Consumeer (haal op én wis) het FINAL-fragment als dat bestaat; anders null.
     * (PARTIAL blijft onaangeroerd.)
     */
    @Synchronized
    fun consumeFinal(): String? {
        val out = lastFinal
        lastFinal = null
        return out
    }

    /**
     * Consumeer (haal op én wis) FINAL als beschikbaar, anders PARTIAL.
     * Beide buffers worden leeggemaakt wanneer PARTIAL wordt geconsumeerd.
     */
    @Synchronized
    fun consumeAny(): String? {
        val out = lastFinal ?: lastPartial
        lastFinal = null
        lastPartial = null
        return out
    }

    private fun normalize(s: String?): String? =
        s?.trim()?.takeIf { it.isNotEmpty() }
}
