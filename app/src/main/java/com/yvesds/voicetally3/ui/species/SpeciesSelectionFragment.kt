package com.yvesds.voicetally3.ui.species

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.databinding.FragmentSpeciesSelectionBinding
import com.yvesds.voicetally3.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally3.utils.UiHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.Lifecycle


@AndroidEntryPoint
class SpeciesSelectionFragment : Fragment(R.layout.fragment_species_selection) {

    private var _binding: FragmentSpeciesSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpeciesSelectionViewModel by viewModels()
    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()

    private lateinit var adapter: SpeciesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSpeciesSelectionBinding.bind(view)

        adapter = SpeciesAdapter { species, isChecked ->
            viewModel.toggleSpecies(species, isChecked)
        }

        val spanCount = calculateSpanCount()
        binding.recyclerViewSpecies.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerViewSpecies.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.speciesList.collectLatest { list ->
                        adapter.submitList(list)
                        UiHelper.showSnackbar(requireView(), "📄 ${list.size} soorten geladen")
                    }
                }
                launch {
                    sharedSpeciesViewModel.selectedSpecies.collectLatest { shared ->
                        viewModel.setSelected(shared)
                    }
                }
                launch {
                    viewModel.selectedSpecies.collectLatest { selectedSet ->
                        adapter.setSelectedSpecies(selectedSet)
                    }
                }
            }
        }

        binding.buttonConfirmSelection.setOnClickListener {
            val selected = viewModel.selectedSpecies.value
            sharedSpeciesViewModel.setSelectedSpecies(selected)

            // ✅ Starttijd registreren bij nieuwe selectie
            sharedSpeciesViewModel.setSessionStart(System.currentTimeMillis())

            UiHelper.showSnackbar(requireView(), "✅ Geselecteerd: ${selected.joinToString()}")
            findNavController().navigate(R.id.action_speciesSelectionFragment_to_tallyFragment)
        }

        viewModel.loadSpecies()
    }


    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredColumnWidthDp = 180
        val spanCount = (screenWidthDp / desiredColumnWidthDp).toInt()
        return if (spanCount >= 1) spanCount else 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
