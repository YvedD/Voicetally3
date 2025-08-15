package com.yvesds.voicetally3.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.CSVManager
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.databinding.FragmentMainBinding
import com.yvesds.voicetally3.utils.UiHelper
import dagger.hilt.android.AndroidEntryPoint
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
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        checkLocationPermissionAndFetch()

        // ✅ SAF status controleren
        val hasSaf = sharedPrefsHelper.getString(KEY_SAF_URI) != null
        updateSafUi(hasSaf)

        if (!hasSaf) {
            // Geen aparte knop in de layout? Bied een snackbar-actie aan om de map te kiezen.
            Snackbar.make(requireView(), "SAF rootmap nog niet ingesteld", Snackbar.LENGTH_LONG)
                .setAction("Kies map") { safLauncher.launch(null) }
                .show()
        }

        // ✅ Soorten selecteren
        binding.buttonOpenSpeciesSelection.setOnClickListener {
            val safUri = sharedPrefsHelper.getString(KEY_SAF_URI)
            if (safUri.isNullOrEmpty()) {
                Snackbar.make(requireView(), "❌ SAF rootmap niet ingesteld!", Snackbar.LENGTH_LONG)
                    .setAction("Kies map") { safLauncher.launch(null) }
                    .show()
            } else {
                findNavController().navigate(R.id.action_mainFragment_to_speciesSelectionFragment)
            }
        }

        // ✅ Training starten (alias editor)
        binding.buttonTrainSpecies.setOnClickListener {
            findNavController().navigate(R.id.aliasEditorFragment)
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
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
            }
            .addOnFailureListener {
                if (_binding != null) {
                    UiHelper.showSnackbar(
                        requireView(),
                        "❌ Nauwkeurige locatie niet beschikbaar, probeer buiten"
                    )
                }
            }
    }

    private fun updateSafUi(hasSaf: Boolean) {
        binding.textSafStatus.text =
            if (hasSaf) "SAF Status : OK!" else "SAF Status : NOG NIET IN ORDE"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SAF_URI = "saf_uri"
        @Suppress("unused")
        private const val KEY_MIC_PERMISSION = "mic_permission"
    }
}
