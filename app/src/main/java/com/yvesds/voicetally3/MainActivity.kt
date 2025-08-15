package com.yvesds.voicetally3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.yvesds.voicetally3.data.CSVManager
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.managers.StorageManager
import com.yvesds.voicetally3.ui.main.MapDialogFragment
import com.yvesds.voicetally3.ui.tally.ResultsDialogFragment
import com.yvesds.voicetally3.utils.SoundPlayer
import com.yvesds.voicetally3.utils.UiHelper
import com.yvesds.voicetally3.utils.weather.WeatherManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var csvManager: CSVManager
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var soundPlayer: SoundPlayer
    @Inject lateinit var weatherManager: WeatherManager

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_SAF_URI = "saf_uri"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            if (sharedPrefsHelper.getBoolean("auto_save_per_hour", false)) {
                val calendar = Calendar.getInstance()
                val minute = calendar.get(Calendar.MINUTE)
                if (minute == 58) {
                    soundPlayer.play("bell")
                    Log.d(TAG, "⏰ Auto-save trigger om minuut 58.")
                    showResultsDialog()
                }
                handler.postDelayed(this, 60_000)
            }
        }
    }

    private val safPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                try {
                    val takeFlags = result.data?.flags?.and(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    ) ?: 0
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    sharedPrefsHelper.setString(KEY_SAF_URI, uri.toString())
                    Log.d(TAG, "✅ Gebruiker koos SAF: $uri")

                    // Structuur opbouwen off-main
                    lifecycleScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            csvManager.ensureInitialStructureSuspend()
                        }
                        Log.d(TAG, if (success) "✅ Structuur opgebouwd na SAF-keuze" else "❌ Structuuropbouw faalde")
                        requestAllRuntimePermissions()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ Kon permissie niet nemen", e)
                }
            } else {
                Log.w(TAG, "⚠️ SAF Picker geannuleerd of gaf null URI.")
                requestAllRuntimePermissions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        updateAutoSaveTimer()
        ensureDocumentsAccessAndSetup()
    }

    private fun ensureDocumentsAccessAndSetup() {
        val safUriString = sharedPrefsHelper.getString(KEY_SAF_URI)
        lifecycleScope.launch {
            val hasStructure = withContext(Dispatchers.IO) {
                storageManager.ensureVoiceTallyStructure()
            }
            if (!hasStructure || safUriString == null) {
                Log.w(TAG, "⚠️ Geen toegang of structuur ontbreekt. User prompt nodig.")
                launchDocumentsPicker()
            } else {
                Log.i(TAG, "✅ Documents + VoiceTally structuur OK.")
                requestAllRuntimePermissions()
            }
        }
    }

    private fun launchDocumentsPicker() {
        val documentsUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsUri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        safPickerLauncher.launch(intent)
    }

    private fun requestAllRuntimePermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 1001)
        } else {
            Log.d(TAG, "✅ Alle benodigde runtime permissies zijn al verleend")
            loadSpeciesCacheAndInitApp()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val denied = grantResults.indices.filter { grantResults[it] != PackageManager.PERMISSION_GRANTED }
            if (denied.isEmpty()) {
                loadSpeciesCacheAndInitApp()
            } else {
                Log.w(TAG, "❌ Niet alle permissies verleend: ${denied.map { permissions[it] }}")
            }
        }
    }

    /** Laad lichte init (bestandslijst) off-main en start daarna de navigatie. */
    private fun loadSpeciesCacheAndInitApp() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val root = storageManager.getVoiceTallyRoot()
                root?.findFile("assets")
                    ?.listFiles()
                    ?.count { it.name?.endsWith(".csv") == true && it.name != "soorten.csv" }
                    ?: 0
            }
            Log.i(TAG, "✅ Soortenbestanden gevonden: $count aliassenbestanden")
            initNavigation()
        }
    }

    private fun initNavigation() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        if (navHost == null) {
            Log.e(TAG, "❌ NavHostFragment niet gevonden.")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.settingsFragment)
                true
            }
            R.id.action_exit_app -> {
                UiHelper.showExitConfirmationDialog(this) { shutdownApp() }
                true
            }
            R.id.action_show_map -> {
                MapDialogFragment().show(supportFragmentManager, "MapDialog")
                true
            }
            R.id.action_weather -> {
                fetchAndShowWeather()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchAndShowWeather() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    lifecycleScope.launch { weatherManager.showWeatherDialog(this@MainActivity) }
                } else {
                    showLocationErrorDialog("Locatie kon niet worden bepaald.")
                }
            }
            .addOnFailureListener { showLocationErrorDialog("Fout bij ophalen van de locatie.") }
    }

    private fun showLocationErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Locatiefout")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHost?.childFragmentManager?.fragments?.firstOrNull()
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && currentFragment is com.yvesds.voicetally3.ui.tally.TallyFragment) {
            currentFragment.triggerSpeechRecognition()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun updateAutoSaveTimer() {
        handler.removeCallbacks(autoSaveRunnable)
        if (sharedPrefsHelper.getBoolean("auto_save_per_hour", false)) {
            handler.post(autoSaveRunnable)
            Log.d(TAG, "⏰ Auto-save timer gestart")
        } else {
            Log.d(TAG, "⏸️ Auto-save timer gestopt")
        }
    }

    private fun showResultsDialog() {
        ResultsDialogFragment().show(supportFragmentManager, "ResultsDialog")
    }

    private fun shutdownApp() {
        soundPlayer.release()
        handler.removeCallbacks(autoSaveRunnable)
        try {
            val locationService = getSystemService(Context.LOCATION_SERVICE)
            if (locationService is android.location.LocationManager) {
                // geen actieve listeners hier; placeholder try/catch blijft defensief
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Fout bij stoppen van GPS-updates: ${e.message}")
        }
        try {
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Fout bij cache opruimen", e)
        }
        finishAffinity()
        exitProcess(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoSaveRunnable)
        soundPlayer.release()
    }
}
