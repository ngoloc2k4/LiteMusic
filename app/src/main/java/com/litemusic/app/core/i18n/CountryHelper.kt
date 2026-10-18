package com.litemusic.app.core.i18n

import android.content.Context
import com.litemusic.app.ui.theme.ThemePreferences
import java.util.Locale

data class CountryOption(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
    val trendingQuery: String,
    val hitsQuery: String,
    val videoQuery: String,
    val remixQuery: String
)

object CountryHelper {
    const val COUNTRY_SYSTEM = "system"

    val POPULAR_COUNTRIES = listOf(
        CountryOption("VN", "Việt Nam", "🇻🇳", "Top Music Trending Vietnam", "Bản hit hôm nay V-Pop", "Top Music Video Thịnh Hành Việt Nam", "Top Remix Vinahouse Hot TikTok"),
        CountryOption("US", "Hoa Kỳ / Toàn cầu", "🇺🇸", "Top Music Trending USA", "Today's Hits Pop", "Top Music Videos", "Top Covers and Remixes"),
        CountryOption("KR", "Hàn Quốc", "🇰🇷", "Top K-Pop Trending", "Today's K-Pop Hits", "Top K-Pop Music Videos", "K-Pop Remix Hits"),
        CountryOption("JP", "Nhật Bản", "🇯🇵", "Top J-Pop Trending", "Today's J-Pop Hits", "Top J-Pop Music Videos", "Anime Music Hits"),
        CountryOption("GB", "Vương quốc Anh", "🇬🇧", "Top Music Trending UK", "UK Today's Hits", "Top UK Music Videos", "UK Dance Remixes"),
        CountryOption("TH", "Thái Lan", "🇹🇭", "Top Music Trending Thailand", "T-Pop Hits", "Top Thai Music Videos", "Thai Remix Hits"),
        CountryOption("FR", "Pháp", "🇫🇷", "Top Music Trending France", "Hits du Moment France", "Top Musique France", "French Remix Hits"),
        CountryOption("DE", "Đức", "🇩🇪", "Top Music Trending Germany", "Top Hits Deutschland", "Top Musikvideos Deutschland", "German Club Hits"),
        CountryOption("CN", "Trung Quốc", "🇨🇳", "Top C-Pop Trending", "C-Pop Hits", "Top Chinese Music Videos", "C-Pop Remix TikTok")
    )

    fun getCountryOption(code: String): CountryOption {
        return POPULAR_COUNTRIES.find { it.code.equals(code, ignoreCase = true) }
            ?: CountryOption(
                code = code.uppercase(),
                displayName = Locale("", code).displayCountry.ifEmpty { code.uppercase() },
                flagEmoji = "🌐",
                trendingQuery = "Top Music Trending $code",
                hitsQuery = "Today's Hits $code",
                videoQuery = "Top Music Video $code",
                remixQuery = "Top Remix $code"
            )
    }

    fun getEffectiveCountryCode(context: Context): String {
        val pref = ThemePreferences.getAppCountry(context)
        if (pref != COUNTRY_SYSTEM && pref.isNotBlank()) {
            return pref.uppercase()
        }
        val systemCountry = Locale.getDefault().country
        return if (systemCountry.isNullOrBlank()) "VN" else systemCountry.uppercase()
    }

    fun getSystemCountryDisplay(context: Context): String {
        val code = Locale.getDefault().country.ifEmpty { "VN" }.uppercase()
        val opt = POPULAR_COUNTRIES.find { it.code.equals(code, ignoreCase = true) }
        val name = opt?.displayName ?: Locale("", code).displayCountry.ifEmpty { code }
        val flag = opt?.flagEmoji ?: "🌐"
        return "$flag $name ($code)"
    }
}
