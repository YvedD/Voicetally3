package com.yvesds.voicetally3.ui.tally

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Button
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class ResultsDialogFragment : DialogFragment() {

    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var weatherManager: WeatherManager

    private var mapView: MapView? = null
    private var marker: Marker? = null
    private var accuracyCircle: Polygon? = null
    private var locationCts: CancellationTokenSource? = null

    private var txtResults: TextView? = null
    private var cachedWeather: WeatherManager.FullWeather? = null
    private var cachedTally: Map<String, Int> = emptyMap()

    /** Huidige plaatsnaam op basis van de (marker)coördinaten. */
    private var currentPlaceName: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_results)

        val tallyMap = sharedSpeciesViewModel.tallyMap.value.orEmpty()
        cachedTally = tallyMap
        txtResults = dialog.findViewById(R.id.txtResults)
        mapView = dialog.findViewById(R.id.mapViewResults)

        // Fallback-coördinaten indien (nog) geen GPS bekend is
        val location = sharedSpeciesViewModel.gpsLocation.value
        val lat = location?.first ?: 51.0
        val lon = location?.second ?: 4.0

        lifecycleScope.launch {
            val weather = weatherManager.fetchFullWeather(requireContext())
            cachedWeather = weather

            initMap(lat, lon)

            // Start met reverse-geocoding op de initiële positie
            reverseGeocodeAndUpdate(lat, lon)

            // Eerste render van het resultaatsscherm met actuele data
            updateResultsText()

            // Vraag éénmalig een nauwkeurige actuele fix op; guard tegen dialog-dismiss.
            requestPreciseLocationUpdate()

            dialog.findViewById<Button>(R.id.btnOpslaanDelenReset).setOnClickListener {
                saveAllFiles(cachedTally, dialog.window?.decorView, cachedWeather, true)
                sharedSpeciesViewModel.resetAll()
                dismissAllowingStateLoss()
            }
            dialog.findViewById<Button>(R.id.btnNieuweSessie).setOnClickListener {
                saveAllFiles(cachedTally, dialog.window?.decorView, cachedWeather, false)
                sharedSpeciesViewModel.resetAll()
                dismissAllowingStateLoss()
            }
        }

        return dialog
    }

    /** Bouwt de tekst voor het resultaatsscherm en toont hem. Wordt ook aangeroepen na marker-verplaatsing. */
    private fun updateResultsText() {
        val txt = txtResults ?: return
        val tallyMap = cachedTally
        val weather = cachedWeather

        // Toon in de pop-up enkel soorten met aantallen > 0
        val positiveTallies = tallyMap.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }

        // Huidige, “waarheidsgetrouwe” coördinaten: marker > ViewModel > NA
        val current = getCurrentLatLon()
        val latStr = current?.first?.let { formatLat(it) } ?: "NA"
        val lonStr = current?.second?.let { formatLon(it) } ?: "NA"

        // Plaatsnaam: actuele reverse-geocodeerde naam > weerbron-naam > "Onbekend"
        val placeName = currentPlaceName ?: weather?.locationName ?: "Onbekend"

        val message = buildString {
            append(" Tellingsoverzicht:\n\n")
            if (positiveTallies.isEmpty()) {
                append("Geen waarnemingen met aantal > 0\n")
            } else {
                positiveTallies.forEach { entry ->
                    append("${entry.key}: ${entry.value}\n")
                }
            }
            if (weather != null) {
                val wIcon = weatherIconFor(weather.weathercode)
                append("\n️ Weerbericht $wIcon\n\n")
                append(" 📍 Locatie: $placeName ($latStr, $lonStr)\n")
                append(" 🕒 Tijdstip: ${weather.time}\n")
                append(" 🌡️ Temp: ${"%.1f".format(weather.temperature)} °C\n")
                append(" 🌧️ Neerslag: ${weather.precipitation} mm\n")
                append(" 💨 Wind: ${weather.windspeed} km/u (${weatherManager.toBeaufort(weather.windspeed)} Bf), ${weatherManager.toCompass(weather.winddirection)}\n")
                append(" ☁️ Bewolking: ${weatherManager.toOctas(weather.cloudcover)}/8\n")
                append(" 👁️ Zicht: ${weather.visibility} m\n")
                append(" 📈 Luchtdruk: ${weather.pressure} hPa\n")
                append(" 🌦️ Weer: ${weatherManager.getWeatherDescription(weather.weathercode)}\n")
            }
        }
        txt.text = message
    }

    private fun initMap(lat: Double, lon: Double) {
        val mv = mapView ?: return
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", 0)
        )
        mv.setTileSource(TileSourceFactory.MAPNIK)
        mv.setMultiTouchControls(true)
        mv.controller.setZoom(16.0)
        val center = GeoPoint(lat, lon)
        mv.controller.setCenter(center)

        // (Her)plaats marker
        val m = Marker(mv).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "GPS locatie"
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(p0: Marker?) {}
                override fun onMarkerDragEnd(p0: Marker?) {
                    val gp = p0?.position ?: return
                    mv.controller.animateTo(gp)
                    updateAccuracyCircle(gp, null)

                    // Sla handmatig verplaatste positie direct op in ViewModel
                    sharedSpeciesViewModel.setGpsLocation(gp.latitude, gp.longitude)

                    // Update plaatsnaam via reverse geocoding + UI hertekenen
                    reverseGeocodeAndUpdate(gp.latitude, gp.longitude)
                }
                override fun onMarkerDragStart(p0: Marker?) {}
            })
        }
        marker = m
        mv.overlays.add(m)
        mv.invalidate()
    }

    /**
     * Vraag een precieze, recente locatie op en update marker/cirkel.
     * Guard tegen lifecycle: als dialog al gesloten is, doe niets.
     */
    private fun requestPreciseLocationUpdate() {
        // Cancel vorige call indien nog bezig
        locationCts?.cancel()
        val cts = CancellationTokenSource().also { locationCts = it }

        val fused = LocationServices.getFusedLocationProviderClient(requireActivity())
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                // Als de dialog weg is of map niet meer bestaat, stop.
                val mv = mapView
                if (dialog?.isShowing != true || !isAdded || mv == null || loc == null) return@addOnSuccessListener

                val gp = GeoPoint(loc.latitude, loc.longitude)
                mv.controller.animateTo(gp)
                marker?.position = gp

                val acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
                updateAccuracyCircle(gp, acc)

                // Bewaar ook in de gedeelde ViewModel voor export
                sharedSpeciesViewModel.setGpsLocation(loc.latitude, loc.longitude)

                // Update plaatsnaam via reverse geocoding + UI hertekenen
                reverseGeocodeAndUpdate(loc.latitude, loc.longitude)
            }
            .addOnFailureListener {
                // negeer; we blijven op vorige/fallback positie
            }
    }

    /**
     * Teken of update een eenvoudige accuracy circle rond de marker.
     * Veilig voor lifecycle: doet niets als mapView al is opgeruimd.
     */
    private fun updateAccuracyCircle(center: GeoPoint, radiusMeters: Double?) {
        val mv = mapView ?: return
        // Verwijder oude cirkel als die er nog was
        accuracyCircle?.let { old ->
            mv.overlays.remove(old)
        }
        accuracyCircle = null

        if (radiusMeters != null && radiusMeters > 0) {
            // Polygon vereist een geldige MapView; we hebben mv hierboven gecontroleerd.
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

    private fun saveAllFiles(
        tallyMap: Map<String, Int>,
        view: View?,
        weather: WeatherManager.FullWeather?,
        triggerShare: Boolean
    ) {
        // Gebruik de (eventueel handmatig gecorrigeerde) markerpositie als waarheid.
        val currentPos = marker?.position
        val lat = currentPos?.latitude ?: sharedSpeciesViewModel.gpsLocation.value?.first
        val lon = currentPos?.longitude ?: sharedSpeciesViewModel.gpsLocation.value?.second

        val timestamp = getTimestamp()
        val bitmap = takeScreenshotOfView(view)
        val screenshotUri = saveScreenshotBitmap(bitmap, timestamp)
        val uris = saveCsvAndTxt(tallyMap, lat, lon, weather, timestamp).toMutableList()
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

        // Actuele locatie-naam (reverse geocode) of weerbron-naam
        val place = currentPlaceName ?: weather?.locationName ?: "Onbekend"

        val csvContent = buildString {
            append("GPS;Latitude;${lat ?: "NA"};Longitude;${lon ?: "NA"}\n")
            append("Aanvang;$start;Einde;$end\n")
            if (weather != null) {
                val wIcon = weatherIconFor(weather.weathercode)
                append("Weer;Locatie;$place (${lat?.let { formatLat(it) } ?: "NA"}, ${lon?.let { formatLon(it) } ?: "NA"})\n")
                append("Tijd;${weather.time}\n")
                append("Temperatuur;${"%.1f".format(weather.temperature)} °C\n")
                append("Neerslag;${weather.precipitation} mm\n")
                append("Wind;${weather.windspeed} km/u;Beaufort;${weatherManager.toBeaufort(weather.windspeed)};Richting;${weatherManager.toCompass(weather.winddirection)}\n")
                append("Bewolking;${weatherManager.toOctas(weather.cloudcover)}/8\n")
                append("Zicht;${weather.visibility} m\n")
                append("Luchtdruk;${weather.pressure} hPa\n")
                append("Weer ($wIcon);${weatherManager.getWeatherDescription(weather.weathercode)}\n")
            }
            append("Soortnaam;Aantal\n")
            // Export: volledige lijst (zoals voorheen), niet gefilterd
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
                val wIcon = weatherIconFor(weather.weathercode)
                append("\n--- Weerbericht $wIcon ---\n")
                append("📍 Locatie: $place (${lat?.let { formatLat(it) } ?: "NA"}, ${lon?.let { formatLon(it) } ?: "NA"})\n")
                append("🕒 Tijd: ${weather.time}\n")
                append("🌡️ Temp: ${"%.1f".format(weather.temperature)} °C\n")
                append("🌧️ Neerslag: ${weather.precipitation} mm\n")
                append("💨 Wind: ${weather.windspeed} km/u (${weatherManager.toBeaufort(weather.windspeed)} Bf), ${weatherManager.toCompass(weather.winddirection)}\n")
                append("☁️ Bewolking: ${weatherManager.toOctas(weather.cloudcover)}/8\n")
                append("👁️ Zicht: ${weather.visibility} m\n")
                append("📈 Luchtdruk: ${weather.pressure} hPa\n")
                append("🌦️ Weer: ${weatherManager.getWeatherDescription(weather.weathercode)}\n")
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

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel eventuele lopende locatie-opvraag zodat callbacks niet meer in deze dialog landen
        locationCts?.cancel()
        locationCts = null

        // Opruimen overlays en referenties
        mapView?.overlays?.remove(marker)
        marker = null
        accuracyCircle = null
        mapView = null
        txtResults = null
    }

    /* ========================= Helpers voor coördinaten/plaatsnaam/icoon ========================= */

    /** Geeft de meest actuele coördinaten terug: marker-positie > ViewModel > null. */
    private fun getCurrentLatLon(): Pair<Double, Double>? {
        val m = marker?.position
        if (m != null) return m.latitude to m.longitude
        val vm = sharedSpeciesViewModel.gpsLocation.value
        if (vm != null) return vm.first to vm.second
        return null
    }

    /** Reverse geocode lat/lon naar plaatsnaam, update state en UI. */
    private fun reverseGeocodeAndUpdate(lat: Double, lon: Double) {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val geocoder = Geocoder(ctx, Locale.getDefault())
            geocoder.getFromLocation(lat, lon, 1) { addresses ->
                val name = addresses?.firstOrNull()?.let { adr ->
                    adr.locality ?: adr.subAdminArea ?: adr.adminArea ?: adr.countryName
                }
                currentPlaceName = name
                updateResultsText()
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val list = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)
                    val name = list?.firstOrNull()?.let { adr ->
                        adr.locality ?: adr.subAdminArea ?: adr.adminArea ?: adr.countryName
                    }
                    withContext(Dispatchers.Main) {
                        currentPlaceName = name
                        updateResultsText()
                    }
                } catch (_: Exception) {
                    // laat currentPlaceName ongewijzigd
                }
            }
        }
    }

    /** Formatteer latitude als 6-decimalen + N/S. */
    private fun formatLat(lat: Double): String {
        val hemi = if (lat >= 0) "N" else "S"
        return "${"%.8f".format(abs(lat))}°$hemi"
    }

    /** Formatteer longitude als 6-decimalen + E/W. */
    private fun formatLon(lon: Double): String {
        val hemi = if (lon >= 0) "E" else "W"
        return "${"%.8f".format(abs(lon))}°$hemi"
    }

    /** Zet open-meteo/weer-code om naar een passend icoon. */
    private fun weatherIconFor(code: Int): String = when (code) {
        0 -> "☀️"                     // helder
        1, 2 -> "🌤️"                  // overwegend zonnig
        3 -> "☁️"                     // bewolkt
        45, 48 -> "🌫️"               // mist
        in 51..57 -> "🌦️"             // motregen / ijzel-motregen
        in 61..67 -> "🌧️"             // regen / ijzel
        in 71..77 -> "🌨️"             // sneeuw
        in 80..82 -> "🌧️"             // buien
        95 -> "🌩️"                    // onweer
        96, 99 -> "⛈️"                // onweer met hagel
        else -> "🌦️"                  // fallback
    }
}
