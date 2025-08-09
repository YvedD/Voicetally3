package com.yvesds.voicetally3.utils.aliasses

/**
 * Représente één regel in het soortenbestand.
 *
 * @param canonicalName De officiële soortnaam (kolom 0).
 * @param aliases De aliassen bij de soort, max 20 (kolom 1 t.e.m. 20).
 */
data class BirdEntry(
    val canonicalName: String,
    val aliases: MutableList<String> = mutableListOf()
)
