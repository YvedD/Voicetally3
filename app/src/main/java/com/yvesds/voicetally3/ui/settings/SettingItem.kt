package com.yvesds.voicetally3.ui.settings

enum class SettingType {
    SWITCH,
    LANGUAGE
}

data class SettingItem(
    val key: String,
    val title: String,
    val description: String = "",
    val isChecked: Boolean = false,
    val type: SettingType = SettingType.SWITCH
)
