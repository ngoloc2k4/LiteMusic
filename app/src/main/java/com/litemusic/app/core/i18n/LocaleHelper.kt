package com.litemusic.app.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.litemusic.app.ui.theme.ThemePreferences
import java.util.Locale

object LocaleHelper {

    fun applyLocale(context: Context, language: String) {
        if (language == "system") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                val defaultLocale = Locale.getDefault()
                updateResources(context, defaultLocale)
            }
        } else {
            val locale = Locale(language)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
            } else {
                updateResources(context, locale)
            }
        }
    }

    fun onAttach(context: Context): Context {
        val lang = ThemePreferences.getAppLanguage(context)
        return if (lang == "system") {
            context
        } else {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            updateResources(context, locale)
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            return context.createConfigurationContext(config)
        }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return context
    }
}
