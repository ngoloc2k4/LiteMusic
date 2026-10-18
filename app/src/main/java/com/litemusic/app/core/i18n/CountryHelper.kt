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

    fun getMoodQuery(code: String, moodName: String): String {
        val c = code.uppercase()
        return when (c) {
            "VN" -> when (moodName) {
                "RELAX" -> "Nhạc chill acoustic nhẹ nhàng thư giãn V-Pop"
                "ENERGY" -> "Nhạc sôi động năng lượng V-Pop Dance"
                "FOCUS" -> "Lofi chill nhẹ nhàng tập trung học tập làm việc"
                "ROMANCE" -> "Nhạc tình yêu ballad lãng mạn ngọt ngào Việt Nam"
                "PARTY" -> "Nhạc quẩy tiệc tùng remix Vinahouse bar pub Việt Nam"
                "SLEEP" -> "Nhạc ngủ ngon piano acoustic êm dịu sâu lắng"
                "WORKOUT" -> "Nhạc tập gym fitness năng lượng sôi động Việt Nam"
                else -> "Nhạc tuyển chọn Việt Nam"
            }
            "US" -> when (moodName) {
                "RELAX" -> "Chill acoustic pop relaxing songs"
                "ENERGY" -> "High energy upbeat pop dance hits"
                "FOCUS" -> "Lofi hip hop beats study focus"
                "ROMANCE" -> "Romantic love songs sweet ballads"
                "PARTY" -> "Party club dance electronic hits"
                "SLEEP" -> "Deep sleep relaxing ambient rain piano"
                "WORKOUT" -> "Workout gym motivation energetic music"
                else -> "Top Hits USA"
            }
            "KR" -> when (moodName) {
                "RELAX" -> "K-Pop chill acoustic soft healing songs"
                "ENERGY" -> "K-Pop high energy hype dance hits"
                "FOCUS" -> "K-Pop lofi study beats chill piano"
                "ROMANCE" -> "K-Drama OST sweet romantic love songs"
                "PARTY" -> "K-Pop party club dance tracks"
                "SLEEP" -> "K-Pop sleep relaxing quiet ballads"
                "WORKOUT" -> "K-Pop workout gym fitness motivation"
                else -> "Top K-Pop"
            }
            "JP" -> when (moodName) {
                "RELAX" -> "J-Pop acoustic chill soft soothing songs"
                "ENERGY" -> "J-Pop high energy anime songs hype"
                "FOCUS" -> "Ghibli lofi study work instrumental beats"
                "ROMANCE" -> "J-Pop sweet romance love ballads"
                "PARTY" -> "J-Pop party club electronic dance"
                "SLEEP" -> "J-Pop relaxing piano sleep songs"
                "WORKOUT" -> "Anime workout fitness gym motivation"
                else -> "Top J-Pop"
            }
            "GB" -> when (moodName) {
                "RELAX" -> "UK acoustic indie chill relaxing songs"
                "ENERGY" -> "UK dance energetic drum and bass pop"
                "FOCUS" -> "UK ambient lofi beats study focus"
                "ROMANCE" -> "UK romantic ballads love songs"
                "PARTY" -> "UK garage club party dance hits"
                "SLEEP" -> "Peaceful acoustic sleep ambient piano"
                "WORKOUT" -> "UK gym workout motivation workout hits"
                else -> "Top Hits UK"
            }
            "TH" -> when (moodName) {
                "RELAX" -> "T-Pop chill acoustic relaxing soft songs"
                "ENERGY" -> "T-Pop dance upbeat energy hits"
                "FOCUS" -> "Thai lofi beats study chill"
                "ROMANCE" -> "Thai romantic love song ballads"
                "PARTY" -> "Thai party remix club hits"
                "SLEEP" -> "Thai sleep soft calm music"
                "WORKOUT" -> "Thai gym workout motivation hits"
                else -> "Top T-Pop"
            }
            "FR" -> when (moodName) {
                "RELAX" -> "Chanson française acoustique douce chill"
                "ENERGY" -> "French pop électro dance énergie"
                "FOCUS" -> "French lofi study beats travail"
                "ROMANCE" -> "Chansons romantiques françaises amour"
                "PARTY" -> "French club party électro remix"
                "SLEEP" -> "Musique douce nuit sommeil piano"
                "WORKOUT" -> "Musique motivation sport entraînement gym"
                else -> "Top Musique France"
            }
            "DE" -> when (moodName) {
                "RELAX" -> "Deutsche Pop Akustik entspannt chill"
                "ENERGY" -> "German Dance Electro Party Energie"
                "FOCUS" -> "Lofi Beats lernen konzentrieren"
                "ROMANCE" -> "Deutsche Liebeslieder Romantik"
                "PARTY" -> "German Club Party Schlager Remix"
                "SLEEP" -> "Schlafmusik sanft entspannend Piano"
                "WORKOUT" -> "Workout Gym Motivation Musik Training"
                else -> "Top Hits Deutschland"
            }
            "CN" -> when (moodName) {
                "RELAX" -> "C-Pop 治愈系民谣 轻松舒缓"
                "ENERGY" -> "C-Pop 动感电子 活力充沛"
                "FOCUS" -> "纯音乐 学习专注 轻音乐 Lofi"
                "ROMANCE" -> "甜歌情歌 浪漫甜蜜 流行精选"
                "PARTY" -> "夜店嗨歌 流行DJ 派对热曲"
                "SLEEP" -> "助眠纯音乐 睡眠冥想 催眠"
                "WORKOUT" -> "燃脂运动 健身动感 励志电音"
                else -> "Top C-Pop"
            }
            else -> when (moodName) {
                "RELAX" -> "$c chill acoustic relaxing songs"
                "ENERGY" -> "$c high energy upbeat dance music"
                "FOCUS" -> "Lofi study focus beats"
                "ROMANCE" -> "$c romantic love ballads"
                "PARTY" -> "$c party club dance hits"
                "SLEEP" -> "Deep sleep relaxing piano ambient"
                "WORKOUT" -> "Workout motivation fitness music"
                else -> "Top Music $c"
            }
        }
    }
}
