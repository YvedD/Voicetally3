package com.yvesds.voicetally3.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.CSVManager
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.databinding.FragmentSetupBinding
import com.yvesds.voicetally3.utils.UiHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ✅ SetupFragment: regelt éénmalige rootmap keuze en persistente SAF-permissie.
 */
@AndroidEntryPoint
class SetupFragment : Fragment(R.layout.fragment_setup) {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var csvManager: CSVManager

    private val requestRootFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sharedPrefsHelper.setString(KEY_SAF_URI, uri.toString())

            // ⚙️ Structuur opbouwen off-main
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { csvManager.ensureInitialStructure() }
                UiHelper.showSnackbar(
                    requireView(),
                    if (result) "✅ Structuur OK!" else "⚠️ Structuur niet aangemaakt!"
                )
                sharedPrefsHelper.setBoolean(KEY_SETUP_DONE, true)
                findNavController().navigate(R.id.action_setupFragment_to_mainFragment)
            }
        } else {
            UiHelper.showSnackbar(requireView(), "❌ Geen map geselecteerd.")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSetupBinding.bind(view)

        // ⛔️ Reeds ingesteld? Dan skip deze setup.
        val isSetupDone = sharedPrefsHelper.getBoolean(KEY_SETUP_DONE)
        if (isSetupDone) {
            findNavController().navigate(R.id.action_setupFragment_to_mainFragment)
            return
        }

        binding.buttonChooseFolder.setOnClickListener { requestRootFolder.launch(null) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SAF_URI = "saf_uri"
        private const val KEY_SETUP_DONE = "setup_done"
    }
}
