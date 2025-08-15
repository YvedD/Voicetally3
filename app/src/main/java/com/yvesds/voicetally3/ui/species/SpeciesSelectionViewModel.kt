package com.yvesds.voicetally3.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally3.data.SpeciesCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/**
 * VM voor het selectiescherm: beheert de volledige lijst en de huidige selectie.
 * - Laadt soortenlijst **off-main** via SpeciesCacheManager (suspend).
 */
@HiltViewModel
class SpeciesSelectionViewModel @Inject constructor(
    private val speciesCacheManager: SpeciesCacheManager,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _speciesList = MutableStateFlow<List<String>>(emptyList())
    val speciesList: StateFlow<List<String>> get() = _speciesList

    private val _selectedSpecies = MutableStateFlow<Set<String>>(emptySet())
    val selectedSpecies: StateFlow<Set<String>> get() = _selectedSpecies

    fun loadSpecies() {
        viewModelScope.launch {
            val list = withContext(ioDispatcher) { speciesCacheManager.getSpeciesListSuspend() }
            _speciesList.value = list
        }
    }

    fun toggleSpecies(species: String, isChecked: Boolean) {
        val current = _selectedSpecies.value.toMutableSet()
        if (isChecked) current.add(species) else current.remove(species)
        _selectedSpecies.value = current
    }

    fun setSelected(species: Set<String>) {
        _selectedSpecies.value = species
    }
}
