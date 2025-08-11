package com.yvesds.voicetally3.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Use case: verwerk een transcript (final of partial) en bepaal of er een telling uitgevoerd moet worden.
 *
 * - Maakt gebruik van SpeechParser voor aliasherkenning.
 * - Draait standaard op een achtergrondthread (dispatcher).
 * - Alias-map wordt PER AANROEP doorgegeven (past bij dynamische gebruikersselectie).
 * - Resultaat bevat soortnaam en aantal, of null als er geen match is.
 */
class SpeechParsingUseCase(
    private val dispatcher: CoroutineDispatcher
) {

    data class Result(
        val species: String,
        val count: Int
    )

    /**
     * Parse een transcript op een achtergrondthread.
     *
     * @param transcript     Het door de spraakherkenner herkende stuk tekst.
     * @param aliasToSpeciesMap  alias (lowercase) -> canonical soortnaam (lowercase)
     * @param minFuzzyScore  Minimum Jaro-Winkler score voor fuzzy alias matching.
     * @return Result of null als er geen betrouwbare match is.
     */
    suspend fun execute(
        transcript: String,
        aliasToSpeciesMap: Map<String, String>,
        minFuzzyScore: Double = 0.90
    ): Result? = withContext(dispatcher) {
        val parseResult = SpeechParser.parse(
            transcript = transcript,
            aliasToSpeciesMap = aliasToSpeciesMap,
            minFuzzyScore = minFuzzyScore
        )
        parseResult?.let { Result(it.species, it.count) }
    }
}
