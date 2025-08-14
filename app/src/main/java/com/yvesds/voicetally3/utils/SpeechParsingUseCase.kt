package com.yvesds.voicetally3.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Use case voor speech-parsing.
 * - executeAll: retourneert ALLE (soort, aantal)-paren uit een transcript.
 * - execute: behoudt compat; geeft enkel de eerste (of null) terug.
 */
class SpeechParsingUseCase(
    private val dispatcher: CoroutineDispatcher
) {

    data class Result(val species: String, val count: Int)

    suspend fun executeAll(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.94
    ): List<Result> = withContext(dispatcher) {
        SpeechParser.parseAll(
            transcript = transcript,
            aliasToSpeciesMap = aliasToSpeciesMap,
            minFuzzyScore = minFuzzyScore
        ).map { Result(it.species, it.count) }
    }

    // Backward compat: eerste resultaat
    suspend fun execute(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.94
    ): Result? = withContext(dispatcher) {
        SpeechParser.parse(
            transcript = transcript,
            aliasToSpeciesMap = aliasToSpeciesMap,
            minFuzzyScore = minFuzzyScore
        )?.let { Result(it.species, it.count) }
    }
}
