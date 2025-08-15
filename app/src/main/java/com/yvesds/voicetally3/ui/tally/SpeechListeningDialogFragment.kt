package com.yvesds.voicetally3.ui.tally

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.voicetally3.databinding.DialogSpeechListeningBinding
import com.yvesds.voicetally3.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally3.utils.SpeechLogAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Toont live de spraaklogs in een lijst terwijl de herkenning bezig is.
 *
 * Verbeteringen:
 * - Lifecycle-aware verzamelen van logs.
 * - Auto-scroll naar boven bij nieuwe regels.
 * - Stabiele RecyclerView-config.
 */
class SpeechListeningDialogFragment : DialogFragment() {

    private var _binding: DialogSpeechListeningBinding? = null
    private val binding get() = _binding!!

    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()
    private lateinit var logAdapter: SpeechLogAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        _binding = DialogSpeechListeningBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupRecyclerView()
        observeLogs()

        return dialog
    }

    private fun setupRecyclerView() {
        logAdapter = SpeechLogAdapter()
        binding.recyclerViewLogs.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
        }
    }

    private fun observeLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedSpeciesViewModel.speechLogs.collectLatest { logs ->
                logAdapter.setLogs(logs)
                binding.recyclerViewLogs.scrollToPosition(0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
