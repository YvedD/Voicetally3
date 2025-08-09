package com.yvesds.voicetally3.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally3.managers.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URL
import javax.inject.Inject

class CSVManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefsHelper: SharedPrefsHelper,
    private val storageManager: StorageManager
) {

    companion object {
        private const val FOLDER_ASSETS = "assets"
        private const val FOLDER_EXPORTS = "exports"
        private const val FILE_SOORTEN = "soorten.csv"
        private const val MIME_CSV = "text/csv"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_PNG = "image/png"
    }

    fun ensureInitialStructure(): Boolean {
        return storageManager.ensureVoiceTallyStructure()
    }

    /**
     * 📥 Lees CSV-bestand en retourneer als lijst van rijen.
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

    /**
     * 💾 Schrijf stringinhoud naar CSV-bestand.
     */
    fun writeCsv(filePath: String, content: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null

        val (folderName, fileName) = splitPath(filePath)
        val folder = storageManager.getOrCreateSubfolder(root, folderName) ?: return null

        // Verwijder bestaande bestand
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

    /**
     * ➕ Voeg nieuwe regel toe aan CSV-bestand.
     */
    fun appendLineToCsv(folder: String, filename: String, newLine: String): Uri? {
        val existing = readCsv(folder, filename)
        val updatedLines = existing.map { it.joinToString(";") } + newLine
        val newContent = updatedLines.joinToString("\n")
        return writeCsv("$folder/$filename", newContent)
    }

    /**
     * 💾 Schrijf tekstbestand naar de exports-map.
     */
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

    /**
     * 🗺️ Download en sla een kaartbeeld op van een locatie.
     */
    fun saveMapTile(lat: Double, lon: Double, timestamp: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val exports = storageManager.getOrCreateSubfolder(root, FOLDER_EXPORTS) ?: return null

        val file = storageManager.createFile(exports, MIME_PNG, "map_$timestamp.png") ?: return null
        val tileUrl =
            "https://staticmap.openstreetmap.de/staticmap.php?center=$lat,$lon&zoom=14&size=600x400&markers=$lat,$lon,red-pushpin"

        return try {
            val input = URL(tileUrl).openStream()
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                input.copyTo(output)
            }
            input.close()
            Log.i("CSVManager", "✅ Map tile opgeslagen als ${file.uri}")
            file.uri
        } catch (e: Exception) {
            Log.e("CSVManager", "❌ Fout bij opslaan map tile: ${e.message}")
            null
        }
    }

    /**
     * 🔧 Helper om "assets/soorten.csv" te splitsen in folder + bestandsnaam.
     */
    private fun splitPath(path: String): Pair<String, String> {
        val parts = path.split("/")
        val fileName = parts.last()
        val folder = parts.dropLast(1).joinToString("/").ifEmpty { FOLDER_ASSETS }
        return Pair(folder, fileName)
    }
}
