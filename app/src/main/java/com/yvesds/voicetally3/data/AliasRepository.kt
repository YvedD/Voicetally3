package com.yvesds.voicetally3.data

import android.util.Log
import com.yvesds.voicetally3.managers.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AliasRepository @Inject constructor(
    private val csvManager: CSVManager,
    private val storageManager: StorageManager
) {

    companion object {
        private const val FOLDER = "assets"
        private const val EXT = ".csv"
    }

    /**
     * 📥 Laad aliassen van een soort uit assets/[soort].csv
     */
    fun loadAliasesForSpecies(speciesName: String): List<String> {
        val filename = "${speciesName.trim().lowercase()}$EXT"
        val rows = csvManager.readCsv(FOLDER, filename)

        val aliases = rows.firstOrNull()
            ?.flatMap { it.split(";") }
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        Log.d("AliasRepository", "✅ Aliassen geladen voor '$speciesName': ${aliases.size}")
        return aliases
    }

    /**
     * 💾 Sla aliassen van een soort op in assets/[soort].csv
     */
    fun saveAliasesForSpecies(speciesName: String, aliases: List<String>): Boolean {
        val filename = "${speciesName.trim().lowercase()}$EXT"
        val content = aliases.joinToString(";")

        val success = csvManager.writeCsv("$FOLDER/$filename", content) != null
        if (success) {
            Log.d("AliasRepository", "✅ Aliassen opgeslagen voor '$speciesName'")
        } else {
            Log.e("AliasRepository", "❌ Opslaan mislukt voor '$speciesName'")
        }
        return success
    }

    /**
     * 🧭 Bouw alias → soort map op op basis van geselecteerde soorten (spraakherkenning)
     */
    fun buildAliasToSpeciesMap(selectedSpecies: Set<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        selectedSpecies.forEach { species ->
            val canonical = species.trim().lowercase()
            result[canonical] = species  // ⬅️ Voeg canonical naam toe
            val aliases = loadAliasesForSpecies(species)
            aliases.forEach { alias ->
                val clean = alias.trim().lowercase()
                if (clean.isNotEmpty()) {
                    result[clean] = species
                }
            }
        }
        Log.d("AliasRepository", "✅ Aliasmap opgebouwd (${result.size} aliassen)")
        return result
    }


    /**
     * 🔄 Bouw fallback map (alias -> soort) voor ALLE soorten
     */
    fun buildFallbackAliasToSpeciesMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val allSpecies = getAllSpecies()

        allSpecies.forEach { species ->
            val aliases = loadAliasesForSpecies(species)
            aliases.forEach { alias ->
                val clean = alias.trim().lowercase()
                if (clean.isNotEmpty()) {
                    result[clean] = species
                }
            }
        }
        Log.d("AliasRepository", "📦 Fallback aliasmap opgebouwd (${result.size} aliassen)")
        return result
    }

    /**
     * 📁 Zorg dat een aliasbestand voor een soort bestaat, en maak het aan indien nodig
     */
    suspend fun ensureSpeciesFile(species: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = "${species.trim().lowercase()}$EXT"
            val root = storageManager.getVoiceTallyRoot() ?: return@withContext false
            val folder = storageManager.getOrCreateSubfolder(root, FOLDER) ?: return@withContext false

            val exists = folder.findFile(fileName) != null
            if (!exists) {
                storageManager.createFile(folder, "text/csv", fileName)?.let {
                    Log.i("AliasRepository", "📁 Nieuw aliasbestand aangemaakt: $fileName")
                }
            }

            true
        } catch (e: Exception) {
            Log.e("AliasRepository", "❌ Fout bij aanmaken aliasbestand: ${e.message}")
            false
        }
    }

    /**
     * 📜 Lees alle soortnamen uit soorten.csv (1 soort per regel)
     */
    fun getAllSpecies(): List<String> {
        return csvManager.readCsv(FOLDER, "soorten.csv")
            .mapNotNull { it.firstOrNull()?.trim() }
            .filter { it.isNotEmpty() }
            .sortedBy { it.lowercase() }
    }

    /**
     * ➕ Voeg nieuwe soort toe aan soorten.csv (indien nog niet aanwezig)
     */
    fun addSpecies(species: String): Boolean {
        val trimmed = species.trim()
        if (trimmed.isEmpty()) return false

        val all = getAllSpecies()
        if (all.any { it.equals(trimmed, ignoreCase = true) }) {
            Log.w("AliasRepository", "⚠️ Soort '$trimmed' bestaat al in soorten.csv")
            return false
        }

        val updated = (all + trimmed).sortedBy { it.lowercase() }
        val content = updated.joinToString("\n")

        val success = csvManager.writeCsv("assets/soorten.csv", content) != null
        if (success) {
            Log.i("AliasRepository", "✅ '$trimmed' toegevoegd aan soorten.csv")
        } else {
            Log.e("AliasRepository", "❌ Kon '$trimmed' niet toevoegen aan soorten.csv")
        }
        return success
    }
}
