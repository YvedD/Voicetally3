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

    private val sharedVM: SharedSpeciesViewModel by activityViewModels()
    private lateinit var adapter: SpeechLogAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Full width
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = DialogSpeechListeningBinding.inflate(layoutInflater)
        dialog?.setContentView(binding.root)

        adapter = SpeechLogAdapter()
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLogs.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            sharedVM.speechLogs.collectLatest { logs ->
                adapter.setLogs(logs)
                binding.recyclerViewLogs.scrollToPosition(0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
