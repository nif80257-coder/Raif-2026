package com.cinemax.app.core

import com.cinemax.app.addons.StremioMeta
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioStream

/** Built-in public-domain picks carry ids like "pd:tt1254207". */
const val PUBLIC_DOMAIN_ID_PREFIX = "pd:"

/** Name the built-in public-domain shelf shows. */
const val PUBLIC_DOMAIN_PROVIDER = "الملكية العامة"

/** One curated public-domain film with a direct HTTPS stream. */
data class PublicDomainFilm(
    val imdbId: String,
    val name: String,
    val year: String,
    val description: String,
    val genres: List<String>,
    val poster: String,
    val background: String,
    val streamUrl: String,
    val streamLabel: String
)

/**
 * Curated public-domain films, each with a direct HTTPS stream that was checked
 * with a range request and answers `video/mp4`, and needs no account, key or token.
 *
 * The set opens with Stremio's own `stremio-public-domain` pick (Big Buck Bunny)
 * and the rest of Blender's open movies, then the classics that are out of
 * copyright in the United States. That repository's own stream entry is a plain
 * `http://` file, which this app refuses, so the same film streams from the
 * Internet Archive over HTTPS instead.
 *
 * Posters come from Metahub by IMDb id; Elephants Dream has no Metahub entry and
 * uses the archive item's own artwork.
 */
val PUBLIC_DOMAIN_FILMS = listOf(
    PublicDomainFilm(
        imdbId = "tt1254207",
        name = "الأرنب الكبير (Big Buck Bunny)",
        year = "2008",
        description = "أرنب كبير يستيقظ ليجد ثلاثة مخلوقات صغيرة تزعجه، فيقرر أن يردّ بالمثل. " +
            "فيلم قصير من إنتاج Blender Foundation، صدر في الملكية العامة.",
        genres = listOf("رسوم متحركة", "قصير", "كوميدي"),
        poster = "https://live.metahub.space/poster/small/tt1254207/img",
        background = "https://live.metahub.space/background/medium/tt1254207/img",
        streamUrl = "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
        streamLabel = "720p · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt1727581",
        name = "سينتل (Sintel)",
        year = "2010",
        description = "فتاة تبحث عن التنين الصغير الذي ربّته، في رحلة عبر أرض قاسية. " +
            "فيلم قصير من Blender Foundation، صدر في الملكية العامة ويُعد من أجمل أفلام الرسوم المتحركة المفتوحة.",
        genres = listOf("رسوم متحركة", "مغامرة", "دراما"),
        poster = "https://live.metahub.space/poster/small/tt1727581/img",
        background = "https://live.metahub.space/background/medium/tt1727581/img",
        streamUrl = "https://archive.org/download/Sintel/sintel-2048-surround.mp4",
        streamLabel = "1080p · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt3966550",
        name = "حلم الفيلة (Elephants Dream)",
        year = "2006",
        description = "رجلان غريبان داخل آلة عجيبة تتغير بلا توقف، في أول فيلم مفتوح المصدر من Blender Foundation.",
        genres = listOf("رسوم متحركة", "خيال علمي", "قصير"),
        poster = "https://archive.org/services/img/ElephantsDream",
        background = "https://archive.org/services/img/ElephantsDream",
        streamUrl = "https://archive.org/download/ElephantsDream/ed_hd.mp4",
        streamLabel = "HD · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0063350",
        name = "ليلة الموتى الأحياء (Night of the Living Dead)",
        year = "1968",
        description = "غرباء يتحصّنون في منزل ريفي بينما يحيط بهم الموتى الأحياء. " +
            "كلاسيكية رعب منخفضة الميزانية للمخرج George A. Romero، صارت في الملكية العامة.",
        genres = listOf("رعب", "كلاسيكي"),
        poster = "https://live.metahub.space/poster/small/tt0063350/img",
        background = "https://live.metahub.space/background/medium/tt0063350/img",
        streamUrl = "https://archive.org/download/Night.Of.The.Living.Dead_1080p/NightOfTheLivingDead_1080p.mp4",
        streamLabel = "1080p · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0056923",
        name = "شاريد (Charade)",
        year = "1963",
        description = "أرملة شابة تكتشف أن زوجها الراحل أخفى ثروة، فيلاحقها رجال خطرون ويحاول رجل غامض مساعدتها. " +
            "كوميديا وتشويق من بطولة كاري غرانت وأودري هيبورن.",
        genres = listOf("إثارة", "كوميديا", "غموض"),
        poster = "https://live.metahub.space/poster/small/tt0056923/img",
        background = "https://live.metahub.space/background/medium/tt0056923/img",
        streamUrl = "https://archive.org/download/charade_fhd/charade_fhd.mp4",
        streamLabel = "1080p · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0032599",
        name = "فتاته يوم الجمعة (His Girl Friday)",
        year = "1940",
        description = "محررة صحفية بارعة ورئيس تحريرها وزوجها السابق، في كوميديا إعلامية لا تتوقف عن الكلام.",
        genres = listOf("كوميديا", "دراما", "كلاسيكي"),
        poster = "https://live.metahub.space/poster/small/tt0032599/img",
        background = "https://live.metahub.space/background/medium/tt0032599/img",
        streamUrl = "https://archive.org/download/Popcornarchive-hisGirlFriday1940/Popcornarchive-hisGirlFriday1940.mp4",
        streamLabel = "HD · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0055830",
        name = "كرنفال الأرواح (Carnival of Souls)",
        year = "1962",
        description = "امرأة تنجو من حادث سيارة فتنتقل إلى بلدة صغيرة، فترافقها أشباح حتى جناح مهجور على ضفة النهر. " +
            "رعب مستقل صار في الملكية العامة.",
        genres = listOf("رعب", "غموض"),
        poster = "https://live.metahub.space/poster/small/tt0055830/img",
        background = "https://live.metahub.space/background/medium/tt0055830/img",
        streamUrl = "https://archive.org/download/1-carnival-of-souls/1Carnival_of_Souls.mp4",
        streamLabel = "SD · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0015324",
        name = "شيرلوك الابن (Sherlock Jr.)",
        year = "1924",
        description = "عامل سينما يحلم بأن يصبح محققًا، فيدخل الشاشة نفسها. من روائع بستر كيتون الصامتة.",
        genres = listOf("كوميديا", "صامت", "كلاسيكي"),
        poster = "https://live.metahub.space/poster/small/tt0015324/img",
        background = "https://live.metahub.space/background/medium/tt0015324/img",
        streamUrl = "https://archive.org/download/sherlock.-jr..-1924.1080p.-blu-ray.x-264-yts.-am/Sherlock.Jr..1924.1080p.BluRay.x264-%5BYTS.AM%5D.mp4",
        streamLabel = "1080p · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0017925",
        name = "الجنرال (The General)",
        year = "1926",
        description = "سائق قطار يحاول استعادة حبيبته وقطاره خلال الحرب الأهلية الأمريكية. " +
            "تحفة بستر كيتون في الحركة الصامتة.",
        genres = listOf("كوميديا", "حركة", "صامت"),
        poster = "https://live.metahub.space/poster/small/tt0017925/img",
        background = "https://live.metahub.space/background/medium/tt0017925/img",
        streamUrl = "https://archive.org/download/TheGeneral_201312/The-General-v2.mp4",
        streamLabel = "HD · MP4"
    ),
    PublicDomainFilm(
        imdbId = "tt0013442",
        name = "نوسفيراتو (Nosferatu)",
        year = "1922",
        description = "أول فيلم مصاص دماء في تاريخ السينما، من إخراج F.W. Murnau، نسخة صامتة مقتبسة من دراكولا.",
        genres = listOf("رعب", "صامت", "كلاسيكي"),
        poster = "https://live.metahub.space/poster/small/tt0013442/img",
        background = "https://live.metahub.space/background/medium/tt0013442/img",
        streamUrl = "https://archive.org/download/Nosferatu1922HD/Nosferatu%20%281922%29%20HD.mp4",
        streamLabel = "HD · MP4"
    )
)

fun isPublicDomainId(id: String): Boolean = id.startsWith(PUBLIC_DOMAIN_ID_PREFIX)

fun publicDomainImdbId(id: String): String = id.removePrefix(PUBLIC_DOMAIN_ID_PREFIX)

/** The film record behind a `pd:` id, or null for an id this build does not know. */
fun publicDomainFilm(id: String): PublicDomainFilm? =
    PUBLIC_DOMAIN_FILMS.firstOrNull { it.imdbId == publicDomainImdbId(id) }

/** The shelf's previews, in the shape the home feed already uses. */
fun publicDomainPreviews(): List<StremioMetaPreview> = PUBLIC_DOMAIN_FILMS.map { film ->
    StremioMetaPreview(
        id = PUBLIC_DOMAIN_ID_PREFIX + film.imdbId,
        type = "movie",
        name = film.name,
        poster = film.poster,
        background = film.background,
        description = film.description,
        releaseInfo = film.year
    )
}

/** Full metadata for the detail screen. */
fun publicDomainMeta(id: String): StremioMeta? {
    val film = publicDomainFilm(id) ?: return null
    return StremioMeta(
        id = PUBLIC_DOMAIN_ID_PREFIX + film.imdbId,
        type = "movie",
        name = film.name,
        poster = film.poster,
        background = film.background,
        description = film.description,
        releaseInfo = film.year,
        genres = film.genres
    )
}

/** The film's direct streams. */
fun publicDomainStreams(id: String): List<StremioStream> {
    val film = publicDomainFilm(id) ?: return emptyList()
    return listOf(StremioStream(name = film.streamLabel, title = film.name, url = film.streamUrl))
}
