package com.yvesds.voicetally3.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AliasRepository
 *
 * - Behoudt sync-methodes voor compatibiliteit met bestaande code.
 * - Biedt suspend-varianten die CSVManager's suspend-methodes gebruiken.
 * - Bestanden:
 *   - assets/soorten.csv             => 1 soort per regel (lowercase in jouw pipeline)
 *   - assets/<soort>.csv             => 1 regel met aliassen, gescheiden door ';'
 */
@Singleton
class AliasRepository @Inject constructor(
    private val csvManager: CSVManager,
    private val storageManager: com.yvesds.voicetally3.managers.StorageManager
) {

    companion object {
        private const val FOLDER = "assets"
        private const val EXT = ".csv"
        private const val FILE_SOORTEN = "soorten.csv" // ✅ toegevoegd
    }
    /** Sync: lees alle soorten uit soorten.csv (1 per regel). */
    fun getAllSpecies(): List<String> {
        return try {
            csvManager.readCsv(FOLDER, "soorten.csv")
                .mapNotNull { it.firstOrNull()?.trim() }
                .filter { it.isNotEmpty() }
                .sortedBy { it.lowercase() }
        } catch (e: Exception) {
            Log.e("AliasRepository", "❌ getAllSpecies failed: ${e.message}", e)
            emptyList()
        }
    }

    /** Suspend: asynchrone variant van getAllSpecies(). */
    suspend fun getAllSpeciesSuspend(): List<String> {
        return csvManager.readCsvSuspend(FOLDER, "soorten.csv")
            .mapNotNull { it.firstOrNull()?.trim() }
            .filter { it.isNotEmpty() }
            .sortedBy { it.lowercase() }
    }

    /** Sync: lees aliassen voor een soort uit assets/<soort>.csv (1 regel met ';'). */
    fun loadAliasesForSpecies(speciesName: String): List<String> {
        val fileName = "${speciesName.trim().lowercase()}$EXT"
        // readCsv splitst reeds op ';' => flatten volstaat
        val rows = csvManager.readCsv(FOLDER, fileName)
        val aliases = rows.flatten()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        Log.d("AliasRepository", "✅ Aliassen geladen voor '$speciesName': ${aliases.size}")
        return aliases
    }

    /** Suspend: asynchrone variant van loadAliasesForSpecies(). */
    suspend fun loadAliasesForSpeciesSuspend(speciesName: String): List<String> {
        val fileName = "${speciesName.trim().lowercase()}$EXT"
        val rows = csvManager.readCsvSuspend(FOLDER, fileName)
        return rows.flatten()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Sla aliassen van een soort op in assets/<soort>.csv (overschrijft).
     * Sync-variant voor compat; gebruik bij voorkeur de suspend-versie.
     */
    fun saveAliasesForSpecies(speciesName: String, aliases: List<String>): Boolean {
        val fileName = "${speciesName.trim().lowercase()}$EXT"
        val content = aliases.joinToString(";")
        val success = csvManager.writeCsv("$FOLDER/$fileName", content) != null
        if (success) {
            Log.d("AliasRepository", "✅ Aliassen opgeslagen voor '$speciesName'")
        } else {
            Log.e("AliasRepository", "❌ Opslaan mislukt voor '$speciesName'")
        }
        return success
    }

    /** Suspend-variant van saveAliasesForSpecies(). */
    suspend fun saveAliasesForSpeciesSuspend(speciesName: String, aliases: List<String>): Boolean =
        withContext(Dispatchers.IO) { saveAliasesForSpecies(speciesName, aliases) }

    /**
     * Bouw alias → soort map voor een set geselecteerde soorten.
     * Sync-variant; zie ook suspend-variant hieronder.
     */
    fun buildAliasToSpeciesMap(selectedSpecies: Set<String>): Map<String, String> {
        val result = HashMap<String, String>(selectedSpecies.size * 4)
        for (species in selectedSpecies) {
            val canonical = species.trim().lowercase()
            // canonical naam zelf ook als alias:
            result[canonical] = species
            for (alias in loadAliasesForSpecies(species)) {
                val clean = alias.trim().lowercase()
                if (clean.isNotEmpty()) result[clean] = species
            }
        }
        Log.d("AliasRepository", "✅ Aliasmap opgebouwd (${result.size} aliassen)")
        return result
    }

    /** Suspend-variant van buildAliasToSpeciesMap(). */
    suspend fun buildAliasToSpeciesMapSuspend(selectedSpecies: Set<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = HashMap<String, String>(selectedSpecies.size * 4)
            for (species in selectedSpecies) {
                val canonical = species.trim().lowercase()
                result[canonical] = species
                for (alias in loadAliasesForSpeciesSuspend(species)) {
                    val clean = alias.trim().lowercase()
                    if (clean.isNotEmpty()) result[clean] = species
                }
            }
            result
        }

    /** Bouw fallback alias→soort map voor ALLE soorten (sync). */
    fun buildFallbackAliasToSpeciesMap(): Map<String, String> {
        val result = HashMap<String, String>()
        for (species in getAllSpecies()) {
            for (alias in loadAliasesForSpecies(species)) {
                val clean = alias.trim().lowercase()
                if (clean.isNotEmpty()) result[clean] = species
            }
        }
        Log.d("AliasRepository", "✅ Fallback aliasmap opgebouwd (${result.size} aliassen)")
        return result
    }

    /** Suspend-variant van de fallback map. */
    suspend fun buildFallbackAliasToSpeciesMapSuspend(): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = HashMap<String, String>()
            for (species in getAllSpeciesSuspend()) {
                for (alias in loadAliasesForSpeciesSuspend(species)) {
                    val clean = alias.trim().lowercase()
                    if (clean.isNotEmpty()) result[clean] = species
                }
            }
            result
        }

    /**
     * Zorg dat aliasbestand voor een soort bestaat (assets/<soort>.csv), maak aan indien nodig.
     * Bestond al als suspend in jouw code: behouden en licht opgeruimd.
     */
    suspend fun ensureSpeciesFile(species: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = "${species.trim().lowercase()}$EXT"
            val root = storageManager.getVoiceTallyRoot() ?: return@withContext false
            val folder = storageManager.getOrCreateSubfolder(root, FOLDER) ?: return@withContext false
            val exists = folder.findFile(fileName) != null
            if (!exists) {
                storageManager.createFile(folder, "text/csv", fileName)?.let {
                    Log.i("AliasRepository", "🆕 Aliasbestand aangemaakt: $fileName")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AliasRepository", "❌ Fout bij aanmaken aliasbestand: ${e.message}", e)
            false
        }
    }

    /** Voeg (unieke) soort toe aan soorten.csv (sync). */
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
        val success = csvManager.writeCsv("$FOLDER/$FILE_SOORTEN", content) != null
        if (success) {
            Log.i("AliasRepository", "✅ '$trimmed' toegevoegd aan soorten.csv")
        } else {
            Log.e("AliasRepository", "❌ Kon '$trimmed' niet toevoegen aan soorten.csv")
        }
        return success
    }
}
