package com.yvesds.voicetally3.utils.aliasses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.databinding.FragmentAliasEditorBinding
import com.yvesds.voicetally3.utils.UiHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AliasEditorFragment : Fragment() {

    private var _binding: FragmentAliasEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AliasEditorViewModel by viewModels()
    private lateinit var adapter: SimpleSpeciesListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAliasEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Adapter setup
        adapter = SimpleSpeciesListAdapter(emptyList()) { clickedSpecies ->
            val bundle = bundleOf("speciesName" to clickedSpecies)
            findNavController().navigate(
                R.id.action_aliasEditorFragment_to_speciesAliasEditorFragment,
                bundle
            )
        }

        binding.recyclerViewAliasEditor.apply {
            layoutManager = GridLayoutManager(requireContext(), getSpanCount())
            adapter = this@AliasEditorFragment.adapter
        }

        // Observers
        viewModel.speciesList.observe(viewLifecycleOwner) { list ->
            adapter.updateData(list)
        }

        // ⏫ Laad soorten
        viewModel.loadSpeciesNames()

        // ➕ Soort toevoegen
        binding.buttonAddSpecies.setOnClickListener {
            UiHelper.showInputDialog(
                context = requireContext(),
                title = "Nieuwe soort toevoegen",
                hint = "Soortnaam (bv. Aalscholver)"
            ) { input ->
                if (!input.isNullOrBlank()) {
                    viewModel.addNewSpecies(input) { success ->
                        requireActivity().runOnUiThread {
                            if (success) {
                                Toast.makeText(
                                    requireContext(),
                                    "✅ '$input' toegevoegd",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "⚠️ '$input' bestaat al",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSpanCount(): Int {
        val dm = resources.displayMetrics
        val dpWidth = dm.widthPixels / dm.density
        return if (dpWidth >= 600) 2 else 1 // 2 kolommen voor tablets
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
