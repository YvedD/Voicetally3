package com.yvesds.voicetally3.utils

import org.junit.Assert.*
import org.junit.Test

class  SpeechParserTest {

    private val aliasMap = mapOf(
        "boerenzwaluw" to listOf("boertjes", "boerenzwaluw"),
        "bonte strandloper" to listOf("bontjes", "bonte strandloper"),
        "aalscholver" to listOf("aalscholver", "alsgolver"),
        "bergeend" to listOf("bergeend", "barent"),
        "blauwe reiger" to listOf("blauwe reiger", "blauwe regen")
    )

    @Test
    fun testExactAliasMatch() {
        val input = "boertjes 3"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(1, result.size)
        assertEquals("boerenzwaluw", result[0].first)
        assertEquals(3, result[0].second)
    }

    @Test
    fun testSingularizeMatch() {
        val input = "bontjes 2"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(1, result.size)
        assertEquals("bonte strandloper", result[0].first)
        assertEquals(2, result[0].second)
    }

    @Test
    fun testPhoneticMetaphoneMatch() {
        val input = "alsgolver 5"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(1, result.size)
        assertEquals("aalscholver", result[0].first)
        assertEquals(5, result[0].second)
    }

    @Test
    fun testLevenshteinFallbackMatch() {
        val input = "barent 4"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(1, result.size)
        assertEquals("bergeend", result[0].first)
        assertEquals(4, result[0].second)
    }

    @Test
    fun testMultipleSpeciesParsing() {
        val input = "aalscholver 2 bergeend 3 blauwe reiger 4"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(3, result.size)
        assertEquals("aalscholver", result[0].first)
        assertEquals(2, result[0].second)
        assertEquals("bergeend", result[1].first)
        assertEquals(3, result[1].second)
        assertEquals("blauwe reiger", result[2].first)
        assertEquals(4, result[2].second)
    }

    @Test
    fun testUnknownSpeciesFallback() {
        val input = "onbestaande 3"
        val result = SpeechParser.extractSpeciesChunks(input, aliasMap)
        assertEquals(1, result.size)
        assertEquals("onbestaande", result[0].first)
        assertEquals(3, result[0].second)
    }
}
