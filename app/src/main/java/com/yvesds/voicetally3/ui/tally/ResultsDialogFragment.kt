package com.yvesds.voicetally3.ui.tally

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.managers.StorageManager
import com.yvesds.voicetally3.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally3.utils.weather.WeatherManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class ResultsDialogFragment : DialogFragment() {

    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var weatherManager: WeatherManager
    @Inject @Named("IO") lateinit var ioDispatcher: CoroutineDispatcher

    private var mapView: MapView? = null
    private var marker: Marker? = null
    private var accuracyCircle: Polygon? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_results)

        val tallyMap: Map<String, Int> = sharedSpeciesViewModel.tallyMap.value.orEmpty()
        val txtResults: TextView = dialog.findViewById(R.id.txtResults)
        mapView = dialog.findViewById(R.id.mapViewResults)

        // Fallback-coördinaten indien (nog) geen GPS bekend is
        val location = sharedSpeciesViewModel.gpsLocation.value
        val lat = location?.first ?: 51.0
        val lon = location?.second ?: 4.0

        viewLifecycleOwner.lifecycleScope.launch {
            val weather = withContext(ioDispatcher) { weatherManager.fetchFullWeather(requireContext()) }

            val message = buildString {
                append(" Tellingsoverzicht:\n\n")
                tallyMap.entries.sortedBy { it.key }.forEach { entry ->
                    append("${entry.key}: ${entry.value}\n")
                }
                if (weather != null) {
                    append("\n️ Weerbericht\n\n")
                    append(" Locatie: ${weather.locationName}\n")
                    append(" Tijdstip: ${weather.time}\n")
                    append("️ Temp: ${"%.1f".format(weather.temperature)} °C\n")
                    append("️ Neerslag: ${weather.precipitation} mm\n")
                    append("️ Wind: ${weather.windspeed} km/u (${weatherManager.toBeaufort(weather.windspeed)} Bf), ${weatherManager.toCompass(weather.winddirection)}\n")
                    append("☁️ Bewolking: ${weatherManager.toOctas(weather.cloudcover)}/8\n")
                    append("️ Zicht: ${weather.visibility} m\n")
                    append(" Luchtdruk: ${weather.pressure} hPa\n")
                    append(" Weer: ${weatherManager.getWeatherDescription(weather.weathercode)}\n")
                }
            }
            txtResults.text = message

            initMap(lat, lon)
            requestPreciseLocationUpdate()

            dialog.findViewById<View>(R.id.btnOpslaanDelenReset).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    saveAllFiles(tallyMap, dialog.window?.decorView, weather, triggerShare = true)
                    sharedSpeciesViewModel.resetAll()
                    dismiss()
                }
            }
            dialog.findViewById<View>(R.id.btnNieuweSessie).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    saveAllFiles(tallyMap, dialog.window?.decorView, weather, triggerShare = false)
                    sharedSpeciesViewModel.resetAll()
                    dismiss()
                }
            }
        }

        return dialog
    }

    private fun initMap(lat: Double, lon: Double) {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0)
        )
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0) // iets dichterbij voor nauwkeuriger gevoel
            controller.setCenter(GeoPoint(lat, lon))

            // Marker (sleepbaar om kleine correcties toe te laten)
            val m = Marker(this).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "GPS locatie"
                isDraggable = true
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(p0: Marker?) {}
                    override fun onMarkerDragEnd(p0: Marker?) {
                        p0?.position?.let { gp ->
                            controller.animateTo(gp)
                            updateAccuracyCircle(gp, null)
                        }
                    }
                    override fun onMarkerDragStart(p0: Marker?) {}
                })
            }
            marker = m
            overlays.add(m)

            // Long-press/tap verplaatsen
            val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        marker?.position = p
                        controller.animateTo(p)
                        updateAccuracyCircle(p, null)
                        return true
                    }
                    return false
                }
            })
            overlays.add(eventsOverlay)
        }
    }

    /**
     * Vraag een precieze, recente locatie op en update marker + (optioneel) accuraatheids-cirkel.
     */
    private fun requestPreciseLocationUpdate() {
        val fused = LocationServices.getFusedLocationProviderClient(requireActivity())
        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val gp = GeoPoint(loc.latitude, loc.longitude)
                    mapView?.controller?.animateTo(gp)
                    marker?.position = gp
                    val acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
                    updateAccuracyCircle(gp, acc)
                    sharedSpeciesViewModel.setGpsLocation(loc.latitude, loc.longitude)
                }
            }
            .addOnFailureListener {
                // geen update; blijven op fallback
            }
    }

    /**
     * Teken of update een eenvoudige “accuracy circle” rond de marker.
     * @param center middelpunt
     * @param radiusMeters straal in meters; als null, verwijder de cirkel
     */
    private fun updateAccuracyCircle(center: GeoPoint, radiusMeters: Double?) {
        val mv = mapView ?: return
        accuracyCircle?.let { mv.overlays.remove(it) }
        accuracyCircle = null
        if (radiusMeters != null && radiusMeters > 0) {
            val circle = Polygon(mv).apply {
                points = Polygon.pointsAsCircle(center, radiusMeters)
                outlinePaint.strokeWidth = 2f
                outlinePaint.alpha = 160
                fillPaint.alpha = 60
            }
            accuracyCircle = circle
            mv.overlays.add(circle)
        }
        mv.invalidate()
    }

    private fun takeScreenshotOfView(view: View?): Bitmap {
        val v = view ?: throw IllegalStateException("Decor view is null")
        val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        v.draw(canvas)
        return bitmap
    }

    private suspend fun saveAllFiles(
        tallyMap: Map<String, Int>,
        view: View?,
        weather: WeatherManager.FullWeather?,
        triggerShare: Boolean
    ) {
        // Gebruik de (eventueel handmatig gecorrigeerde) markerpositie als waarheid voor export.
        val currentPos = marker?.position
        val lat = currentPos?.latitude ?: sharedSpeciesViewModel.gpsLocation.value?.first
        val lon = currentPos?.longitude ?: sharedSpeciesViewModel.gpsLocation.value?.second
        val timestamp = getTimestamp()

        // Screenshot tekenen (UI) en wegschrijven (IO)
        val bitmap = takeScreenshotOfView(view)
        val screenshotUri = withContext(ioDispatcher) { saveScreenshotBitmap(bitmap, timestamp) }
        val uris = withContext(ioDispatcher) {
            saveCsvAndTxt(tallyMap, lat, lon, weather, timestamp)
        }.toMutableList()
        screenshotUri?.let { uris.add(it) }

        if (triggerShare && uris.isNotEmpty()) {
            shareFiles(ArrayList(uris))
        }
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
                append("Luchtdruk;${weather.pressure} hPa\n")
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
                append("Luchtdruk: ${weather.pressure} hPa\n")
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            null
        }
    }

    private fun shareFiles(uris: ArrayList<Uri>) {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_SUBJECT, "Telling export")
            putExtra(
                Intent.EXTRA_TEXT,
                "Bijlagen bevatten de export van de telling, logbestand en screenshot."
            )
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Delen met..."))
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }
}
