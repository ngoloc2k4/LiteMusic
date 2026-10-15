package com.litemusic.app.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.litemusic.app.ui.theme.ThemePreferences
import java.util.Locale

object LocaleHelper {

    fun applyLocale(context: Context, language: String) {
        if (language == "system") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                val defaultLocale = Locale.getDefault()
                updateResources(context, defaultLocale)
            }
        } else {
            val locale = parseLocale(language)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(language.replace("-r", "-"))
            } else {
                updateResources(context, locale)
            }
        }
    }

    private fun parseLocale(language: String): Locale {
        return if (language.contains("-r")) {
            val parts = language.split("-r")
            Locale(parts[0], parts[1])
        } else if (language.contains("-")) {
            val parts = language.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(language)
        }
    }

    fun onAttach(context: Context): Context {
        val lang = ThemePreferences.getAppLanguage(context)
        return if (lang == "system") {
            context
        } else {
            val locale = parseLocale(lang)
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
