package com.yvesds.voicetally3.utils

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * SpeechParser
 *
 * Doel:
 *  - Volledige zinnen parsen met één of MEERDERE (soortnaam + aantal) paren.
 *  - De DELIMITER is ALTIJD het getal (1..), dat hoort bij de VOORAFGAANDE soortnaam.
 *  - Een soortnaam telt 1..5 woorden. Ontbreekt het getal, dan is de default 1.
 *
 * Voorbeelden:
 *  - "aalscholver 5 bergeend 4 blauwe reiger 7"
 *  - "aalscholver5 bergeend4 blauwe reiger7"
 *  - "aalscholver 5 barent 4 blauwe reiger 3"   (fuzzy: "barent" -> "bergeend")
 *  - "… blauwe reiger 5 mergeent 5"             (fuzzy: "mergeent" -> "bergeend")
 *
 * Verwacht: aliasToSpeciesMap is plat en LOWERCASE:
 *   alias (lowercase) -> canonical (lowercase)
 */
object SpeechParser {

    data class ParseResult(val species: String, val count: Int)

    /* --------------------------- Publieke API --------------------------- */

    /**
     * Parse ALLE (soort, aantal)-paren uit één transcript.
     * @param transcript           vrije tekst van de recognizer
     * @param aliasToSpeciesMap    alias (lowercase) -> canonical (lowercase)
     * @param minFuzzyScore        globale basisdrempel; wordt adaptief toegepast
     */
    fun parseAll(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.94
    ): List<ParseResult> {
        if (transcript.isBlank() || aliasToSpeciesMap.isEmpty()) return emptyList()

        val text = normalize(transcript)
        val baseTokens = tokenize(text)
        val tokens = splitEmbeddedNumbers(baseTokens) // "bergeend4" -> ["bergeend","4"]

        val results = mutableListOf<ParseResult>()
        var i = 0
        while (i < tokens.size) {
            // Zoek het eerstvolgende GETAL (delimiter) vanaf i
            val numIdx = nextNumberIndex(tokens, i)
            if (numIdx == -1) {
                // Geen getal meer: probeer één laatste soort vóór het einde (default 1)
                val speciesTail = findSpeciesBefore(tokens, tokens.size, aliasToSpeciesMap, minFuzzyScore)
                if (speciesTail != null) results.add(ParseResult(speciesTail, 1))
                break
            }

            val countToken = tokens[numIdx]
            val countVal = countToken.toIntOrNull()
            val count = if (countVal != null && countVal > 0) countVal else 1

            // Voor dit numIdx zoeken we de beste alias in de 1..5 tokens ERVÓÓR (zonder cijfers)
            val species = findSpeciesBefore(tokens, numIdx, aliasToSpeciesMap, minFuzzyScore)

            if (species != null) {
                results.add(ParseResult(species, count))
                // Ga verder NA het getal
                i = numIdx + 1
            } else {
                // Geen soortnaam gevonden vóór het getal: sla het getal over en schuif op
                i = numIdx + 1
            }
        }

        return results
    }

    /** Backwards-compat: eerste match. */
    fun parse(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.94
    ): ParseResult? = parseAll(transcript, aliasToSpeciesMap, minFuzzyScore).firstOrNull()

    /* --------------------------- Kernhelpers --------------------------- */

    /**
     * Vind de BESTE alias in de 1..5 tokens vlak vóór endIndex (exclusief),
     * waarbij tokens met cijfers uitgesloten worden.
     * Geeft canonical species terug, of null als niets gevonden.
     */
    private fun findSpeciesBefore(
        tokens: List<String>,
        endIndexExclusive: Int,
        aliasToSpeciesMap: Map<String, String>,
        baseFuzzyScore: Double
    ): String? {
        // verzamel maximaal 5 woorden vóór endIndex die GEEN cijfers bevatten
        val words = ArrayList<String>(5)
        var idx = endIndexExclusive - 1
        while (idx >= 0 && words.size < 5) {
            val t = tokens[idx]
            if (isNumber(t)) break // stop bij een eerder nummer: dat hoort niet bij de soortnaam
            words.add(t)
            idx--
        }
        if (words.isEmpty()) return null
        words.reverse() // nu in natuurlijke volgorde

        var bestSpecies: String? = null
        var bestScore = 0.0
        var foundExact = false

        // Probeer suffixen van 5..1 woorden (woorden het dichtst bij het getal hebben prioriteit)
        val maxLen = min(5, words.size)
        for (len in maxLen downTo 1) {
            val phrase = words.takeLast(len).joinToString(" ")
            val norm = normalizePhraseSingular(phrase)

            // EXACT?
            val exact = aliasToSpeciesMap[norm]
            if (exact != null) {
                bestSpecies = exact
                foundExact = true
                break
            }

            // FUZZY met adaptieve drempel en Levenshtein-hulp
            if (!foundExact) {
                val (alias, jwScore) = bestFuzzyAlias(norm, aliasToSpeciesMap.keys)
                if (alias != null) {
                    val aliasLen = len // aantal woorden in de phrase
                    val adaptiveJW = adaptiveThresholdJW(aliasLen, baseFuzzyScore)
                    val levDist = levenshtein(norm, alias)

                    // Acceptatiecriteria:
                    // - multi-woord: JW >= 0.94 (strak)
                    // - één-woord:   JW >= 0.82  of  (levDist <= 2 en JW >= 0.80)
                    val pass = if (aliasLen >= 2) {
                        jwScore >= adaptiveJW
                    } else {
                        jwScore >= adaptiveJW || (levDist <= 2 && jwScore >= 0.80)
                    }

                    if (pass) {
                        // kies beste score; kleine bonus voor langere alias (specifieker)
                        var combined = jwScore
                        if (alias.length > norm.length) combined += 0.005
                        if (combined > bestScore) {
                            bestScore = combined
                            bestSpecies = aliasToSpeciesMap.getValue(alias)
                        } else if (combined == bestScore && bestSpecies != null) {
                            val prevAlias = aliasToSpeciesMap.entries.firstOrNull { it.value == bestSpecies }?.key
                            if (prevAlias != null && alias.length > prevAlias.length) {
                                bestSpecies = aliasToSpeciesMap.getValue(alias)
                            }
                        }
                    }
                }
            }
        }

        return bestSpecies
    }

    /** Drempel per phrase-lengte (in woorden). */
    private fun adaptiveThresholdJW(wordsInPhrase: Int, base: Double): Double {
        return if (wordsInPhrase >= 2) {
            // multi-woord: streng
            max(base, 0.94)
        } else {
            // één-woord: iets soepeler voor ASR-typos
            0.82
        }
    }

    /** Zoek index van eerstvolgend numeriek token vanaf 'from', of -1 als er geen meer is. */
    private fun nextNumberIndex(tokens: List<String>, from: Int): Int {
        for (i in from until tokens.size) {
            if (isNumber(tokens[i])) return i
        }
        return -1
    }

    /* --------------------------- Normalisatie & tokenisatie --------------------------- */

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokenize(s: String): List<String> =
        s.split(" ", "-").mapNotNull { part ->
            val t = part.trim()
            if (t.isEmpty()) null else t
        }

    /** Split combinaties zoals "bergeend4" -> ["bergeend", "4"] */
    private fun splitEmbeddedNumbers(tokens: List<String>): List<String> {
        val out = ArrayList<String>(tokens.size + 4)
        val pattern = Regex("^([a-zà-ÿ]+)(\\d+)$") // letters gevolgd door cijfers
        for (t in tokens) {
            val m = pattern.matchEntire(t)
            if (m != null) {
                out.add(m.groupValues[1])
                out.add(m.groupValues[2])
            } else {
                out.add(t)
            }
        }
        return out
    }

    private fun isNumber(token: String): Boolean = token.all { it.isDigit() }

    /** NL-enkelvoudvorming per woord in een phrase ("blauwe reigers" -> "blauwe reiger"). */
    private fun normalizePhraseSingular(phrase: String): String {
        val parts = phrase.split(" ")
        val singulars = ArrayList<String>(parts.size)
        for (w in parts) {
            val t = w.trim()
            if (t.isNotEmpty()) {
                singulars.add(singularizeNl(t))
            }
        }
        return singulars.joinToString(" ")
    }

    private fun singularizeNl(word: String): String {
        if (word.length <= 3) return word
        if (word == "ganzen") return "gans"
        if (word.endsWith("en")) {
            return if (word.endsWith("zen")) word.dropLast(3) + "s" else word.dropLast(2)
        }
        if (word.endsWith("s")) return word.dropLast(1)
        return word
    }

    /* --------------------------- Fuzzy matching --------------------------- */

    private fun bestFuzzyAlias(phrase: String, aliases: Set<String>): Pair<String?, Double> {
        var best: String? = null
        var bestScore = 0.0
        for (alias in aliases) {
            val raw = jaroWinkler(phrase, alias)
            val score = adjustedScore(raw, phrase, alias)
            if (score > bestScore) {
                bestScore = score
                best = alias
            } else if (score == bestScore && best != null) {
                // Tie-breaker: langere alias wint (meer specifiek)
                if (alias.length > best.length) {
                    best = alias
                }
            }
        }
        return best to bestScore
    }

    /**
     * Heuristieken bovenop Jaro-Winkler:
     *  - +0.02 als laatste woord gelijk is ("reiger" vs "reiger")
     *  - +0.01 als alias langer is (specifieker)
     *  - +0.01 als alias dezelfde prefix heeft als phrase
     */
    private fun adjustedScore(raw: Double, phrase: String, alias: String): Double {
        var s = raw
        val pLast = phrase.substringAfterLast(' ', phrase)
        val aLast = alias.substringAfterLast(' ', alias)
        if (pLast == aLast) s += 0.02
        if (alias.length > phrase.length) s += 0.01
        val firstWord = phrase.substringBefore(' ', phrase)
        if (alias.startsWith(firstWord)) s += 0.01
        return s.coerceIn(0.0, 1.0)
    }

    /* --------------------------- Levenshtein (simpel) --------------------------- */

    private fun levenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val del = dp[i - 1][j] + 1
                val ins = dp[i][j - 1] + 1
                val sub = dp[i - 1][j - 1] + cost
                dp[i][j] = min(min(del, ins), sub)
            }
        }
        return dp[n][m]
    }

    /* --------------------------- Jaro-Winkler implementatie --------------------------- */

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

    // Helper: aantal matches, transposities en prefixlengte
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
            while (k < s2.length && !s2Matches[k]) k++
            if (k < s2.length && s1[i] != s2[k]) transpositions++
            k++
        }

        var prefix = 0
        val limit = min(4, min(s1.length, s2.length))
        for (i in 0 until limit) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return intArrayOf(matches, transpositions / 2, prefix)
    }
}
