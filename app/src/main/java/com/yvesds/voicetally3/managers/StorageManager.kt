package com.yvesds.voicetally3.managers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally3.data.SharedPrefsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefsHelper: SharedPrefsHelper
) {

    companion object {
        private const val TAG = "StorageManager"
        private const val KEY_SAF_URI = "saf_uri"
    }

    /**
     * 🔍 Haal de root VoiceTally folder op vanuit een SAF URI string
     */
    fun getVoiceTallyRoot(): DocumentFile? {
        val safUriString = sharedPrefsHelper.getString(KEY_SAF_URI)
        if (safUriString == null) {
            Log.w(TAG, "⚠️ Geen SAF URI ingesteld")
            return null
        }

        val rootUri = Uri.parse(safUriString)
        val root = DocumentFile.fromTreeUri(context, rootUri)

        if (root == null) {
            Log.e(TAG, "❌ Kon root DocumentFile niet ophalen")
            return null
        }

        val voiceTallyFolder = if (root.name.equals("VoiceTally", ignoreCase = true)) {
            root
        } else {
            root.findFile("VoiceTally") ?: root.createDirectory("VoiceTally")
        }

        if (voiceTallyFolder == null) {
            Log.e(TAG, "❌ VoiceTally folder kon niet worden aangemaakt of gevonden")
        }

        return voiceTallyFolder
    }

    /**
     * 🔧 Haal of maak een subfolder binnen VoiceTally aan
     */
    fun getOrCreateSubfolder(parent: DocumentFile?, name: String): DocumentFile? {
        if (parent == null) return null

        val existing = parent.findFile(name)
        return if (existing != null && existing.isDirectory) {
            existing
        } else {
            parent.createDirectory(name).also {
                if (it == null) Log.e(TAG, "❌ Kon subfolder '$name' niet aanmaken")
            }
        }
    }

    /**
     * ✅ Zorg dat de mappenstructuur (VoiceTally/assets/exports + soorten.csv) bestaat
     */
    fun ensureVoiceTallyStructure(): Boolean {
        val safUriString = sharedPrefsHelper.getString(KEY_SAF_URI)
        val safUri = safUriString?.let { Uri.parse(it) }

        val hasPermission = safUri != null && context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == safUri && perm.isReadPermission && perm.isWritePermission
        }

        if (!hasPermission) {
            Log.w(TAG, "❌ Geen toegang tot Documents folder. User prompt nodig.")
            return false
        }

        val documents = DocumentFile.fromTreeUri(context, safUri!!) ?: return false

        val voiceTally = documents.findFile("VoiceTally") ?: documents.createDirectory("VoiceTally")
        val assets = voiceTally?.findFile("assets") ?: voiceTally?.createDirectory("assets")
        val exports = voiceTally?.findFile("exports") ?: voiceTally?.createDirectory("exports")

        if (assets == null || exports == null) {
            Log.e(TAG, "❌ Kon assets of exports map niet aanmaken.")
            return false
        }

        // Zorg dat soorten.csv bestaat
        val soortenCsv = assets.findFile("soorten.csv")
        if (soortenCsv == null) {
            assets.createFile("text/csv", "soorten.csv")?.let { file ->
                context.contentResolver.openOutputStream(file.uri)?.use {
                    it.write("".toByteArray())
                }
                Log.i(TAG, "✅ soorten.csv aangemaakt in assets.")
            }
        }

        Log.i(TAG, "✅ VoiceTally folderstructuur is volledig aanwezig.")
        return true
    }

    /**
     * 📄 Haal een bestand op binnen een opgegeven folder
     */
    fun getFile(folder: DocumentFile?, fileName: String): DocumentFile? {
        if (folder == null) return null
        return folder.findFile(fileName)
    }

    /**
     * 📄 Overload voor root vanuit een Uri (meestal onnodig)
     */
    fun getVoiceTallyRoot(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }

    /**
     * 📝 Maak of overschrijf een bestand binnen een folder
     */
    fun createFile(folder: DocumentFile?, mimeType: String, fileName: String): DocumentFile? {
        if (folder == null) return null

        val existing = folder.findFile(fileName)
        if (existing != null) {
            if (!existing.delete()) {
                Log.w(TAG, "⚠️ Kon bestaand bestand $fileName niet verwijderen.")
                return null
            }
        }

        return folder.createFile(mimeType, fileName)
    }
}
