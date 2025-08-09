package com.yvesds.voicetally3.ui.tally

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.managers.StorageManager
import com.yvesds.voicetally3.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally3.utils.weather.WeatherManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ResultsDialogFragment : DialogFragment() {

    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var weatherManager: WeatherManager

    private var mapView: MapView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_results)

        val tallyMap = sharedSpeciesViewModel.tallyMap.value.orEmpty()
        val txtResults = dialog.findViewById<TextView>(R.id.txtResults)
        mapView = dialog.findViewById(R.id.mapViewResults)

        val location = sharedSpeciesViewModel.gpsLocation.value
        val lat = location?.first ?: 51.0
        val lon = location?.second ?: 4.0

        lifecycleScope.launch {
            val weather = weatherManager.fetchFullWeather(requireContext())

            val message = buildString {
                append("📊 Tellingsoverzicht:\n\n")
                tallyMap.entries.sortedBy { it.key }.forEach { entry ->
                    append("${entry.key}: ${entry.value}\n")
                }

                if (weather != null) {
                    append("\n🌦️ Weerbericht\n\n")
                    append("📍 Locatie: ${weather.locationName}\n")
                    append("🕒 Tijdstip: ${weather.time}\n")
                    append("🌡️ Temp: ${"%.1f".format(weather.temperature)} °C\n")
                    append("🌧️ Neerslag: ${weather.precipitation} mm\n")
                    append("🌬️ Wind: ${weather.windspeed} km/u (${weatherManager.toBeaufort(weather.windspeed)} Bf), ${weatherManager.toCompass(weather.winddirection)}\n")
                    append("☁️ Bewolking: ${weatherManager.toOctas(weather.cloudcover)}/8\n")
                    append("👁️ Zicht: ${weather.visibility} m\n")
                    append("🧭 Luchtdruk: ${weather.pressure * 100} Pa\n")
                    append("📝 Weer: ${weatherManager.getWeatherDescription(weather.weathercode)}\n")
                }
            }

            txtResults.text = message

            initMap(lat, lon)

            dialog.findViewById<Button>(R.id.btnOpslaanDelenReset).setOnClickListener {
                saveAllFiles(tallyMap, dialog.window?.decorView, weather, true)
                sharedSpeciesViewModel.resetAll()
                dismiss()
            }

            dialog.findViewById<Button>(R.id.btnNieuweSessie).setOnClickListener {
                saveAllFiles(tallyMap, dialog.window?.decorView, weather, false)
                sharedSpeciesViewModel.resetAll()
                dismiss()
            }
        }

        return dialog
    }

    private fun initMap(lat: Double, lon: Double) {
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))

        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(lat, lon))
            setMultiTouchControls(true)

            val marker = Marker(this)
            marker.position = GeoPoint(lat, lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = "GPS locatie"
            overlays.add(marker)
        }
    }

    private fun takeScreenshotOfView(view: View?): Bitmap {
        val bitmap = Bitmap.createBitmap(view!!.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun saveAllFiles(
        tallyMap: Map<String, Int>,
        view: View?,
        weather: WeatherManager.FullWeather?,
        triggerShare: Boolean
    ) {
        val location = sharedSpeciesViewModel.gpsLocation.value
        val lat = location?.first
        val lon = location?.second
        val timestamp = getTimestamp()

        val bitmap = takeScreenshotOfView(view)
        val screenshotUri = saveScreenshotBitmap(bitmap, timestamp)

        val uris = saveCsvAndTxt(tallyMap, lat, lon, weather, timestamp).toMutableList()
        screenshotUri?.let { uris.add(it) }

        if (triggerShare && uris.isNotEmpty()) shareFiles(ArrayList(uris))
    }

    private fun saveCsvAndTxt(
        tallyMap: Map<String, Int>,
        lat: Double?,
        lon: Double?,
        weather: WeatherManager.FullWeather?,
        timestamp: String
    ): List<Uri> {
        val uris = mutableListOf<Uri>()

        val csvFileName = "Telling_$timestamp.csv"
        val start = sharedSpeciesViewModel.sessionStart.value?.let {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "?"
        val end = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val csvContent = buildString {
            append("GPS;Latitude;${lat ?: "NA"};Longitude;${lon ?: "NA"}\n")
            append("Aanvang;$start;Einde;$end\n")
            if (weather != null) {
                append("Weer;Locatie;${weather.locationName}\n")
                append("Tijd;${weather.time}\n")
                append("Temperatuur;${"%.1f".format(weather.temperature)} °C\n")
                append("Neerslag;${weather.precipitation} mm\n")
                append("Wind;${weather.windspeed} km/u;Beaufort;${weatherManager.toBeaufort(weather.windspeed)};Richting;${weatherManager.toCompass(weather.winddirection)}\n")
                append("Bewolking;${weatherManager.toOctas(weather.cloudcover)}/8\n")
                append("Zicht;${weather.visibility} m\n")
                append("Luchtdruk;${weather.pressure * 100} Pa\n")
                append("Omschrijving;${weatherManager.getWeatherDescription(weather.weathercode)}\n")
            }
            append("Soortnaam;Aantal\n")
            tallyMap.entries.sortedBy { it.key }.forEach { entry ->
                append("${entry.key};${entry.value}\n")
            }
        }

        val csvUri = saveTextFile(csvFileName, csvContent, "text/csv")
        csvUri?.let { uris.add(it) }

        val txtFileName = "log_$timestamp.txt"
        val txtContent = buildString {
            append("=== VoiceTally Log $timestamp ===\n\n")
            append(sharedSpeciesViewModel.exportAllSpeechLogs())
            if (weather != null) {
                append("\n--- Weerbericht ---\n")
                append("Locatie: ${weather.locationName}\n")
                append("Tijd: ${weather.time}\n")
                append("Temp: ${"%.1f".format(weather.temperature)} °C\n")
                append("Neerslag: ${weather.precipitation} mm\n")
                append("Wind: ${weather.windspeed} km/u (${weatherManager.toBeaufort(weather.windspeed)} Bf), ${weatherManager.toCompass(weather.winddirection)}\n")
                append("Bewolking: ${weatherManager.toOctas(weather.cloudcover)}/8\n")
                append("Zicht: ${weather.visibility} m\n")
                append("Luchtdruk: ${weather.pressure * 100} Pa\n")
                append("Omschrijving: ${weatherManager.getWeatherDescription(weather.weathercode)}\n")
            }
        }

        val txtUri = saveTextFile(txtFileName, txtContent, "text/plain")
        txtUri?.let { uris.add(it) }

        return uris
    }

    private fun saveScreenshotBitmap(bitmap: Bitmap, timestamp: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val exports = storageManager.getOrCreateSubfolder(root, "exports") ?: return null

        val fileName = "location_$timestamp.png"
        val file = storageManager.createFile(exports, "image/png", fileName) ?: return null

        return try {
            context?.contentResolver?.openOutputStream(file.uri)?.use { output: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            file.uri
        } catch (e: Exception) {
            null
        }
    }

    private fun saveTextFile(fileName: String, content: String, mimeType: String): Uri? {
        val root = storageManager.getVoiceTallyRoot() ?: return null
        val exports = storageManager.getOrCreateSubfolder(root, "exports") ?: return null
        val file = storageManager.createFile(exports, mimeType, fileName) ?: return null

        return try {
            context?.contentResolver?.openOutputStream(file.uri)?.use { output ->
                output.bufferedWriter().use { it.write(content) }
            }
            file.uri
        } catch (e: Exception) {
            null
        }
    }

    private fun shareFiles(uris: ArrayList<Uri>) {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_SUBJECT, "Telling export")
            putExtra(Intent.EXTRA_TEXT, "Bijlagen bevatten de export van de telling, logbestand en screenshot.")
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Delen met..."))
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
}
