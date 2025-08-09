package com.yvesds.voicetally3.utils.aliasses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yvesds.voicetally3.databinding.FragmentSpeciesAliasEditorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SpeciesAliasEditorFragment : Fragment() {

    private lateinit var binding: FragmentSpeciesAliasEditorBinding
    private val viewModel: SpeciesAliasEditorViewModel by viewModels()
    private var adapter: AliasFieldAdapter? = null
    private var speciesName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSpeciesAliasEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speciesName = arguments?.getString("speciesName")
        if (speciesName == null) {
            Toast.makeText(requireContext(), "⚠️ Geen soort opgegeven", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        binding.textSpeciesName.text = "Aliassen voor: $speciesName"

        // 🧱 Grid layout: 1 kolom op telefoon, 2 op tablet
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 2 else 1
        binding.recyclerViewAliases.layoutManager = GridLayoutManager(requireContext(), spanCount)

        // 🔁 Laad data
        viewModel.loadAliases(speciesName!!)
        viewModel.aliases.observe(viewLifecycleOwner) { aliases ->
            val paddedList = aliases.toMutableList()
            while (paddedList.size < 20) paddedList.add("")
            adapter = AliasFieldAdapter(paddedList)
            binding.recyclerViewAliases.adapter = adapter
        }

        // 💾 Opslaan
        binding.buttonSave.setOnClickListener {
            val updated = adapter?.getUpdatedAliases()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            viewModel.saveAliases(speciesName!!, updated)
            Toast.makeText(requireContext(), "✅ Aliassen opgeslagen", Toast.LENGTH_SHORT).show()
        }
    }
}
