package com.yvesds.voicetally3.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.*
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.CSVManager
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.databinding.FragmentMainBinding
import com.yvesds.voicetally3.utils.UiHelper
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment(R.layout.fragment_main) {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var csvManager: CSVManager

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sharedPrefsHelper.setString(KEY_SAF_URI, uri.toString())
            updateSafUi(true)
            UiHelper.showSnackbar(requireView(), "✅ Rootmap ingesteld!")
        } else {
            UiHelper.showSnackbar(requireView(), "❌ Geen map gekozen.")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchAndStorePreciseLocation()
            UiHelper.showSnackbar(requireView(), "✅ Locatie permissie verleend")
        } else {
            UiHelper.showSnackbar(requireView(), "❌ Locatie permissie geweigerd")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainBinding.bind(view)

/*
        // ✅ osmdroid configuratie
        Configuration.getInstance().load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
*/

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermissionAndFetch()

        // ✅ SAF status
        val hasSaf = sharedPrefsHelper.getString(KEY_SAF_URI) != null
        updateSafUi(hasSaf)

        /*// ✅ GPS permissie
        val gpsGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        */

        // ✅ Soorten selecteren
        binding.buttonOpenSpeciesSelection.setOnClickListener {
            val safUri = sharedPrefsHelper.getString(KEY_SAF_URI)
            if (safUri.isNullOrEmpty()) {
                UiHelper.showSnackbar(requireView(), "❌ SAF rootmap niet ingesteld!")
            } else {
                findNavController().navigate(R.id.action_mainFragment_to_speciesSelectionFragment)
            }
        }

        // ✅ Training starten
        binding.buttonTrainSpecies.setOnClickListener {
            findNavController().navigate(R.id.aliasEditorFragment)
        }

    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fetchAndStorePreciseLocation()
        }
    }

    private fun fetchAndStorePreciseLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lon = location?.longitude ?: 0.0
                sharedPrefsHelper.setDouble("gps_latitude", lat)
                sharedPrefsHelper.setDouble("gps_longitude", lon)

/*
                if (_binding != null) { // 🔒 check dat de view nog bestaat
                    val geoPoint = GeoPoint(lat, lon)
                    val map = binding.mapView
                    map.controller.setZoom(18.0) // hogere zoom voor detail
                    map.controller.setCenter(geoPoint)

                    val marker = Marker(map)
                    marker.position = geoPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Huidige locatie"
                    map.overlays.add(marker)

                } else {
                    Log.w("MainFragment", "⚠️ Binding is null, skip map update.")
                }
*/
            }
            .addOnFailureListener {
                if (_binding != null) {
                    UiHelper.showSnackbar(requireView(), "❌ Nauwkeurige locatie niet beschikbaar, probeer buiten")
                }
            }
    }


    private fun updateSafUi(hasSaf: Boolean) {
        binding.textSafStatus.text = if (hasSaf) "SAF Status : OK!" else "SAF Status : NOG NIET IN ORDE"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SAF_URI = "saf_uri"
        private const val KEY_MIC_PERMISSION = "mic_permission"
    }
}
