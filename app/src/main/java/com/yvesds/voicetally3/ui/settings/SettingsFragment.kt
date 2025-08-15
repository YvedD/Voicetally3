package com.yvesds.voicetally3.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.voicetally3.MainActivity
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.SharedPrefsHelper
import com.yvesds.voicetally3.data.SettingsKeys
import com.yvesds.voicetally3.databinding.FragmentSettingsBinding
import com.yvesds.voicetally3.utils.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefsHelper: SharedPrefsHelper

    private lateinit var settingsAdapter: SettingsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        val currentLanguage =
            sharedPrefsHelper.getString(SettingsKeys.SPEECH_INPUT_LANGUAGE, "nl-BE") ?: "nl-BE"

        val settingsItems = listOf(
            SettingItem(
                key = SettingsKeys.AUTO_SAVE_PER_HOUR,
                title = "Per uur automatisch opslaan",
                description = "Slaat automatisch op elke 58ste minuut de tellingen op",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.AUTO_SAVE_PER_HOUR, false),
                type = SettingType.SWITCH
            ),
            SettingItem(
                key = SettingsKeys.SPEECH_INPUT_LANGUAGE,
                title = "Spraakinvoertaal",
                description = "Kies de taal voor spraakinvoer",
                isChecked = false,
                type = SettingType.LANGUAGE
            ),
            SettingItem(
                key = SettingsKeys.LOG_PARTIALS,
                title = "Toon partial results",
                description = "Toon gedeeltelijke spraakresultaten in log",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARTIALS, true),
                type = SettingType.SWITCH
            ),
            SettingItem(
                key = SettingsKeys.LOG_FINALS,
                title = "Toon final results",
                description = "Toon finale spraakresultaten in log",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_FINALS, true),
                type = SettingType.SWITCH
            ),
            SettingItem(
                key = SettingsKeys.LOG_PARSED_BLOCKS,
                title = "Toon parsed blocks",
                description = "Toon gevonden blokken (soorten + aantallen) in log",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_PARSED_BLOCKS, true),
                type = SettingType.SWITCH
            ),
            /* SettingItem(
                key = SettingsKeys.LOG_WARNINGS,
                title = "Toon waarschuwingen",
                description = "Toon waarschuwingen zoals duplicaten of niet-geselecteerde soorten",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_WARNINGS, true),
                type = SettingType.SWITCH
            ), */
            SettingItem(
                key = SettingsKeys.LOG_ERRORS,
                title = "Toon fouten",
                description = "Toon foutmeldingen in log, zoals spraakherkenningsproblemen",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_ERRORS, true),
                type = SettingType.SWITCH
            ),
            SettingItem(
                key = SettingsKeys.LOG_INFO,
                title = "Toon informatieve logs",
                description = "Toon informatieve meldingen zoals instellingen of statusinfo",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.LOG_INFO, true),
                type = SettingType.SWITCH
            ),
            SettingItem(
                key = SettingsKeys.ENABLE_EXTRA_SOUNDS,
                title = "Extra geluiden activeren",
                description = "Activeert extra geluiden bij spraakherkenning en parsing",
                isChecked = sharedPrefsHelper.getBoolean(SettingsKeys.ENABLE_EXTRA_SOUNDS, true),
                type = SettingType.SWITCH
            )
        )

        settingsAdapter = SettingsAdapter(
            items = settingsItems,
            currentLanguage = currentLanguage,
            onToggle = { key, isChecked ->
                sharedPrefsHelper.setBoolean(key, isChecked)
                if (key == SettingsKeys.AUTO_SAVE_PER_HOUR) {
                    (activity as? MainActivity)?.updateAutoSaveTimer()
                }
            },
            onLanguageSelected = { langCode ->
                sharedPrefsHelper.setString(SettingsKeys.APP_LANGUAGE, langCode)
                sharedPrefsHelper.setString(SettingsKeys.SPEECH_INPUT_LANGUAGE, langCode)
                Log.d("SettingsFragment", "Spraakinvoertaal gewijzigd naar: $langCode")
                LocaleHelper.setLocale(requireContext(), langCode)
                activity?.recreate()
            }
        )

        binding.recyclerViewSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSettings.adapter = settingsAdapter

        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
}
