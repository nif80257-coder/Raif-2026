package com.nuvio.app.features.addons

/**
 * The add-on ecosystem review from `docs/Raif-Addon-Ecosystem-Review.md`, as data the app renders.
 *
 * Keeping it typed (rather than shipping the Markdown) means the screen can order the backlog by
 * priority and count what is already shipped, and the text stays reviewable in one place. The
 * document remains the long-form record; this file is what the app shows.
 */
internal enum class AddonReviewSource(val label: String) {
    AwesomeStremio("awesome-stremio"),
    HelloWorld("addon-helloworld"),
    PublicDomain("stremio-public-domain"),
    OfficialSources("official-sources"),
}

/** Where a reviewed feature stands in this app today. */
internal enum class AddonReviewVerdict {
    Present,
    Partial,
    Missing,
    NotApplicable,
}

internal enum class AddonReviewDifficulty {
    Easy,
    Medium,
    Hard,
}

internal data class AddonReviewEntry(
    val feature: String,
    val source: AddonReviewSource,
    val summary: String,
    val verdict: AddonReviewVerdict,
    val benefit: String,
    val difficulty: AddonReviewDifficulty,
    /** 5 is the highest. */
    val priority: Int,
)

/** Something the app already does, but not the way the reviewed source does it. */
internal data class AddonReviewFix(
    val topic: String,
    val where: String,
    val problem: String,
    val fix: String,
)

internal data class AddonReviewRisk(
    val title: String,
    val body: String,
)

internal data class AddonReviewStep(
    val title: String,
    val steps: List<String>,
)

internal val AddonReviewEntries: List<AddonReviewEntry> = listOf(
    AddonReviewEntry(
        feature = "Cinemeta",
        source = AddonReviewSource.AwesomeStremio,
        summary = "كتالوجات وميتاداتا رسمية، ومورد addon_catalog",
        verdict = AddonReviewVerdict.Present,
        benefit = "أساس الرئيسية والاكتشاف",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "Torrentio",
        source = AddonReviewSource.AwesomeStremio,
        summary = "مجمّع تورنت و debrid متعدد المصادر",
        verdict = AddonReviewVerdict.Present,
        benefit = "أهم مصدر تورنت في القائمة",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "Orion",
        source = AddonReviewSource.AwesomeStremio,
        summary = "مصدر تورنت و debrid بمفتاح API؛ أُزيل من القائمة الافتراضية",
        verdict = AddonReviewVerdict.Missing,
        benefit = "لا فائدة بلا مفتاح خاص بالمستخدم، ومفتاحه السابق لم يكن له",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "Deflix",
        source = AddonReviewSource.AwesomeStremio,
        summary = "إضافة debrid فقط، بلا تورنت",
        verdict = AddonReviewVerdict.Missing,
        benefit = "لا شيء بلا اشتراك debrid مدفوع",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "متتبّعات عامة منفردة",
        source = AddonReviewSource.AwesomeStremio,
        summary = "1337x و RARBG و TPB و TPB+ و pct كإضافات مستقلة",
        verdict = AddonReviewVerdict.Partial,
        benefit = "مغطّاة عبر المجمّعات، لا حاجة لإضافة لكل متتبّع",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "Stream Quality Filter",
        source = AddonReviewSource.AwesomeStremio,
        summary = "إزالة التكرار والترتيب بالجودة قبل العرض",
        verdict = AddonReviewVerdict.Present,
        benefit = "قائمة مصادر مرتّبة بلا تكرار",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "كتالوجات أنمي",
        source = AddonReviewSource.AwesomeStremio,
        summary = "Kitsu و HorribleSubs و Animes Brasil",
        verdict = AddonReviewVerdict.Missing,
        benefit = "مستبعد بقرار المنتج: أفلام ومسلسلات فقط",
        difficulty = AddonReviewDifficulty.Hard,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "بث مباشر (Live TV)",
        source = AddonReviewSource.AwesomeStremio,
        summary = "إضافات بنوع channel مثل dlive.tv",
        verdict = AddonReviewVerdict.Missing,
        benefit = "محتوى مباشر لا يقدّمه التطبيق اليوم",
        difficulty = AddonReviewDifficulty.Hard,
        priority = 2,
    ),
    AddonReviewEntry(
        feature = "بودكاست",
        source = AddonReviewSource.AwesomeStremio,
        summary = "إضافات بنوع other أو podcasts",
        verdict = AddonReviewVerdict.Missing,
        benefit = "جمهور مختلف تمامًا عن مستخدم التطبيق",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "OpenSubtitles الرسمي",
        source = AddonReviewSource.AwesomeStremio,
        summary = "مزوّد ترجمات رسمي بلا مفتاح عبر بروكسي Stremio",
        verdict = AddonReviewVerdict.Present,
        benefit = "تغطية ترجمة عربية مع أربعة مزوّدين آخرين",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "قائمة IMDb ككتالوج",
        source = AddonReviewSource.AwesomeStremio,
        summary = "استخدام أي قائمة IMDb يبنيها المستخدم ككتالوج",
        verdict = AddonReviewVerdict.Missing,
        benefit = "الفجوة الوحيدة في تغطية القوائم: لدينا Trakt و MDBList و TMDB",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "كتالوج من وسم IMDb",
        source = AddonReviewSource.AwesomeStremio,
        summary = "بناء كتالوج ديناميكي من وسم أو كلمة مفتاحية",
        verdict = AddonReviewVerdict.Missing,
        benefit = "اكتشاف أدق من التصنيفات الجاهزة",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 3,
    ),
    AddonReviewEntry(
        feature = "كتالوجات الجوائز",
        source = AddonReviewSource.AwesomeStremio,
        summary = "Top 250 وأوسكار وكان وفينيسيا وبرلين و RT Certified",
        verdict = AddonReviewVerdict.Partial,
        benefit = "موجودة جزئيًا داخل أحد مصادرنا عبر curated=awards",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 3,
    ),
    AddonReviewEntry(
        feature = "دليل إضافات داخل التطبيق",
        source = AddonReviewSource.AwesomeStremio,
        summary = "مورد addon_catalog ومجموعة إضافات المجتمع api.strem.io",
        verdict = AddonReviewVerdict.Missing,
        benefit = "تثبيت مصدر بلمستين بدل لصق رابط، وفتح كل ما في هذه المستودعات",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "PimpMyStremio",
        source = AddonReviewSource.AwesomeStremio,
        summary = "مدير إضافات محلي يشغّل إضافات على Node",
        verdict = AddonReviewVerdict.NotApplicable,
        benefit = "غير ممكن على iOS، ويشغّل كودًا من الشبكة",
        difficulty = AddonReviewDifficulty.Hard,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "Stremio Downloader",
        source = AddonReviewSource.AwesomeStremio,
        summary = "تنزيل البث للمشاهدة بلا اتصال",
        verdict = AddonReviewVerdict.Present,
        benefit = "تنزيلات كاملة مع نشاط مباشر على iOS",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "أدوات مطوّري الإضافات",
        source = AddonReviewSource.AwesomeStremio,
        summary = "SDK بعدة لغات ومساعد نشر وبناء كتالوجات",
        verdict = AddonReviewVerdict.Missing,
        benefit = "لا تخص المستخدم النهائي",
        difficulty = AddonReviewDifficulty.Hard,
        priority = 1,
    ),
    AddonReviewEntry(
        feature = "روابط HTTP مباشرة",
        source = AddonReviewSource.HelloWorld,
        summary = "حقل url في نتيجة البث",
        verdict = AddonReviewVerdict.Present,
        benefit = "أساس البث المباشر",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "بث تورنت p2p",
        source = AddonReviewSource.HelloWorld,
        summary = "infoHash و fileIdx و trackers",
        verdict = AddonReviewVerdict.Present,
        benefit = "المصدر الأساسي بلا تكلفة، مع شاشة إجماع",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "يوتيوب (ytId)",
        source = AddonReviewSource.HelloWorld,
        summary = "فيديو يوتيوب بمعرّفه فقط",
        verdict = AddonReviewVerdict.Present,
        benefit = "محتوى مجاني إضافي يعمل داخل التطبيق",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "روابط خارجية (externalUrl)",
        source = AddonReviewSource.HelloWorld,
        summary = "إعادة توجيه إلى صفحة خارجية بدل ملف فيديو",
        verdict = AddonReviewVerdict.Partial,
        benefit = "روابط رسمية بلا خرق حقوق، لكن بلا وسم جهة أو تحقق HTTPS",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "extra: بحث وتصنيف وترقيم",
        source = AddonReviewSource.HelloWorld,
        summary = "search و genre و skip داخل الكتالوج",
        verdict = AddonReviewVerdict.Present,
        benefit = "تصفّح كامل داخل كل كتالوج",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "idPrefixes",
        source = AddonReviewSource.HelloWorld,
        summary = "المعرّفات التي يقبلها المصدر",
        verdict = AddonReviewVerdict.Present,
        benefit = "إخفاء المصادر التي لا تخدم العنوان",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "مورد meta",
        source = AddonReviewSource.HelloWorld,
        summary = "ميتاداتا كاملة تأتي من الإضافة نفسها",
        verdict = AddonReviewVerdict.Present,
        benefit = "صفحة تفاصيل أغنى",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "ملصقات MetaHub",
        source = AddonReviewSource.HelloWorld,
        summary = "صور جاهزة من images.metahub.space بلا مفتاح",
        verdict = AddonReviewVerdict.Missing,
        benefit = "إصلاح عناوين تظهر بلا ملصق",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 2,
    ),
    AddonReviewEntry(
        feature = "Public Domain Movies",
        source = AddonReviewSource.PublicDomain,
        summary = "أكثر من ٥٠٠ فيلم ملكية عامة عبر تورنت",
        verdict = AddonReviewVerdict.Present,
        benefit = "محتوى قانوني مجاني بلا مفاتيح، وتحقّقت أن مانيفستها حيّ",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "قائمة استبعاد",
        source = AddonReviewSource.PublicDomain,
        summary = "exclude/meta.json لاستبعاد ما لا يحق نشره",
        verdict = AddonReviewVerdict.Missing,
        benefit = "تحكّم بما يظهر لك ولأطفالك",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "خانة «مجاني وقانوني»",
        source = AddonReviewSource.PublicDomain,
        summary = "تصنيف واضح للمحتوى الحر في الرئيسية",
        verdict = AddonReviewVerdict.Missing,
        benefit = "مصدر لا ينقطع حين تفشل المصادر الأخرى",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "توثيق ترخيص المصادر",
        source = AddonReviewSource.PublicDomain,
        summary = "بيان حقوق كل مصدر داخل الواجهة",
        verdict = AddonReviewVerdict.Partial,
        benefit = "شفافية وثقة، وهو موثّق في الشيفرة لا في الواجهة",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 3,
    ),
    AddonReviewEntry(
        feature = "أين أشاهد رسميًا",
        source = AddonReviewSource.OfficialSources,
        summary = "روابط اشتراك وإيجار عبر externalUrl",
        verdict = AddonReviewVerdict.Partial,
        benefit = "بديل حين لا يوجد بث؛ WatchHub مثبّت لكنه أقدم وبلا منطقة أو حلقات",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 5,
    ),
    AddonReviewEntry(
        feature = "تكامل Watchmode",
        source = AddonReviewSource.OfficialSources,
        summary = "توفّر دقيق لكل منطقة مع مستوى الحلقة",
        verdict = AddonReviewVerdict.Missing,
        benefit = "دقة أعلى، وخطة مجانية بحدود ٢٥٠٠ طلب شهريًا",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "توكن إعداد في الرابط",
        source = AddonReviewSource.OfficialSources,
        summary = "تفضيلات مُصدَّرة داخل رابط المانيفست بلا قاعدة بيانات",
        verdict = AddonReviewVerdict.Partial,
        benefit = "التطبيق يقبل روابط مهيّأة ولا يبنيها",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 3,
    ),
    AddonReviewEntry(
        feature = "manifest.config",
        source = AddonReviewSource.OfficialSources,
        summary = "حقول إعداد يعلنها المانيفست لتُعرض كنموذج",
        verdict = AddonReviewVerdict.Missing,
        benefit = "تهيئة بعض الإضافات بلا متصفح خارجي",
        difficulty = AddonReviewDifficulty.Medium,
        priority = 4,
    ),
    AddonReviewEntry(
        feature = "ترتيب المجموعات",
        source = AddonReviewSource.OfficialSources,
        summary = "أولوية الظهور تتبع ترتيب الإضافات المثبّتة",
        verdict = AddonReviewVerdict.Present,
        benefit = "تحريك أعلى وأسفل موجود، والسحب ليس بعد",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 2,
    ),
    AddonReviewEntry(
        feature = "تحصين الروابط الخارجية",
        source = AddonReviewSource.OfficialSources,
        summary = "HTTPS فقط، ورفض صفحات الخدمة الجذرية، ووسم الحلقة التقريبية",
        verdict = AddonReviewVerdict.Missing,
        benefit = "أمان ومنع فخ، ونفتح اليوم أي externalUrl كما هو",
        difficulty = AddonReviewDifficulty.Easy,
        priority = 5,
    ),
)

internal val AddonReviewFixes: List<AddonReviewFix> = listOf(
    AddonReviewFix(
        topic = "addon_catalog",
        where = "AddonModels.kt / AddonManifestParser.kt",
        problem = "كان الحقل غائبًا عن النموذج، فلم يكن هناك دليل إضافات",
        fix = "أُضيف addonCatalogs وقراءته، وصار الدليل يُفتح ويُثبّت من شاشة المصادر",
    ),
    AddonReviewFix(
        topic = "config[]",
        where = "AddonManifestParser.kt",
        problem = "كان يُهمَل، فتبقى الإضافة تحتاج تهيئة في المتصفح",
        fix = "أُضيف نموذج التهيئة الداخلي وبناء رابط المانيفست المهيّأ",
    ),
    AddonReviewFix(
        topic = "background",
        where = "AddonManifest.kt",
        problem = "لا يُقرأ، فبطاقات الإضافات بلا خلفية",
        fix = "إضافة الحقل",
    ),
    AddonReviewFix(
        topic = "behaviorHints.adult",
        where = "AddonManifestParser.kt",
        problem = "يُقرأ ولا يُستخدم في أي فلتر",
        fix = "فلتر اختياري في الرئيسية والبحث",
    ),
    AddonReviewFix(
        topic = "externalUrl",
        where = "StreamsScreen / PlayerDestination",
        problem = "فتح مباشر بلا وسم جهة ولا اشتراط HTTPS",
        fix = "وسم المضيف وتأكيد قبل الخروج",
    ),
    AddonReviewFix(
        topic = "WatchHub",
        where = "DefaultAddonSources.kt",
        problem = "يُصنّف «مباشر» ويُخلط مع ملفات الفيديو",
        fix = "تصنيف رابع «رسمي» في AddonTransport",
    ),
    AddonReviewFix(
        topic = "Orion",
        where = "DefaultAddonSources.kt",
        problem = "مفتاح Orion لمستخدم آخر مضمّن في الرابط",
        fix = "أُزيل المصدر ومفتاحه من القائمة الافتراضية، ومضيفه صار في RetiredDefaultAddonHosts",
    ),
    AddonReviewFix(
        topic = "optionsLimit",
        where = "AddonCatalog",
        problem = "يُقرأ ولا يُستخدم في بناء رابط الطلب المجمّع",
        fix = "استخدامه عند الطلبات المجمّعة",
    ),
)

internal val AddonReviewRisks: List<AddonReviewRisk> = listOf(
    AddonReviewRisk(
        title = "طبقات المحتوى",
        body = "آمن: الملكية العامة وروابط الخدمات الرسمية وأرشيف الإنترنت والميتاداتا. " +
            "رمادي: كل متتبّعات التورنت، وهي أفلام ومسلسلات محمية في معظم الدول. " +
            "التطبيق عميل ولا يستضيف محتوى، لكن توزيعه بمصادر جاهزة يضعه في موقع مختلف عن تطبيق فارغ.",
    ),
    AddonReviewRisk(
        title = "المفاتيح المضمّنة",
        body = "كان مفتاح Orion في القائمة الافتراضية ليس للمستخدم: يستهلك حصة شخص آخر، " +
            "ويتوقف المصدر بلا إنذار عند إبطاله، ويكشف بيانات اعتماد طرف ثالث داخل ملفات التطبيق. " +
            "أُزيل الآن، ومضيفه في RetiredDefaultAddonHosts فلا يعود بتحديث أو سحب من الخادم. " +
            "القاعدة: لا مفاتيح في القائمة الافتراضية، وهو نفس سبب إخراج AutoStream و Jackettio.",
    ),
    AddonReviewRisk(
        title = "الروابط الخارجية",
        body = "أي إضافة قد تعيد رابطًا يفتح تطبيقًا آخر عبر Universal Link. " +
            "الحد الأدنى: HTTPS فقط، وعرض اسم المضيف قبل الخروج، وتأكيد صريح، ورفض صفحات الجذر العامة.",
    ),
    AddonReviewRisk(
        title = "الخصوصية تجاه الإضافات",
        body = "كل إضافة بث ترى معرّف ما تشاهده، أي قائمة مشاهدة بالوكالة. " +
            "دليل الإضافات يجب أن يعرض هذا قبل التثبيت، ويعرض behaviorHints.p2p كتحذير.",
    ),
    AddonReviewRisk(
        title = "p2p",
        body = "بث التورنت يكشف عنوان IP للأقران. شاشة الإجماع يجب أن تبقى إلزامية " +
            "مع كل مصدر تورنت يُضاف من الدليل.",
    ),
    AddonReviewRisk(
        title = "Watchmode",
        body = "الخطة المجانية حدودها ٢٥٠٠ طلب شهريًا وثلاث دول وبلا روابط حلقات. " +
            "شروطهم تسمح بتخزين البيانات غير المصوّرة ٣٠ يومًا كحد أقصى وتطلب إسنادًا، " +
            "وسحب بيانات JustWatch مباشرة يخالف شروطهم.",
    ),
    AddonReviewRisk(
        title = "الترجمات",
        body = "الترجمة عمل مشتق. مزوّداتنا تعمل في منطقة رمادية، " +
            "و OpenSubtitles عبر بروكسي Stremio يتجنّب الحاجة إلى مفتاح.",
    ),
    AddonReviewRisk(
        title = "كود محلي",
        body = "لا تنفيذ JS من الشبكة في التطبيق. " +
            "أي حل على طريقة PimpMyStremio يجب ألا يُضاف.",
    ),
)

internal val AddonReviewPlan: List<AddonReviewStep> = listOf(
    AddonReviewStep(
        title = "دليل إضافات داخل التطبيق",
        steps = listOf(
            "أضف AddonCatalogRef و addonCatalogs إلى النموذج والمانيفست بارسر",
            "اجلب catalog/{type}/{id}/addon_catalog.json عبر AddonHttpClient بسقف ١٥ ثانية",
            "شاشة دليل تعرض الاسم والوصف وشارات المصدر وزر تثبيت يستدعي addAddon",
        ),
    ),
    AddonReviewStep(
        title = "خانة «مجاني وقانوني»",
        steps = listOf(
            "Public Domain Movies مثبّتة أصلًا، فلا حاجة لمصدر جديد",
            "أضف LegalCatalogAddonHosts وامنع تقليمها كما تفعل KeptCatalogAddonHosts",
            "قسم ثابت أعلى الرئيسية يُخفى بهدوء إن أُوقف الكتالوج؛ Archivio متقاعد فلا يُضاف",
        ),
    ),
    AddonReviewStep(
        title = "«أين أشاهد رسميًا» وتحصين الروابط",
        steps = listOf(
            "ارفض غير HTTPS في externalOpenUrl واعرض مضيف الوجهة",
            "شارة «رسمي» في بطاقة المصدر وتأكيد قبل الخروج من التطبيق",
            "قسم «متوفر على» من TMDB watch/providers بلا مفتاح جديد",
        ),
    ),
    AddonReviewStep(
        title = "فلترة المحتوى",
        steps = listOf(
            "استخدم behaviorHints.adult في كتالوجات الرئيسية والبحث",
            "مخزن إخفاء عناوين لكل ملف شخصي على نمط AddonPauseExceptions مع clear عند الحذف",
            "زر «إخفاء هذا العنوان» في PosterActionSheet وإمكانية الاسترجاع",
        ),
    ),
    AddonReviewStep(
        title = "نموذج إعداد الإضافة",
        steps = listOf(
            "اقرأ config من المانيفست إلى AddonConfigField",
            "نموذج داخل شاشة الإضافات بدل فتح رابط التهيئة في المتصفح",
            "أضف القيم كـ query params وثبّت عبر addAddon، بلا تخزين مشترك بين الملفات",
        ),
    ),
)

/** Entries still worth building, the highest priority first and the cheapest first within it. */
internal fun missingInPriorityOrder(
    entries: List<AddonReviewEntry> = AddonReviewEntries,
): List<AddonReviewEntry> = entries
    .filter { entry -> entry.verdict == AddonReviewVerdict.Missing || entry.verdict == AddonReviewVerdict.Partial }
    .sortedWith(compareByDescending<AddonReviewEntry> { it.priority }.thenBy { it.difficulty.ordinal })

internal fun List<AddonReviewEntry>.countVerdict(verdict: AddonReviewVerdict): Int =
    count { entry -> entry.verdict == verdict }

internal fun List<AddonReviewEntry>.bySource(source: AddonReviewSource): List<AddonReviewEntry> =
    filter { entry -> entry.source == source }
