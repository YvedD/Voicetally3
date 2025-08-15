package com.yvesds.voicetally3.ui.tally

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.databinding.FragmentTallyBinding
import com.yvesds.voicetally3.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally3.utils.LogEntry
import com.yvesds.voicetally3.utils.LogType
import com.yvesds.voicetally3.utils.SpeechLogAdapter
import com.yvesds.voicetally3.utils.SpeechParsingBuffer
import com.yvesds.voicetally3.utils.SpeechParsingUseCase
import com.yvesds.voicetally3.utils.SpeechRecognitionHelper
import com.yvesds.voicetally3.utils.SoundPlayer
import com.yvesds.voicetally3.data.SettingsKeys
import com.yvesds.voicetally3.data.SharedPrefsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class TallyFragment : Fragment(R.layout.fragment_tally) {

    private var _binding: FragmentTallyBinding? = null
    private val binding get() = _binding!!

    private val sharedSpeciesViewModel: SharedSpeciesViewModel by activityViewModels()

    private lateinit var tallyAdapter: TallyAdapter
    private lateinit var logAdapter: SpeechLogAdapter
    private lateinit var speechHelper: SpeechRecognitionHelper
    private lateinit var parsingBuffer: SpeechParsingBuffer

    @Inject lateinit var parsingUseCase: SpeechParsingUseCase
    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper
    @Inject lateinit var soundPlayer: SoundPlayer

    // Inject de CPU-bound dispatcher (aanbevolen i.p.v. hardcoded Dispatchers.Default)
    @Inject @Named("Default") lateinit var defaultDispatcher: CoroutineDispatcher

    /** Laatste FINAL die verwerkt werd, om duplicates te negeren. */
    private var lastFinalProcessed: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTallyBinding.bind(view)

        setupRecyclerViews()
        observeTallyMap()
        observeLogs()
        setupSpeechHelper()
        setupButtons()

        if (sharedSpeciesViewModel.sessionStart.value == null) {
            sharedSpeciesViewModel.setSessionStart(System.currentTimeMillis())
        }

        fetchAndStoreLocation()
        showLoggingDebugInfo()

        // Initieel vullen met reeds opgeslagen logs
        logAdapter.setLogs(sharedSpeciesViewModel.speechLogs.value)
    }

    private fun setupRecyclerViews() {
        tallyAdapter = TallyAdapter(
            onIncrement = { species -> sharedSpeciesViewModel.increment(species) },
            onDecrement = { species -> sharedSpeciesViewModel.decrement(species) },
            onReset = { species -> sharedSpeciesViewModel.reset(species) }
        )
        binding.recyclerViewTally.layoutManager = GridLayoutManager(requireContext(), calculateSpanCount())
        binding.recyclerViewTally.adapter = tallyAdapter

        logAdapter = SpeechLogAdapter()
        binding.recyclerViewSpeechLog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSpeechLog.adapter = logAdapter
    }

    private fun observeTallyMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedSpeciesViewModel.tallyMap.collectLatest { map ->
                val items = map.entries.sortedBy { it.key }
                tallyAdapter.submitList(items)
            }
        }
    }

    private fun observeLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedSpeciesViewModel.speechLogs.collectLatest { logs ->
                logAdapter.setLogs(logs)
                binding.recyclerViewSpeechLog.scrollToPosition(0)
            }
        }
    }

    private fun setupSpeechHelper() {
        parsingBuffer = SpeechParsingBuffer()
        speechHelper = SpeechRecognitionHelper(
            context = requireContext(),
            onFinalResult = { spokenText ->
                val normalized = spokenText.lowercase().trim()

                if (normalized == lastFinalProcessed) {
                    val showWarnings = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_WARNINGS, true)
                    addLogLine(
                        LogEntry(
                            text = "⚠️ Final duplicate ignored",
                            showInUi = showWarnings,
                            includeInExport = true,
                            type = LogType.WARNING
                        )
                    )
                    return@SpeechRecognitionHelper
                }
                lastFinalProcessed = normalized

                val showFinals = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_FINALS, true)
                addLogLine(
                    LogEntry(
                        text = "✅ Final: $normalized",
                        showInUi = showFinals,
                        includeInExport = true,
                        type = LogType.FINAL
                    )
                )

                parseAndUpdateMultiple(normalized)
            },
            onPartialResult = { partial ->
                parsingBuffer.updatePartial(partial)
                val showPartials = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARTIALS, true)
                addLogLine(
                    LogEntry(
                        text = "  Partial: ${partial.lowercase()}",
                        showInUi = showPartials,
                        includeInExport = true,
                        type = LogType.PARTIAL
                    )
                )
            },
            onError = { error ->
                val showErrors = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_ERRORS, true)
                addLogLine(
                    LogEntry(
                        text = "❌ Fout: $error",
                        showInUi = showErrors,
                        includeInExport = true,
                        type = LogType.ERROR
                    )
                )
            }
        )
    }

    private fun setupButtons() {
        binding.buttonEndSession.setOnClickListener {
            ResultsDialogFragment().show(parentFragmentManager, "ResultsDialog")
        }
        binding.buttonAddSpecies.setOnClickListener {
            findNavController().navigate(R.id.action_tallyFragment_to_speciesSelectionFragment)
        }
    }

    private fun fetchAndStoreLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    sharedSpeciesViewModel.setGpsLocation(it.latitude, it.longitude)
                }
            }
    }

    /** Voeg log toe via ViewModel én update RecyclerView — ALTIJD op main thread aanroepen. */
    private fun addLogLine(entry: LogEntry) {
        // We zorgen ervoor dat dit altijd op main wordt aangeroepen.
        sharedSpeciesViewModel.addSpeechLog(entry)
        if (entry.showInUi || entry.type == LogType.TALLY_UPDATE || entry.type == LogType.WARNING) {
            logAdapter.addLine(entry)
            binding.recyclerViewSpeechLog.scrollToPosition(0)
        }
    }

    private fun addLogLine(
        text: String,
        showInUi: Boolean = true,
        type: LogType = LogType.INFO
    ) {
        addLogLine(
            LogEntry(
                text = text,
                showInUi = showInUi,
                includeInExport = true,
                type = type
            )
        )
    }

    private fun showLoggingDebugInfo() {
        val languageCode = sharedPrefsHelper.getString(SettingsKeys.SPEECH_INPUT_LANGUAGE, "nl-NL") ?: "nl-NL"
        val logPartials = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARTIALS, true)
        val logFinals = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_FINALS, true)
        val logParsedBlocks = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARSED_BLOCKS, true)
        val logWarnings = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_WARNINGS, true)
        val logErrors = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_ERRORS, true)
        val logInfo = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_INFO, true)
        val enableExtraSounds = sharedPrefsHelper.getBoolean(SettingsKeys.ENABLE_EXTRA_SOUNDS, true)

        val debugInfo = """
            [DEBUG Logging Settings]
            ➜ Language: $languageCode
            ➜ Partials: $logPartials
            ➜ Finals: $logFinals
            ➜ Parsed Blocks: $logParsedBlocks
            ➜ Warnings: $logWarnings
            ➜ Errors: $logErrors
            ➜ Info: $logInfo
            ➜ Extra Sounds: $enableExtraSounds
        """.trimIndent()

        addLogLine(debugInfo, showInUi = logInfo, type = LogType.INFO)
    }

    fun saveAndShowResults() {
        ResultsDialogFragment().show(parentFragmentManager, "ResultsDialog")
    }

    /** Verwerk meerdere (soort, aantal)-paren in één final uiting. */
    private fun parseAndUpdateMultiple(spokenText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1) Bouw alias-map (merge actief + fallback)
            val aliasMap: Map<String, String> = buildMap {
                putAll(sharedSpeciesViewModel.actieveAliasMap.value)
                putAll(sharedSpeciesViewModel.fallbackAliasMap.value)
            }

            // 2) Parse in background (CPU-bound)
            val results = withContext(defaultDispatcher) {
                parsingUseCase.executeAll(
                    transcript = spokenText,
                    aliasToSpeciesMap = aliasMap
                )
            }

            // 3) Bereid UI-gegevens voor in background (geen UI calls!)
            data class Prepared(
                val parsedBlockLines: List<LogEntry>,
                val logLines: List<LogEntry>,
                val validUpdates: List<Pair<String, Int>>,
                val pendingAdditions: List<Pair<String, Int>>
            )

            val prepared = withContext(defaultDispatcher) {
                val selected = sharedSpeciesViewModel.selectedSpecies.value
                val allKnownSpecies = sharedSpeciesViewModel.speciesList
                val showParsedBlocks = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARSED_BLOCKS, true)
                val showWarnings = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_WARNINGS, true)
                val showErrors = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_ERRORS, true)

                if (results.isEmpty()) {
                    val err = LogEntry(
                        text = "❌ Geen parse-resultaat",
                        showInUi = showErrors,
                        includeInExport = true,
                        type = LogType.ERROR
                    )
                    Prepared(
                        parsedBlockLines = emptyList(),
                        logLines = listOf(err),
                        validUpdates = emptyList(),
                        pendingAdditions = emptyList()
                    )
                } else {
                    val parsedBlockLines: List<LogEntry> =
                        if (showParsedBlocks) {
                            results.map { r ->
                                LogEntry(
                                    text = "  Gevonden: ${r.species} → ${r.count}",
                                    showInUi = true,
                                    includeInExport = true,
                                    type = LogType.PARSED_BLOCK
                                )
                            }
                        } else emptyList()

                    val logLines = mutableListOf<LogEntry>()
                    val validUpdates = mutableListOf<Pair<String, Int>>()
                    val pendingAdditions = mutableListOf<Pair<String, Int>>()

                    results.forEach { r ->
                        val species = r.species.lowercase()
                        val amount = r.count
                        val nameFormatted = species.replaceFirstChar { it.uppercase() }

                        when {
                            selected.contains(species) -> {
                                logLines.add(LogEntry("✅ $nameFormatted ➜ +$amount", type = LogType.TALLY_UPDATE))
                                validUpdates.add(species to amount)
                            }
                            allKnownSpecies.contains(species) -> {
                                logLines.add(
                                    LogEntry(
                                        text = "⚠️ $nameFormatted is niet geselecteerd",
                                        showInUi = showWarnings,
                                        includeInExport = true,
                                        type = LogType.WARNING
                                    )
                                )
                                pendingAdditions.add(species to amount)
                            }
                            else -> {
                                logLines.add(
                                    LogEntry(
                                        text = "❌ $nameFormatted niet herkend",
                                        showInUi = showErrors,
                                        includeInExport = true,
                                        type = LogType.ERROR
                                    )
                                )
                            }
                        }
                    }

                    Prepared(
                        parsedBlockLines = parsedBlockLines,
                        logLines = logLines,
                        validUpdates = validUpdates,
                        pendingAdditions = pendingAdditions
                    )
                }
            }

            // 4) UITSLUITEND OP MAIN: logs toevoegen, tallies bijwerken, geluiden, dialogen
            prepared.parsedBlockLines.forEach { addLogLine(it) }
            prepared.logLines.forEach { addLogLine(it) }

            if (prepared.validUpdates.isNotEmpty()) {
                sharedSpeciesViewModel.updateTallies(prepared.validUpdates)
                soundPlayer.play("success")
            }

            prepared.pendingAdditions.forEach { (species, amount) ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Soort niet geselecteerd")
                    .setMessage("Wil je '${species.replaceFirstChar { it.uppercase() }}' toevoegen aan de tellingen?")
                    .setPositiveButton("Toevoegen") { _, _ ->
                        sharedSpeciesViewModel.addSpeciesToSelection(species, amount)
                        soundPlayer.play("success")
                    }
                    .setNegativeButton("Annuleren") { _, _ ->
                        soundPlayer.play("error")
                    }
                    .show()
            }

            if (prepared.validUpdates.isEmpty() && prepared.pendingAdditions.isEmpty()) {
                soundPlayer.play("error")
            }
        }
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val desiredColumnWidthDp = 300
        return (screenWidthDp / desiredColumnWidthDp).toInt().coerceIn(1, 3)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        speechHelper.stopListening()
        speechHelper.destroy()
    }

    fun triggerSpeechRecognition() {
        speechHelper.startListening()
    }
}
