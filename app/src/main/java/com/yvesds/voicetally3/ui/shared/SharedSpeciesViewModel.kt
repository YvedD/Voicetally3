package com.yvesds.voicetally3.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally3.data.AliasRepository
import com.yvesds.voicetally3.utils.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/**
 * Gedeelde VM tussen meerdere schermen.
 * - Beheert geselecteerde soorten, tally-map, sessie-info, GPS, speech-logs.
 * - Bouwt 2 alias-maps:
 *   1) actieveAliasMap = alias -> soort (enkel voor geselecteerde soorten)
 *   2) fallbackAliasMap = alias -> soort (alle soorten, voor herkenning buiten selectie)
 *
 * Let op:
 * - Alle I/O (CSV/alias-laden) gebeurt via AliasRepository **suspend** functies op IO.
 * - UI-updates gebeuren op main via StateFlow.
 */
@HiltViewModel
class SharedSpeciesViewModel @Inject constructor(
    private val aliasRepository: AliasRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    // ==== UI state ====
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

    // Alias-maps
    private val _actieveAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val actieveAliasMap: StateFlow<Map<String, String>> get() = _actieveAliasMap

    private val _fallbackAliasMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val fallbackAliasMap: StateFlow<Map<String, String>> get() = _fallbackAliasMap

    /**
     * Volledige soortenlijst (read-only snapshot).
     * Wordt eenmalig lazy geladen via suspend call.
     */
    @Volatile
    var speciesList: List<String> = emptyList()
        private set

    init {
        // Laad fallback aliasmap + speciesList in background
        preloadSpeciesAndFallbackAliasMap()
    }

    private fun preloadSpeciesAndFallbackAliasMap() {
        viewModelScope.launch {
            // Laad alle soorten (IO)
            val allSpecies = withContext(ioDispatcher) { aliasRepository.getAllSpeciesSuspend() }
            speciesList = allSpecies

            // Bouw fallback aliasmap (IO)
            val map = withContext(ioDispatcher) { aliasRepository.buildFallbackAliasToSpeciesMapSuspend() }
            _fallbackAliasMap.value = map
        }
    }

    fun setGpsLocation(lat: Double, lon: Double) {
        _gpsLocation.value = lat to lon
    }

    fun setSessionStart(time: Long) {
        _sessionStart.value = time
    }

    /**
     * Vervang de selectie; tally’s worden behouden waar mogelijk.
     * Bouwt tegelijk de actieve aliasmap (IO).
     */
    fun setSelectedSpecies(species: Set<String>) {
        // Behoud bestaande teller-waarden waar mogelijk
        val normalizedTally = _tallyMap.value.mapKeys { it.key.lowercase() }
        val updated = species.associateWith { normalizedTally[it.lowercase()] ?: 0 }

        _selectedSpecies.value = species
        _tallyMap.value = updated

        // Actieve aliasmap voor enkel de geselecteerde soorten
        viewModelScope.launch(ioDispatcher) {
            val aliasMap = aliasRepository.buildAliasToSpeciesMapSuspend(species)
            _actieveAliasMap.value = aliasMap
        }
    }

    /**
     * Voeg soort toe aan selectie en voer een bijkomende increment uit.
     * Bouwt de actieve aliasmap opnieuw op.
     */
    fun addSpeciesToSelection(species: String, amount: Int) {
        val current = _selectedSpecies.value.toMutableSet()
        val added = current.add(species)
        if (added) {
            _selectedSpecies.value = current
            // Herbouw aliasmap op IO
            viewModelScope.launch(ioDispatcher) {
                val aliasMap = aliasRepository.buildAliasToSpeciesMapSuspend(current)
                _actieveAliasMap.value = aliasMap
            }
        }
        // Tally bijwerken
        val newTally = _tallyMap.value.toMutableMap()
        newTally[species] = (newTally[species] ?: 0) + amount
        _tallyMap.value = newTally
    }

    fun increment(species: String) = updateTally(species) { it + 1 }

    fun decrement(species: String) = updateTally(species) { (it - 1).coerceAtLeast(0) }

    fun reset(species: String) = updateTally(species) { 0 }

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

    private fun updateTally(species: String, op: (Int) -> Int) {
        val current = _tallyMap.value.toMutableMap()
        current[species] = op(current[species] ?: 0)
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

    /**
     * Vind soort bij alias; doorzoekt eerst actieve aliasmap, dan fallback.
     */
    fun findSpeciesForAlias(aliasInput: String): String? {
        val normalized = aliasInput.trim().lowercase()
        return _actieveAliasMap.value[normalized] ?: _fallbackAliasMap.value[normalized]
    }
}
