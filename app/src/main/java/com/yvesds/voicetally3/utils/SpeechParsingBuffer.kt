package com.yvesds.voicetally3.utils

/**
 * ✅ Buffer helper voor partial/final merge.
 * Houdt de laatste partial bij en vergelijkt met final.
 * Voorkomt dubbele verwerking.
 */
class SpeechParsingBuffer {

    private var lastPartial: String? = null

    /**
     * ✅ Bewaart de laatste partial
     */
    fun updatePartial(partial: String) {
        lastPartial = partial
    }

    /**
     * ✅ Vergelijkt final met laatste partial.
     * @return true als final verschillend is en dus verwerkt moet worden.
     */
    fun shouldProcessFinal(final: String): Boolean {
        val shouldProcess = final != lastPartial
        lastPartial = null // reset buffer na final processing
        return shouldProcess
    }

    /**
     * ✅ Optioneel: reset expliciet indien nodig
     */
    fun reset() {
        lastPartial = null
    }
}
