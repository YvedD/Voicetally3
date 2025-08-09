package com.yvesds.voicetally3.utils

import org.apache.commons.codec.language.DoubleMetaphone
import kotlin.math.min

object SpeechParser {

    private val doubleMetaphone = DoubleMetaphone().apply { maxCodeLen = 6 }

    private val textToNumber = mapOf(
        "een" to 1, "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5,
        "zes" to 6, "zeven" to 7, "acht" to 8, "negen" to 9, "tien" to 10,
        "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14, "vijftien" to 15,
        "zestien" to 16, "zeventien" to 17, "achttien" to 18, "negentien" to 19, "twintig" to 20,
        "wave" to 5 // Voor vaak verkeerd herkende "vijf"
    )

    fun extractSpeciesChunks(
        spokenText: String,
        aliasToSpeciesMap: Map<String, String>
    ): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        val processedSpecies = mutableSetOf<String>()
        val words = spokenText.lowercase().split("\\s+".toRegex())
        val buffer = mutableListOf<String>()

        var i = 0
        while (i < words.size) {
            val word = words[i]
            val number = word.toIntOrNull() ?: textToNumber[word]

            if (number != null && buffer.isNotEmpty()) {
                val species = parseBuffer(buffer, aliasToSpeciesMap, processedSpecies)
                if (species != null) {
                    results.add(species to number)
                }
                buffer.clear()
            } else if (number == null) {
                buffer.add(word)
            }

            i++
        }

        if (buffer.isNotEmpty()) {
            val species = parseBuffer(buffer, aliasToSpeciesMap, processedSpecies)
            if (species != null) {
                results.add(species to 1)
            }
        }

        return results
    }

    private fun parseBuffer(
        buffer: List<String>,
        aliasToSpeciesMap: Map<String, String>,
        processed: MutableSet<String>
    ): String? {
        val raw = buffer.joinToString(" ").trim()
        val matched = matchAlias(raw, aliasToSpeciesMap)
        val canonical = matched ?: raw.replaceFirstChar { it.uppercase() }

        return if (canonical !in processed) {
            processed.add(canonical)
            canonical
        } else null
    }

    private fun matchAlias(input: String, aliasMap: Map<String, String>): String? {
        val cleanInput = input.lowercase().trim()
        val singularInput = singularize(cleanInput)

        // 1. Exact match
        aliasMap[cleanInput]?.let { return it }
        aliasMap[singularInput]?.let { return it }

        // Prepare for fuzzy matching
        val inputMetaPrimary = doubleMetaphone.doubleMetaphone(cleanInput)
        val inputMetaAlt = doubleMetaphone.doubleMetaphone(cleanInput, true)

        var bestMatch: String? = null
        var bestScore = 0.0

        aliasMap.forEach { (alias, species) ->
            val aliasClean = alias.lowercase().trim()
            val aliasMeta1 = doubleMetaphone.doubleMetaphone(aliasClean)
            val aliasMeta2 = doubleMetaphone.doubleMetaphone(aliasClean, true)

            val metaphoneMatch = inputMetaPrimary == aliasMeta1 || inputMetaAlt == aliasMeta2

            // Jaro-Winkler
            val jaroScore = jaroWinkler(cleanInput, aliasClean)

            val isGoodScore = jaroScore > 0.90 || metaphoneMatch

            if (isGoodScore && jaroScore > bestScore) {
                bestScore = jaroScore
                bestMatch = species
            }
        }

        return bestMatch
    }

    private fun singularize(word: String): String =
        when {
            word.endsWith("en") -> word.dropLast(2)
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }

    private fun jaroWinkler(s1: String, s2: String): Double {
        val jaro = jaroSimilarity(s1, s2)
        val prefixLength = s1.zip(s2).takeWhile { it.first == it.second }.count().coerceAtMost(4)
        return jaro + (0.1 * prefixLength * (1 - jaro))
    }

    private fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxDist = (maxOf(s1.length, s2.length) / 2) - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        for (i in s1.indices) {
            val start = maxOf(0, i - maxDist)
            val end = min(i + maxDist + 1, s2.length)

            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }

        if (matches == 0) return 0.0

        var k = 0
        for (i in s1.indices) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) transpositions++
                k++
            }
        }

        return (matches / s1.length.toDouble() +
                matches / s2.length.toDouble() +
                (matches - transpositions / 2.0) / matches) / 3.0
    }
}
