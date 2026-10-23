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
        try {
            val locale = if (language == "system") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.content.res.Resources.getSystem().configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    android.content.res.Resources.getSystem().configuration.locale
                }
            } else {
                parseLocale(language)
            }

            Locale.setDefault(locale)
            updateResources(context, locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (language == "system") {
                    localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    localeManager?.applicationLocales = LocaleList.forLanguageTags(language.replace("-r", "-"))
                }
            }
        } catch (_: Throwable) {}
    }

    private fun parseLocale(language: String): Locale {
        return try {
            if (language.contains("-r")) {
                val parts = language.split("-r")
                Locale(parts[0], parts[1])
            } else if (language.contains("-")) {
                val parts = language.split("-")
                Locale(parts[0], parts[1])
            } else {
                Locale(language)
            }
        } catch (_: Throwable) {
            Locale.getDefault()
        }
    }

    fun onAttach(context: Context): Context {
        return try {
            val lang = ThemePreferences.getAppLanguage(context)
            if (lang == "system") {
                context
            } else {
                val locale = parseLocale(lang)
                Locale.setDefault(locale)
                updateResources(context, locale)
            }
        } catch (_: Throwable) {
            context
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        return try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context.createConfigurationContext(config)
        } catch (_: Throwable) {
            context
        }
    }
}
