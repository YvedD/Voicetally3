package com.yvesds.voicetally3.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally3.data.AliasRepository
import com.yvesds.voicetally3.utils.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedSpeciesViewModel @Inject constructor(
    private val aliasRepository: AliasRepository
) : ViewModel() {

    private val _selectedSpecies = MutableStateFlow<Set<String>>(emptySet())
    val selectedSpecies: StateFlow<Set<String>> get() = _selectedSpecies

    private val _tallyMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tallyMap: StateFlow<Map<String, Int>> get() = _tallyMap

    private val _sessionStart = MutableStateFlow<Long?>(null)
    val sessionStart: StateFlow<Long?> get() = _sessionStart

    private val _gpsLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val gpsLocation: StateFlow<Pair<Double, Double>?> get() = _gpsLocation

    private val _speechLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val speechLogs: StateFlow<List<LogEntry>> get() = _speechLogs

    private val _actieveAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val actieveAliasMap: StateFlow<Map<String, String>> get() = _actieveAliasMap

    private val _fallbackAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val fallbackAliasMap: StateFlow<Map<String, String>> get() = _fallbackAliasMap

    val speciesList: List<String> = aliasRepository.getAllSpecies()

    init {
        preloadFallbackAliasMap()
    }

    private fun preloadFallbackAliasMap() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = aliasRepository.buildFallbackAliasToSpeciesMap()
            _fallbackAliasMap.value = map
        }
    }

    fun setGpsLocation(lat: Double, lon: Double) {
        _gpsLocation.value = lat to lon
    }

    fun setSessionStart(time: Long) {
        _sessionStart.value = time
    }

    fun setSelectedSpecies(species: Set<String>) {
        val normalizedTally = _tallyMap.value.mapKeys { it.key.lowercase() }
        val updated = species.associateWith { normalizedTally[it.lowercase()] ?: 0 }
        _selectedSpecies.value = species
        _tallyMap.value = updated

        viewModelScope.launch(Dispatchers.IO) {
            val aliasMap = aliasRepository.buildAliasToSpeciesMap(species)
            _actieveAliasMap.value = aliasMap
        }
    }

    fun addSpeciesToSelection(species: String, amount: Int) {
        val current = _selectedSpecies.value.toMutableSet()
        if (current.add(species)) {
            _selectedSpecies.value = current

            viewModelScope.launch(Dispatchers.IO) {
                val aliasMap = aliasRepository.buildAliasToSpeciesMap(current)
                _actieveAliasMap.value = aliasMap
            }
        }

        val newTally = _tallyMap.value.toMutableMap()
        newTally[species] = (newTally[species] ?: 0) + amount
        _tallyMap.value = newTally
    }

    fun increment(species: String) {
        updateTally(species) { it + 1 }
    }

    fun decrement(species: String) {
        updateTally(species) { (it - 1).coerceAtLeast(0) }
    }

    fun reset(species: String) {
        updateTally(species) { 0 }
    }

    fun resetAll() {
        _tallyMap.value = _tallyMap.value.mapValues { 0 }
        _sessionStart.value = System.currentTimeMillis()
        _speechLogs.value = emptyList()
    }

    fun updateTallies(updates: List<Pair<String, Int>>) {
        val current = _tallyMap.value.toMutableMap()
        updates.forEach { (species, amount) ->
            val prev = current[species] ?: 0
            current[species] = prev + amount
        }
        _tallyMap.value = current
    }

    private fun updateTally(species: String, operation: (Int) -> Int) {
        val current = _tallyMap.value.toMutableMap()
        current[species] = operation(current[species] ?: 0)
        _tallyMap.value = current
    }

    fun addSpeechLog(entry: LogEntry) {
        if (entry.showInUi) {
            _speechLogs.value = _speechLogs.value + entry
        }
    }


    fun exportAllSpeechLogs(): String {
        return _speechLogs.value
            .reversed()
            .joinToString("\n") { it.text }
    }


    fun findSpeciesForAlias(aliasInput: String): String? {
        val normalizedAlias = aliasInput.trim().lowercase()
        return _actieveAliasMap.value[normalizedAlias]
            ?: _fallbackAliasMap.value[normalizedAlias]
    }
}
