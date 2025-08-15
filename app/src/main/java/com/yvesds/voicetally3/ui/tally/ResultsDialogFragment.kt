package com.yvesds.voicetally3.ui.tally

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.*
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
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
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

    // UI
    private var txtResults: TextView? = null

    // Huidige (live) status die we in de UI renderen
    private var currentTally: Map<String, Int> = emptyMap()
    private var currentWeather: WeatherManager.FullWeather? = null
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var currentPlace: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.dialog_results, container, false)

        txtResults = root.findViewById(R.id.txtResults)
        mapView = root.findViewById(R.id.mapViewResults)

        // Init: tally + startlocatie
        currentTally = sharedSpeciesViewModel.tallyMap.value.orEmpty()
        val vmLoc = sharedSpeciesViewModel.gpsLocation.value
        currentLat = vmLoc?.first ?: 51.0
        currentLon = vmLoc?.second ?: 4.0

        // OSMDroid + kaart
        initMap(currentLat ?: 51.0, currentLon ?: 4.0)

        // Weer laden + initiele plaatsnaam ophalen, daarna UI renderen
        viewLifecycleOwner.lifecycleScope.launch {
            currentWeather = withContext(ioDispatcher) { weatherManager.fetchFullWeather(requireContext()) }
            // Haal plaatsnaam bij initiele coordinaten (kan verschillen van weather.locationName)
            currentPlace = withContext(ioDispatcher) {
                val lat = currentLat ?: 51.0
                val lon = currentLon ?: 4.0
                weatherManager.getLocalityName(requireContext(), lat, lon)
            }
            renderResultsText()

            // Vraag nog een precieze locatie-update (update marker + plaatsnaam + UI)
            requestPreciseLocationUpdate()

            root.findViewById<View>(R.id.btnOpslaanDelenReset).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    saveAllFiles(currentTally, root, currentWeather, triggerShare = true)
                    sharedSpeciesViewModel.resetAll()
                    dismissAllowingStateLoss()
                }
            }
            root.findViewById<View>(R.id.btnNieuweSessie).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    saveAllFiles(currentTally, root, currentWeather, triggerShare = false)
                    sharedSpeciesViewModel.resetAll()
                    dismissAllowingStateLoss()
                }
            }
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Bouwt de kaart en registreert listeners die realtime de UI bijwerken. */
    private fun initMap(lat: Double, lon: Double) {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0)
        )
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
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
                            onMapPositionChanged(gp.latitude, gp.longitude, withAccuracy = null)
                        }
                    }
                    override fun onMarkerDragStart(p0: Marker?) {}
                })
            }
            marker = m
            overlays.add(m)

            // Long-press/tap verplaatsen
            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        marker?.position = p
                        controller.animateTo(p)
                        onMapPositionChanged(p.latitude, p.longitude, withAccuracy = null)
                        return true
                    }
                    return false
                }
            })
            overlays.add(eventsOverlay)
        }
    }

    /**
     * Wordt aangeroepen bij:
     *  - einde drag van de marker
     *  - long-press nieuwe plek
     *  - precise location update
     *
     * Doet:
     *  - center kaart (optioneel al gebeurd)
     *  - update accuracy circle (indien meegegeven)
     *  - reverse geocode naar plaatsnaam (IO)
     *  - update ViewModel gps + UI (plaats + coords)
     */
    private fun onMapPositionChanged(lat: Double, lon: Double, withAccuracy: Double?) {
        currentLat = lat
        currentLon = lon

        // Update accuracy circle (kan null zijn)
        val gp = GeoPoint(lat, lon)
        updateAccuracyCircle(gp, withAccuracy)

        // Schrijf nieuwe GPS weg naar de gedeelde VM
        sharedSpeciesViewModel.setGpsLocation(lat, lon)

        // Haal plaatsnaam async op en render daarna UI
        viewLifecycleOwner.lifecycleScope.launch {
            currentPlace = withContext(ioDispatcher) {
                weatherManager.getLocalityName(requireContext(), lat, lon)
            }
            renderResultsText()
        }
    }

    /**
     * Precise location vragen en dan de positie/plaatsnaam doorvoeren.
     */
    private fun requestPreciseLocationUpdate() {
        val fused = LocationServices.getFusedLocationProviderClient(requireActivity())
        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val lat = loc.latitude
                    val lon = loc.longitude
                    mapView?.controller?.animateTo(GeoPoint(lat, lon))
                    marker?.position = GeoPoint(lat, lon)
                    val acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
                    onMapPositionChanged(lat, lon, acc)
                }
            }
            .addOnFailureListener {
                // Geen update; we blijven op de huidige waarden
            }
    }

    /**
     * Teken of update een eenvoudige “accuracy circle” rond de marker.
     * @param center middelpunt
     * @param radiusMeters straal in meters; als null of <=0, verwijder de cirkel
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

    /** Render de resultaten-tekst op basis van de huidige state (tally, weer, locatie). */
    private fun renderResultsText() {
        val t = StringBuilder()

        // Locatieblok bovenaan, altijd zichtbaar met 9 decimalen
        val latStr = currentLat?.let { String.format(Locale.US, "%.9f", it) } ?: "NA"
        val lonStr = currentLon?.let { String.format(Locale.US, "%.9f", it) } ?: "NA"
        val place = currentPlace ?: currentWeather?.locationName ?: "Onbekend"
        t.append(" Locatie: $place ($latStr, $lonStr)\n\n")

        // Tellingen
        t.append(" Tellingsoverzicht:\n\n")
        currentTally.entries.sortedBy { it.key }.forEach { entry ->
            t.append("${entry.key}: ${entry.value}\n")
        }

        // Weer (optioneel)
        currentWeather?.let { w ->
            t.append("\n️ Weerbericht\n\n")
            t.append(" Tijdstip: ${w.time}\n")
            t.append("️ Temp: ${"%.1f".format(w.temperature)} °C\n")
            t.append("️ Neerslag: ${w.precipitation} mm\n")
            t.append("️ Wind: ${w.windspeed} km/u (${weatherManager.toBeaufort(w.windspeed)} Bf), ${weatherManager.toCompass(w.winddirection)}\n")
            t.append("☁️ Bewolking: ${weatherManager.toOctas(w.cloudcover)}/8\n")
            t.append("️ Zicht: ${w.visibility} m\n")
            t.append(" Luchtdruk: ${w.pressure} hPa\n")
            t.append(" Weer: ${weatherManager.getWeatherDescription(w.weathercode)}\n")
        }

        txtResults?.text = t.toString()
    }

    private fun takeScreenshotOfView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private suspend fun saveAllFiles(
        tallyMap: Map<String, Int>,
        rootView: View,
        weather: WeatherManager.FullWeather?,
        triggerShare: Boolean
    ) {
        val lat = currentLat
        val lon = currentLon
        val timestamp = getTimestamp()

        // Screenshot tekenen (UI) en wegschrijven (IO)
        val bitmap = takeScreenshotOfView(rootView)
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
            weather?.let { w ->
                append("Weer;Locatie;${currentPlace ?: w.locationName}\n")
                append("Tijd;${w.time}\n")
                append("Temperatuur;${"%.1f".format(w.temperature)} °C\n")
                append("Neerslag;${w.precipitation} mm\n")
                append("Wind;${w.windspeed} km/u;Beaufort;${weatherManager.toBeaufort(w.windspeed)};Richting;${weatherManager.toCompass(w.winddirection)}\n")
                append("Bewolking;${weatherManager.toOctas(w.cloudcover)}/8\n")
                append("Zicht;${w.visibility} m\n")
                append("Luchtdruk;${w.pressure} hPa\n")
                append("Omschrijving;${weatherManager.getWeatherDescription(w.weathercode)}\n")
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
            // Neem de reeds opgebouwde UI-tekst bovenaan ook mee (locatie + coords)
            append("Locatie: ${currentPlace ?: "Onbekend"} (${String.format(Locale.US, "%.9f", currentLat ?: 0.0)}, ${String.format(Locale.US, "%.9f", currentLon ?: 0.0)})\n\n")
            append(sharedSpeciesViewModel.exportAllSpeechLogs())
            weather?.let { w ->
                append("\n--- Weerbericht ---\n")
                append("Locatie: ${currentPlace ?: w.locationName}\n")
                append("Tijd: ${w.time}\n")
                append("Temp: ${"%.1f".format(w.temperature)} °C\n")
                append("Neerslag: ${w.precipitation} mm\n")
                append("Wind: ${w.windspeed} km/u (${weatherManager.toBeaufort(w.windspeed)} Bf), ${weatherManager.toCompass(w.winddirection)}\n")
                append("Bewolking: ${weatherManager.toOctas(w.cloudcover)}/8\n")
                append("Zicht: ${w.visibility} m\n")
                append("Luchtdruk: ${w.pressure} hPa\n")
                append("Omschrijving: ${weatherManager.getWeatherDescription(w.weathercode)}\n")
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
