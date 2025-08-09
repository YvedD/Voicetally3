package com.yvesds.voicetally3.utils.training

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally3.data.SharedPrefsHelper

class TrainingCsvWriter(
    private val context: Context,
    private val sharedPrefsHelper: SharedPrefsHelper
) {
    companion object {
        private const val TAG = "TrainingCsvWriter"
        private const val CSV_NAME = "soorten.csv"
    }

    private fun getCsvFile(): DocumentFile? {
        val safUriString = sharedPrefsHelper.getString("saf_uri") ?: return null
        val rootUri = Uri.parse(safUriString)

        // 🎯 Stap 1: Rootmap ophalen
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null

        // 🎯 Stap 2: Zoek "VoiceTally" submap binnen root
        val voiceTallyFolder = if (root.name?.equals("VoiceTally", ignoreCase = true) == true) {
            root
        } else {
            root.findFile("VoiceTally") ?: root.createDirectory("VoiceTally")
        } ?: return null

        // 🎯 Stap 3: Zoek of maak "assets" binnen VoiceTally
        val assetsFolder = voiceTallyFolder.findFile("assets")
            ?: voiceTallyFolder.createDirectory("assets")
        if (assetsFolder == null) {
            Log.e(TAG, "❌ Kan submap 'assets' niet vinden of aanmaken in VoiceTally")
            return null
        }

        // 🎯 Stap 4: Zoek of maak 'soorten.csv'
        return assetsFolder.findFile(CSV_NAME)
            ?: assetsFolder.createFile("text/csv", CSV_NAME)
    }

    fun appendAliasToSpecies(species: String, alias: String) {
        val file = getCsvFile() ?: return
        val lines = mutableListOf<String>()

        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.useLines {
            lines.addAll(it)
        }

        val speciesLower = species.lowercase()
        val existingLine = lines.find { it.split(";").getOrNull(1) == speciesLower }

        val updatedLines = if (existingLine != null) {
            lines.map {
                if (it == existingLine && !it.contains(alias, ignoreCase = true)) {
                    "$it;$alias"
                } else {
                    it
                }
            }
        } else {
            lines + "$species;$speciesLower;$alias"
        }

        context.contentResolver.openOutputStream(file.uri, "w")?.bufferedWriter()?.use { writer ->
            updatedLines.forEach { line ->
                writer.write(line.trimEnd(';'))
                writer.newLine()
            }
        }

        Log.d(TAG, "✅ Alias '$alias' toegevoegd aan '$species'")
    }

    fun addNewSpeciesLine(species: String) {
        val file = getCsvFile() ?: return
        val lines = mutableListOf<String>()

        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.useLines {
            lines.addAll(it)
        }

        val exists = lines.any {
            val lower = it.split(";").getOrNull(1)?.trim()?.lowercase()
            lower == species.lowercase()
        }

        if (!exists) {
            lines.add("$species;${species.lowercase()}")

            context.contentResolver.openOutputStream(file.uri, "w")?.bufferedWriter()?.use { writer ->
                lines.forEach { line ->
                    writer.write(line.trimEnd(';')) // 🧹 verwijder trailing delimiters
                    writer.newLine()               // ✅ echte newline forceren
                }
            }

            Log.d(TAG, "✅ Soort '$species' toegevoegd met line-break")
        }
    }

}
