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
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSVManager
 *
 * - Behoudt sync-methodes (backwards compatible).
 * - Voegt *suspend*-varianten toe die IO op de juiste dispatcher uitvoeren.
 * - Werkt samen met StorageManager voor SAF-ops.
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

    /** Controle / init van de mappenstructuur (sync voor back-compat). */
    fun ensureInitialStructure(): Boolean = storageManager.ensureVoiceTallyStructure()

    /** Suspend-variant van ensureInitialStructure (off-main). */
    suspend fun ensureInitialStructureSuspend(): Boolean = withContext(ioDispatcher) {
        storageManager.ensureVoiceTallyStructure()
    }

    /**
     * Lees CSV-bestand en retourneer als lijst van rijen,
     * waarbij elke rij reeds is opgesplitst op ';'.
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
            Log.e("CSVManager", "❌ Fout bij lezen CSV '$fileName': ${e.message}")
            emptyList()
        }
    }

    /** Suspend-variant van readCsv (off-main). */
    suspend fun readCsvSuspend(subFolder: String, fileName: String): List<List<String>> =
        withContext(ioDispatcher) { readCsv(subFolder, fileName) }

    /**
     * Schrijf stringinhoud naar CSV-bestand. Overschrijft bestaand bestand.
     * Retourneert de Uri van het bestand of null bij fout.
     */
    fun writeCsv(filePath: String, content: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val (folderName, fileName) = splitPath(filePath)
        val folder = storageManager.getOrCreateSubfolder(root, folderName) ?: return null

        // Verwijder bestaand bestand (clean write)
        folder.findFile(fileName)?.delete()
        val file = storageManager.createFile(folder, MIME_CSV, fileName) ?: return null
        return try {
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                output.bufferedWriter().use { it.write(content) }
            }
            Log.i("CSVManager", "✅ CSV opgeslagen als ${file.uri}")
            file.uri
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij opslaan CSV: ${e.message}")
            null
        }
    }

    /** Suspend-variant van writeCsv. */
    suspend fun writeCsvSuspend(filePath: String, content: String): Uri? =
        withContext(ioDispatcher) { writeCsv(filePath, content) }

    /** Voeg een regel toe (append) via heropbouw (simpel & veilig). */
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
            Log.e("CSVManager", "❌ Fout bij opslaan TXT: ${e.message}")
            null
        }
    }

    /** Suspend-variant van writeTxt. */
    suspend fun writeTxtSuspend(fileName: String, content: String): Uri? =
        withContext(ioDispatcher) { writeTxt(fileName, content) }

    /**
     * Download en sla een kaart-tegel op in exports, bestandsnaam op basis van timestamp.
     * ⚠️ Voor netwerk-I/O gebruik bij voorkeur de *suspend*-variant.
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
            Log.e("CSVManager", "❌ Fout bij opslaan map tile: ${e.message}")
            null
        }
    }

    /** Suspend-variant van saveMapTile. */
    suspend fun saveMapTileSuspend(lat: Double, lon: Double, timestamp: String): Uri? =
        withContext(ioDispatcher) { saveMapTile(lat, lon, timestamp) }

    /** Helper om "assets/soorten.csv" te splitsen in folder + bestandsnaam. */
    private fun splitPath(path: String): Pair<String, String> {
        val parts = path.split("/")
        val fileName = parts.last()
        val folder = parts.dropLast(1).joinToString("/").ifEmpty { FOLDER_ASSETS }
        return folder to fileName
    }
}
