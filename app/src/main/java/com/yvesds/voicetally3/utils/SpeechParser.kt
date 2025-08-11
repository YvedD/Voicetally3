package com.yvesds.voicetally3.utils

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * SpeechParser
 *
 * Herkent eenvoudige tel-commando's in (Nederlands) spraakteksten en koppelt aliassen aan soortnamen.
 * - Voorbeelden die herkend worden:
 *   "merel", "2 merels", "twee merels", "tel 3 koolmees", "nog 1 aalscholver", "plus vijf vink"
 * - Ondersteuning voor cijfers (1, 2, 3, ...) en basis-woordgetallen ("een", "twee", ... "twintig").
 * - Aliassen worden gezocht via exacte match, case-insensitief; als dat faalt, via Jaro-Winkler fuzzy match.
 * - Resultaat: (speciesName, count), of null als geen betrouwbare soort werd gevonden.
 *
 * Let op:
 * - Geef hier een *platte* alias-map door: alias (lowercase) -> canonical soortnaam (lowercase).
 *   Voorbeeld: mapOf("koolmees" to "koolmees", "mees" to "koolmees", "merel" to "merel")
 */
object SpeechParser {

    data class ParseResult(
        val species: String,
        val count: Int
    )

    // Basiswoordenschat voor getallen (NL). Uit te breiden indien nodig.
    private val numberWords: Map<String, Int> = mapOf(
        "nul" to 0, "zero" to 0,
        "een" to 1, "één" to 1, "en" to 1, // sommige engines maken hier "en" van
        "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5,
        "zes" to 6, "zeven" to 7, "acht" to 8, "negen" to 9,
        "tien" to 10, "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14,
        "vijftien" to 15, "zestien" to 16, "zeventien" to 17, "achttien" to 18, "negentien" to 19,
        "twintig" to 20
    )

    // Woorden die een verhoging impliceren, handig voor natuurlijke zinnen
    private val incrementHints = setOf("tel", "plus", "nog", "erbij", "er bij", "bij", "extra")

    /**
     * Hoofdfunctie: parse een transcript en probeer (soort, aantal) af te leiden.
     * @param transcript vrije tekst (van recognizer)
     * @param aliasToSpeciesMap alias (lowercase) -> canonical soortnaam (lowercase)
     * @param minFuzzyScore drempel voor fuzzy alias match (0.0 ... 1.0)
     */
    fun parse(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.90
    ): ParseResult? {
        if (transcript.isBlank() || aliasToSpeciesMap.isEmpty()) return null

        val text = normalize(transcript)
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        // 1) Zoek aantallen in de buurt van alias-woorden
        val count = detectCount(tokens).coerceAtLeast(1)

        // 2) Probeer een alias te vinden (eerst exact, dan fuzzy)
        val species = detectSpecies(tokens, aliasToSpeciesMap, minFuzzyScore) ?: return null

        return ParseResult(species = species, count = count)
    }

    /**
     * Probeer een soort (canonical name) te vinden op basis van tokens.
     * - Eerst exacte alias-match (case-insensitief, enkelvoud/meervoud).
     * - Zo niet, fuzzy match met Jaro-Winkler tegen alle aliassen.
     */
    private fun detectSpecies(
        tokens: List<String>,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double
    ): String? {
        // Exact match, met simpele singularisatie
        tokens.forEach { t ->
            val cand = aliasToSpeciesMap[t] ?: aliasToSpeciesMap[singularizeNl(t)]
            if (cand != null) return cand
        }

        // Fuzzy match met de beste score boven de drempel
        var bestAlias: String? = null
        var bestScore = 0.0

        val aliases = aliasToSpeciesMap.keys
        for (t in tokens) {
            val ts = singularizeNl(t)
            for (alias in aliases) {
                val score = jaroWinkler(ts, alias)
                if (score > bestScore) {
                    bestScore = score
                    bestAlias = alias
                }
            }
        }

        return if (bestScore >= minFuzzyScore) aliasToSpeciesMap[bestAlias] else null
    }

    /**
     * Haal een tel-waarde uit tokens.
     * - Neemt de eerste ‘waarschijnlijke’ waarde die gevonden wordt.
     * - Ondersteunt cijfers en basiswoordgetallen.
     * - Als incrementHints aanwezig zijn zonder expliciet getal, val terug op 1.
     */
    private fun detectCount(tokens: List<String>): Int {
        var hinted = false
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t in incrementHints) hinted = true

            // Cijfer?
            if (t.all { it.isDigit() }) {
                return t.toIntOrNull()?.takeIf { it > 0 } ?: 1
            }

            // Woordgetal?
            numberWords[t]?.let { if (it > 0) return it }

            // Soms komt een samengesteld woord met spatie-artefacts binnen (bv. "vijf en twintig"),
            // dat ondersteunen we hier beperkt door te kijken naar "een"/"en" als 1.
            if (i + 1 < tokens.size) {
                val two = "${t} ${tokens[i + 1]}"
                numberWords[t]?.let { left ->
                    numberWords[tokens[i + 1]]?.let { right ->
                        val sum = left + right
                        if (sum > 0) return sum
                    }
                }
                // heel eenvoudige  "vijf en" -> 6 work-around is onwenselijk; houden we bij enkelvoudig gebruik.
            }
        }

        // Indien een hint gevonden is maar geen getal, reken 1
        return if (hinted) 1 else 0
    }

    /**
     * Normaliseer tekst: lowercase, trim, vervang dubbele spaties.
     */
    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ\\s-]"), " ") // verwijder vreemde leestekens, behoud letters/cijfers/spaties
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Tokeniseer op spaties en koppelwerktekens.
     */
    private fun tokenize(s: String): List<String> =
        s.split(" ", "-").mapNotNull {
            val t = it.trim()
            if (t.isEmpty()) null else t
        }

    /**
     * Eenvoudige NL-singularisatie:
     * - verwijdert meervouds-suffixen: "s", "en" (merels -> merel, ganzen -> gans)
     * - specifieke uitzonderingen kunnen toegevoegd worden
     */
    private fun singularizeNl(word: String): String {
        if (word.length <= 3) return word
        // Algemene regels
        if (word.endsWith("en")) {
            // ganzen -> gans
            if (word.endsWith("zen")) return word.dropLast(3) + "s"
            return word.dropLast(2)
        }
        if (word.endsWith("s")) return word.dropLast(1)

        // Uitzonderingen (breid uit indien nodig)
        return when (word) {
            "ganzen" -> "gans"
            else -> word
        }
    }

    /**
     * Jaro-Winkler similarity (0.0 .. 1.0).
     * Compacte implementatie zonder externe libs.
     */
    private fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val mtp = matches(s1, s2)
        val m = mtp[0].toDouble()
        if (m == 0.0) return 0.0
        val j = (m / s1.length + m / s2.length + (m - mtp[1]) / m) / 3.0
        val p = 0.1
        val l = min(4, mtp[2]).toDouble()
        return j + l * p * (1 - j)
    }

    // Helper voor Jaro-Winkler: aantal matches, transposities en prefixlengte
    private fun matches(s1: String, s2: String): IntArray {
        val maxLen = max(s1.length, s2.length)
        val matchDist = floor(maxLen / 2.0).toInt() - 1

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - matchDist)
            val end = min(i + matchDist + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return intArrayOf(0, 0, 0)

        var k = 0
        var transpositions = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        var prefix = 0
        for (i in 0 until min(4, min(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return intArrayOf(matches, transpositions / 2, prefix)
    }
}
