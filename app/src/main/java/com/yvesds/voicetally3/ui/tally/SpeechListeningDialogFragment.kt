package com.yvesds.voicetally3.ui.tally

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.voicetally3.databinding.DialogSpeechListeningBinding
import com.yvesds.voicetally3.utils.LogEntry
import com.yvesds.voicetally3.utils.SpeechLogAdapter

class SpeechListeningDialogFragment : DialogFragment() {

    private var _binding: DialogSpeechListeningBinding? = null
    private val binding get() = _binding!!
    private lateinit var logAdapter: SpeechLogAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        _binding = DialogSpeechListeningBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        setupRecyclerView()

        // ✅ Voorbeeld LogEntry lijst
        val myLogEntries = listOf(
            LogEntry("log1"),
            LogEntry("log2"),
            LogEntry("log3")
        )

        logAdapter.setLogs(myLogEntries)

        return dialog
    }

    private fun setupRecyclerView() {
        logAdapter = SpeechLogAdapter()
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLogs.adapter = logAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
