package com.yvesds.voicetally3.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally3.managers.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSVManager
 *
 * - Behoudt bestaande sync-methodes (backwards compatible).
 * - Voegt suspend-varianten toe die IO op de juiste dispatcher uitvoeren.
 * - Werkt met de bestaand gebruikte VoiceTally-structuur via StorageManager.
 */
@Singleton
class CSVManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefsHelper: SharedPrefsHelper,
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val FOLDER_ASSETS = "assets"
        private const val FOLDER_EXPORTS = "exports"
        private const val FILE_SOORTEN = "soorten.csv"

        private const val MIME_CSV = "text/csv"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_PNG = "image/png"
    }

    /** Controleer/maak de initiële mappenstructuur. (sync, voor compat) */
    fun ensureInitialStructure(): Boolean = storageManager.ensureVoiceTallyStructure()

    /** Suspend-variant van ensureInitialStructure. */
    suspend fun ensureInitialStructureSuspend(): Boolean = withContext(ioDispatcher) {
        storageManager.ensureVoiceTallyStructure()
    }

    /**
     * Lees CSV-bestand en retourneer als lijst van rijen, waarbij elke rij al is opgesplitst op ';'.
     * Voor `soorten.csv` krijg je dus een lijst met telkens één kolom per rij.
     */
    fun readCsv(subFolder: String, fileName: String): List<List<String>> {
        val root = storageManager.getVoiceTallyRoot() ?: return emptyList()
        val folder = root.findFile(subFolder) ?: return emptyList()
        val file = folder.findFile(fileName) ?: return emptyList()
        return try {
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.useLines { lines ->
                lines.map { line -> line.split(";").map { it.trim() } }.toList()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij lezen CSV '$fileName': ${e.message}", e)
            emptyList()
        }
    }

    /** Suspend-variant van readCsv. */
    suspend fun readCsvSuspend(subFolder: String, fileName: String): List<List<String>> =
        withContext(ioDispatcher) { readCsv(subFolder, fileName) }

    /**
     * Schrijf stringinhoud naar CSV-bestand. Overschrijft bestaand bestand.
     * Retourneert de Uri van het nieuw aangemaakte/overschreven bestand of null bij fout.
     */
    fun writeCsv(filePath: String, content: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val (folderName, fileName) = splitPath(filePath)
        val folder = storageManager.getOrCreateSubfolder(root, folderName) ?: return null

        // Verwijder bestaand bestand (zodat we zeker "clean" schrijven)
        folder.findFile(fileName)?.delete()
        val file = storageManager.createFile(folder, MIME_CSV, fileName) ?: return null
        return try {
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                output.bufferedWriter().use { it.write(content) }
            }
            Log.i("CSVManager", "✅ CSV opgeslagen als ${file.uri}")
            file.uri
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij opslaan CSV: ${e.message}", e)
            null
        }
    }

    /** Suspend-variant van writeCsv. */
    suspend fun writeCsvSuspend(filePath: String, content: String): Uri? =
        withContext(ioDispatcher) { writeCsv(filePath, content) }

    /** Voeg één regel toe aan een CSV-bestand (append, simpele heropbouw). */
    fun appendLineToCsv(folder: String, filename: String, newLine: String): Uri? {
        val existing = readCsv(folder, filename)
        val updatedLines = existing.map { it.joinToString(";") } + newLine
        val newContent = updatedLines.joinToString("\n")
        return writeCsv("$folder/$filename", newContent)
    }

    /** Suspend-variant van appendLineToCsv. */
    suspend fun appendLineToCsvSuspend(folder: String, filename: String, newLine: String): Uri? =
        withContext(ioDispatcher) { appendLineToCsv(folder, filename, newLine) }

    /** Schrijf een TXT/log-bestand in exports. */
    fun writeTxt(fileName: String, content: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val folder = storageManager.getOrCreateSubfolder(root, FOLDER_EXPORTS) ?: return null
        folder.findFile(fileName)?.delete()
        val file = storageManager.createFile(folder, MIME_TEXT, fileName) ?: return null
        return try {
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                output.bufferedWriter().use { it.write(content) }
            }
            Log.i("CSVManager", "✅ TXT/log opgeslagen als ${file.uri}")
            file.uri
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij opslaan TXT: ${e.message}", e)
            null
        }
    }

    /** Suspend-variant van writeTxt. */
    suspend fun writeTxtSuspend(fileName: String, content: String): Uri? =
        withContext(ioDispatcher) { writeTxt(fileName, content) }

    /**
     * Download en sla een kaart-tegel (static map) op in exports, bestandsnaam op basis van timestamp.
     * ⚠️ Netwerk-I/O: gebruik bij voorkeur de suspend-variant.
     */
    fun saveMapTile(lat: Double, lon: Double, timestamp: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val exports = storageManager.getOrCreateSubfolder(root, FOLDER_EXPORTS) ?: return null
        val file = storageManager.createFile(exports, MIME_PNG, "map_$timestamp.png") ?: return null
        val tileUrl =
            "https://staticmap.openstreetmap.de/staticmap.php?center=$lat,$lon&zoom=14&size=600x400&markers=$lat,$lon,red-pushpin"
        return try {
            URL(tileUrl).openStream().use { input ->
                context.contentResolver.openOutputStream(file.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            Log.i("CSVManager", "✅ Map tile opgeslagen als ${file.uri}")
            file.uri
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij opslaan map tile: ${e.message}", e)
            null
        }
    }

    /** Suspend-variant van saveMapTile. */
    suspend fun saveMapTileSuspend(lat: Double, lon: Double, timestamp: String): Uri? =
        withContext(ioDispatcher) { saveMapTile(lat, lon, timestamp) }

    /** Lees de **eerste** ruwe tekstregel uit een CSV-bestand (zonder splitting). */
    fun readFirstLine(subFolder: String, fileName: String): String? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val folder = root.findFile(subFolder) ?: return null
        val file = folder.findFile(fileName) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readLine()
            }
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij lezen eerste regel van '$fileName': ${e.message}", e)
            null
        }
    }

    /** Suspend-variant van readFirstLine. */
    suspend fun readFirstLineSuspend(subFolder: String, fileName: String): String? =
        withContext(ioDispatcher) { readFirstLine(subFolder, fileName) }

    /** Helper om "assets/soorten.csv" te splitsen in folder + bestandsnaam. */
    private fun splitPath(path: String): Pair<String, String> {
        val parts = path.split("/")
        val fileName = parts.last()
        val folder = parts.dropLast(1).joinToString("/").ifEmpty { FOLDER_ASSETS }
        return folder to fileName
    }
}
