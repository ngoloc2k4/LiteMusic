package com.litemusic.app.ui.theme

import android.content.Context
import android.content.SharedPreferences

object ThemePreferences {
    private const val PREFS_NAME = "litemusic_user_settings"
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_COLOR_PALETTE = "pref_color_palette"
    private const val KEY_THEME_STYLE = "pref_theme_style"
    private const val KEY_DSP_ENABLED = "pref_dsp_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val name = getPrefs(context).getString(KEY_THEME_MODE, AppThemeMode.DARK_OLED.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.DARK_OLED.name)
        } catch (e: Exception) {
            AppThemeMode.DARK_OLED
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getColorPalette(context: Context): AppColorPalette {
        val name = getPrefs(context).getString(KEY_COLOR_PALETTE, AppColorPalette.SYSTEM_MONET.name)
        return try {
            AppColorPalette.valueOf(name ?: AppColorPalette.SYSTEM_MONET.name)
        } catch (e: Exception) {
            AppColorPalette.SYSTEM_MONET
        }
    }

    fun setColorPalette(context: Context, palette: AppColorPalette) {
        getPrefs(context).edit().putString(KEY_COLOR_PALETTE, palette.name).apply()
    }

    fun getThemeStyle(context: Context): AppThemeStyle {
        val name = getPrefs(context).getString(KEY_THEME_STYLE, AppThemeStyle.MATERIAL_3.name)
        return try {
            AppThemeStyle.valueOf(name ?: AppThemeStyle.MATERIAL_3.name)
        } catch (e: Exception) {
            AppThemeStyle.MATERIAL_3
        }
    }

    fun setThemeStyle(context: Context, style: AppThemeStyle) {
        getPrefs(context).edit().putString(KEY_THEME_STYLE, style.name).apply()
    }

    fun getDspEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DSP_ENABLED, true)
    }

    fun setDspEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DSP_ENABLED, enabled).apply()
    }

    private const val KEY_SKIP_DURATION = "pref_skip_duration"
    private const val KEY_DEBUG_MODE = "pref_debug_mode"

    fun getSkipDuration(context: Context): Int {
        return getPrefs(context).getInt(KEY_SKIP_DURATION, 10)
    }

    fun setSkipDuration(context: Context, durationSeconds: Int) {
        getPrefs(context).edit().putInt(KEY_SKIP_DURATION, durationSeconds).apply()
    }

    fun isDebugMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEBUG_MODE, false)
    }

    fun setDebugMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }
}

