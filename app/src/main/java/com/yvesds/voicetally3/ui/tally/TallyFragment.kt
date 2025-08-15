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
import androidx.recyclerview.widget.SimpleItemAnimator
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

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
    @Inject @Named("Default") lateinit var defaultDispatcher: CoroutineDispatcher

    private var lastFinalProcessed: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTallyBinding.bind(view)

        setupRecyclerViews()
        observeTallyMap()
        observeLogs()
        setupSpeechHelper()
        setupButtons()
        applyWindowInsetsWorkaround()

        if (sharedSpeciesViewModel.sessionStart.value == null) {
            sharedSpeciesViewModel.setSessionStart(System.currentTimeMillis())
        }

        fetchAndStoreLocation()
        showLoggingDebugInfo()
        logAdapter.setLogs(sharedSpeciesViewModel.speechLogs.value)
    }

    private fun applyWindowInsetsWorkaround() {
        val rv = binding.recyclerViewTally
        val originalBottom = rv.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = originalBottom + sys.bottom)
            insets
        }
        rv.requestApplyInsets()
    }

    private fun setupRecyclerViews() {
        tallyAdapter = TallyAdapter(
            onIncrement = { species -> sharedSpeciesViewModel.increment(species) },
            onDecrement = { species -> sharedSpeciesViewModel.decrement(species) },
            onReset = { species -> sharedSpeciesViewModel.reset(species) }
        )

        val grid = GridLayoutManager(requireContext(), calculateSpanCount())
        grid.setRecycleChildrenOnDetach(true)

        binding.recyclerViewTally.layoutManager = grid
        binding.recyclerViewTally.adapter = tallyAdapter

        // 🚀 Responsieve updates:
        binding.recyclerViewTally.setHasFixedSize(true)
        (binding.recyclerViewTally.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recyclerViewTally.itemAnimator?.apply {
            changeDuration = 0
            moveDuration = 0
            addDuration = 0
            removeDuration = 0
        }
        // Cache iets verhogen, minder rebinds bij snel tikken
        binding.recyclerViewTally.setItemViewCacheSize(32)
        binding.recyclerViewTally.recycledViewPool.setMaxRecycledViews(0, 64)

        logAdapter = SpeechLogAdapter()
        binding.recyclerViewSpeechLog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSpeechLog.adapter = logAdapter
    }

    private fun observeTallyMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedSpeciesViewModel.tallyMap.collectLatest { map ->
                val items: List<Map.Entry<String, Int>> =
                    map.entries.sortedBy { it.key }.toList()
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
                            "⚠️ Final duplicate ignored",
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
                        "✅ Final: $normalized",
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
                        "  Partial: ${partial.lowercase()}",
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
                        "❌ Fout: $error",
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { sharedSpeciesViewModel.setGpsLocation(it.latitude, it.longitude) }
        }
    }

    private fun addLogLine(entry: LogEntry) {
        sharedSpeciesViewModel.addSpeechLog(entry)
        if (entry.showInUi || entry.type == LogType.TALLY_UPDATE || entry.type == LogType.WARNING) {
            logAdapter.addLine(entry)
            binding.recyclerViewSpeechLog.scrollToPosition(0)
        }
    }

    private fun addLogLine(text: String, showInUi: Boolean = true, type: LogType = LogType.INFO) {
        addLogLine(LogEntry(text = text, showInUi = showInUi, includeInExport = true, type = type))
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

    private fun parseAndUpdateMultiple(spokenText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val aliasMap: Map<String, String> = buildMap {
                putAll(sharedSpeciesViewModel.actieveAliasMap.value)
                putAll(sharedSpeciesViewModel.fallbackAliasMap.value)
            }
            val results = withContext(defaultDispatcher) {
                parsingUseCase.executeAll(
                    transcript = spokenText,
                    aliasToSpeciesMap = aliasMap
                )
            }

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
                    val err = LogEntry("❌ Geen parse-resultaat", showInUi = showErrors, includeInExport = true, type = LogType.ERROR)
                    Prepared(emptyList(), listOf(err), emptyList(), emptyList())
                } else {
                    val parsedBlockLines =
                        if (showParsedBlocks) results.map { LogEntry("  Gevonden: ${it.species} → ${it.count}", showInUi = true, includeInExport = true, type = LogType.PARSED_BLOCK) }
                        else emptyList()

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
                                logLines.add(LogEntry("⚠️ $nameFormatted is niet geselecteerd", showInUi = showWarnings, includeInExport = true, type = LogType.WARNING))
                                pendingAdditions.add(species to amount)
                            }
                            else -> {
                                logLines.add(LogEntry("❌ $nameFormatted niet herkend", showInUi = showErrors, includeInExport = true, type = LogType.ERROR))
                            }
                        }
                    }
                    Prepared(parsedBlockLines, logLines, validUpdates, pendingAdditions)
                }
            }

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
        // Gebruik dimen (px) i.p.v. hardcoded dp → schaalbaar per device via values-sw600dp
        val dm = resources.displayMetrics
        val screenPx = dm.widthPixels.toFloat()
        val desiredItemPx = resources.getDimension(R.dimen.grid_desired_column_width) // in px
        val span = (screenPx / desiredItemPx).toInt().coerceAtLeast(1)
        return span.coerceIn(1, 3) // max 3 kolommen (tablets)
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
