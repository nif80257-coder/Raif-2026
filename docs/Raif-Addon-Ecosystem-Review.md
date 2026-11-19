# مراجعة منظومة الإضافات — Řaif

تاريخ الفحص: بعد آخر تعديلات مسار المصادر. المراجع الأربعة المفحوصة:

1. `doingodswork/awesome-stremio` — قائمة منسّقة (CC0) لأدوات وإضافات Stremio مفتوحة المصدر.
2. `Stremio/addon-helloworld` — مثال SDK الرسمي، يوضّح بنية المانيفست وأنواع روابط البث.
3. `Stremio/stremio-public-domain` — مستودع بيانات (MIT) لمحتوى ملكية عامة + الإضافة الرسمية المنشورة منه.
4. `user-griffin/official-sources` — إضافة «توفّر رسمي» (availability) صُمّمت أصلًا لـ Nuvio، تعيد روابط `externalUrl` للخدمات الرسمية عبر Watchmode.

**المنهج:** قرأت README كل مستودع، ثم تحقّقت من المانيفستات الحيّة بنفسي (`watchhub.strem.io`، `caching.stremio.net/publicdomainmovies`، `v3-cinemeta` من `stremio-official-addons/index.json`)، وقرأت مجلد إضافات المجتمع `api.strem.io/addonscollection.json` (٣٢١ كيلوبايت). في المقابل قارنت مع شيفرة التطبيق الفعلية: `AddonModels.kt`، `AddonManifestParser.kt`، `StreamParser.kt`، `StreamModels.kt`، `DefaultAddonSources.kt`، `AppUrlBridge.kt`، `AddonHealthCheckModels.kt`، `SearchRepository.kt`.

**مفتاح العمود «موجودة في تطبيقي؟»:** ✅ موجودة · ⚠️ جزئية أو موجودة بشكل ناقص · ❌ غير موجودة · — لا ينطبق على iOS.

**الأولوية:** ٥ = الأعلى، ١ = الأدنى.

---

## ١) `awesome-stremio`

| الميزة/المصدر | المستودع | الوصف | موجودة في تطبيقي؟ | الفائدة للمستخدم | الصعوبة التقنية | الأولوية |
|---|---|---|---|---|---|---|
| Cinemeta (catalog + meta + addon_catalog) | awesome-stremio | كتالوج وميتاداتا رسمية | ✅ مثبّت (`v3-cinemeta`) | أساس الرئيسية والاكتشاف | — | — |
| Torrentio | awesome-stremio | مجمّع تورنت + debrid متعدد المصادر | ✅ مثبّت (نسخة عربية) | أهم مصدر تورنت | — | — |
| Orion | awesome-stremio | تورنت + debrid، يحتاج مفتاح API | ❌ غير موجودة — أُزيل المصدر ومفتاحه | لا فائدة بلا مفتاح خاص | سهل | 1 |
| Deflix | awesome-stremio | debrid فقط، مكتوب بـ Go | ❌ | لا قيمة بلا debrid مدفوع | — | ١ |
| 1337x / RARBG / TPB / TPB+ / pct / Juan Carlos 2 | awesome-stremio | إضافات متتبّعات عامة منفردة | ⚠️ مغطاة عبر مجمّعاتنا | لا شيء إضافي | — | ١ |
| Stream Quality Filter (SQF) | awesome-stremio | إزالة التكرار والترتيب بالجودة | ✅ ميزة داخلية (dedupe + رتب الجودة) | واجهة مصادر مرتّبة | — | — |
| Animes Brasil / HorribleSubs / Kitsu | awesome-stremio | أنمي | ❌ | مستبعد بقرارك | — | ١ |
| Lobo Guara (PT-BR) | awesome-stremio | مسلسلات مدبلجة برتغالية | ❌ | لغة غير مستهدفة | — | ١ |
| dlive.tv (Live TV) | awesome-stremio | بث مباشر بنوع `channel` | ❌ نوع `channel` غير مدعوم | محتوى مباشر | عالية | ٢ |
| Podcasts / podcasts-for-all | awesome-stremio | نوع `other`/بودكاست | ❌ | جمهور مختلف | متوسطة | ١ |
| OpenSubtitles الرسمي | awesome-stremio | ترجمات | ✅ `opensubtitles-v3` + ٤ مزوّدين | تغطية عربية | — | — |
| IMDb list addon (jaruba) | awesome-stremio | أي قائمة IMDb ككتالوج عبر بيانات المستخدم في الرابط | ❌ (لدينا Trakt/MDBList/TMDB فقط) | قوائم يبنيها المستخدم | متوسطة | ٤ |
| IMDb tag addon (jaruba) | awesome-stremio | كتالوج ديناميكي من وسم IMDb | ❌ | اكتشاف أدق | متوسطة | ٣ |
| Top movies (Top 250 / RT Certified / أوسكار / كان وبرلين) | awesome-stremio | كتالوجات جوائز وقوائم مرجعية | ⚠️ جزئي عبر `curated=awards` في أحد مصادرنا | رئيسية أغنى | منخفضة | ٣ |
| مجموعة إضافات المجتمع + مورد `addon_catalog` | awesome-stremio | دليل إضافات يقدّمه الخادم نفسه | ❌ التطبيق لا يقرأ `addonCatalogs` إطلاقًا | تثبيت بلمسة بدل لصق روابط | متوسطة | ٥ |
| PimpMyStremio | awesome-stremio | مدير إضافات محلي يشغّل Node | ❌ وغير قابل للتنفيذ على iOS + خطر تنفيذ كود | — | عالية جدًا | ١ |
| Stremio Downloader | awesome-stremio | تنزيل البث | ✅ تنزيلات كاملة (Android + iOS/Live Activity) | مشاهدة بلا إنترنت | — | — |
| Addon publishing helper / Catalog builder / SDKs | awesome-stremio | أدوات مطوّرين | ❌ | لا تخص المستخدم النهائي | عالية | ١ |
| Flatpak / RaspberryPi / install scripts | awesome-stremio | تثبيت سطح المكتب | — | لا ينطبق | — | — |

## ٢) `addon-helloworld`

| الميزة/المصدر | المستودع | الوصف | موجودة في تطبيقي؟ | الفائدة للمستخدم | الصعوبة التقنية | الأولوية |
|---|---|---|---|---|---|---|
| `url` (HTTP مباشر) | addon-helloworld | تشغيل ملف عبر HTTP | ✅ | أساس البث المباشر | — | — |
| `infoHash` + `fileIdx` + `sources: tracker:` | addon-helloworld | بث تورنت p2p | ✅ محرك p2p + إجماع المستخدم | المصدر الأساسي بلا تكلفة | — | — |
| `ytId` (YouTube) | addon-helloworld | فيديو يوتيوب | ✅ `YouTubeStreamResolver` + مستخرج داخلي | محتوى مجاني إضافي | — | — |
| `externalUrl` | addon-helloworld | إعادة توجيه لصفحة خارجية | ⚠️ يُفتح خارجيًا بلا وسم وجهة أو تحقق HTTPS | روابط رسمية بلا كسر حقوق | منخفضة | ٥ |
| `catalogs[].extra` (search / genre / skip) | addon-helloworld | تصفّح وبحث وترقيم | ✅ | تصفّح كامل | — | — |
| `idPrefixes` | addon-helloworld | تحديد معرفات يقبلها المصدر | ✅ | إخفاء مصادر لا تخدم العنوان | — | — |
| مورد `meta` (ميتاداتا كاملة من الإضافة) | addon-helloworld | صفحة تفاصيل من المصدر | ✅ `MetaDetailsRepository` | تفاصيل أغنى | — | — |
| صور MetaHub كبديل (`images.metahub.space`) | addon-helloworld | ملصق من ميتاهب بلا مفتاح | ❌ نعتمد TMDB/Cinemeta فقط | إصلاح ملصقات ناقصة | منخفضة | ٢ |
| نشر serverless | addon-helloworld | نشر الإضافة | — | يخص مطوّري الإضافات | — | — |

## ٣) `stremio-public-domain`

| الميزة/المصدر | المستودع | الوصف | موجودة في تطبيقي؟ | الفائدة للمستخدم | الصعوبة التقنية | الأولوية |
|---|---|---|---|---|---|---|
| إضافة Public Domain Movies | stremio-public-domain | ٥٠٠+ فيلم ملكية عامة، تورنت قانوني | ✅ مثبّتة، وتحقّقت أن مانيفستها حيّ الآن | محتوى قانوني مجاني بلا مفاتيح | — | — |
| `exclude/meta.json` | stremio-public-domain | قائمة استبعاد يدوية لما أُدرج خطأً | ❌ لا قائمة حجب/إخفاء في التطبيق | تحكّم بما يظهر لك ولأطفالك | منخفضة | ٤ |
| بيانات الملكية العامة كحزمة/كتالوج «مجاني وقانوني» | stremio-public-domain | تصنيف واضح للمحتوى الحر | ❌ لا خانة كهذه في الرئيسية | مصدر يعمل دائمًا حين تفشل المصادر | منخفضة | ٥ |
| توثيق ترخيص كل مصدر | stremio-public-domain | سياسة مراجعة الحقوق | ⚠️ موثّق في الشيفرة لا في الواجهة | شفافية وثقة | منخفضة | ٣ |

## ٤) `official-sources`

| الميزة/المصدر | المستودع | الوصف | موجودة في تطبيقي؟ | الفائدة للمستخدم | الصعوبة التقنية | الأولوية |
|---|---|---|---|---|---|---|
| مورد «أين أشاهد رسميًا» عبر `externalUrl` | official-sources | روابط اشتراك/إيجار رسمية | ⚠️ `watchhub.strem.io` مثبّت (نفس الفكرة، أقدم، بلا منطقة أو حلقات) | بديل حين لا يوجد بث | متوسطة | ٥ |
| تكامل Watchmode (منطقة + حلقة + ترتيب المزوّد) | official-sources | توفّر دقيق لكل منطقة | ❌ | دقة أعلى | متوسطة | ٤ |
| توكن إعداد مُصدَّر في رابط المانيفست | official-sources | تفضيلات بلا قاعدة بيانات | ⚠️ التطبيق يقبل روابط مهيّأة ولا يبنيها | إضافة مُهيّأة بسرعة | متوسطة | ٣ |
| `manifest.config[]` (نموذج إعداد داخل التطبيق) | official-sources / مجتمع | حقول إعداد يعلنها المانيفست | ❌ `AddonManifestParser` يقرأ `behaviorHints` فقط | تهيئة بعض الإضافات بلا متصفح | متوسطة | ٤ |
| ترتيب المجموعات = ترتيب الإضافات | official-sources | أولوية الظهور | ✅ تحريك أعلى/أسفل (لا سحب) | التحكم بما يظهر أولًا | منخفضة | ٢ |
| رفض صفحات الخدمة الجذرية والروابط غير HTTPS ووسم الحلقة التقريبية | official-sources | تحصين الروابط الخارجية | ❌ نفتح أي `externalUrl` كما هو | أمان ومنع فخ | منخفضة | ٥ |

---

## الميزات الغائبة مرتّبة بالأولوية

1. **دليل إضافات داخل التطبيق** — التطبيق لا يقرأ `addonCatalogs` ولا `addon_catalog`، مع أن Cinemeta يعلنها (`official` و`community`) ومجموعة المجتمع جاهزة كـ JSON. أعلى قيمة: يحوّل «أضف مصدرًا بلصق رابط» إلى تصفّح وتثبيت بلمستين، ويفتح كل إضافات المستودعات الأربعة تقريبًا.
2. **خانة محتوى مجاني وقانوني** — إضافة Public Domain Movies مثبّتة عندنا بالفعل، لكنها غير ظاهرة كخانة. تحويلها إلى قسم واضح يعطي مصدرًا لا ينقطع ولا يحتاج مفتاحًا ولا يشكّل خطر حقوق.
3. **«أين أشاهد رسميًا» + تحصين الروابط الخارجية** — `externalUrl` اليوم يُفتح بلا وسم جهة ولا اشتراط HTTPS. مع WatchHub المثبّت، هذا يعني انتقالًا أعمى خارج التطبيق.
4. **فلترة المحتوى: احترام `behaviorHints.adult` + قائمة إخفاء** — العَلَم مقروء في `AddonManifestParser` ولا يُستخدم في أي مكان، ولا توجد وسيلة لإخفاء عنوان.
5. **نموذج إعداد الإضافة من `manifest.config[]`** — يفتح إضافات مثل AIOStatus وIMDb Ratings وCast Search بلا متصفح خارجي.
6. **كتالوجات IMDb** (قائمة IMDb / وسم IMDb) — قوائم يبنيها المستخدم بنفسه، وهي الفجوة الوحيدة في تغطية القوائم (لدينا Trakt وMDBList وTMDB).
7. **كتالوجات الجوائز** (Top 250، أوسكار، كان/فينيسيا/برلين) — أغنى للاكتشاف، والأسهل عبر TMDB discover + قائمة ثابتة.
8. **تصنيف رابع للمصادر: «رسمي»** — `AddonTransport` فيه Torrent/Direct/Catalog فقط، فتُصنّف WatchHub كـ«مباشر» وتُخلط روابط الاشتراك مع ملفات الفيديو.
9. **MetaHub كبديل ملصقات** — إصلاح سريع لعناوين تظهر بلا ملصق.
10. **سحب لإعادة ترتيب الإضافات** بدل زرّي أعلى/أسفل.
11. **Live TV (`channel`)** و**البودكاست (`other`)** — قيمة منخفضة لك (طلبت أفلامًا ومسلسلات) وكلفة عالية (أنواع محتوى جديدة تمرّ في كل الشاشات).
12. **PimpMyStremio** — مرفوض: يشغّل كود Node محليًا، غير ممكن على iOS، وخطر أمني مباشر.

## «موجودة لكن بشكل خاطئ» — إصلاحات صغيرة عالية الأثر

| الموضوع | الموضع | المشكلة | الإصلاح |
|---|---|---|---|
| `addon_catalog` | `AddonModels.kt` / `AddonManifestParser.kt` | الحقل غير موجود في النموذج أصلًا | إضافة `addonCatalogs` + القراءة |
| `config[]` | `AddonManifestParser.kt` | يُهمَل، فتبقى الإضافة «تحتاج تهيئة» بلا وسيلة | قراءة الحقول وعرض نموذج |
| `background` | `AddonManifest.kt` | لا يُقرأ، فبطاقات الإضافات بلا خلفية | إضافة الحقل |
| `behaviorHints.adult` | `AddonManifestParser.kt:92` | يُقرأ ولا يُستخدم | فلتر اختياري |
| `externalUrl` | `StreamsScreen` / `PlayerDestination` | فتح مباشر بلا وسم أو اشتراط HTTPS | وسم الجهة + تأكيد + HTTPS فقط |
| WatchHub | `DefaultAddonSources.kt:105` | يصنّف «مباشر» ويخلط مع ملفات | تصنيف «رسمي» رابع في `AddonTransport.kt` |
| Orion | `DefaultAddonSources.kt` + `RetiredDefaultAddonHosts` | مفتاح Orion لمستخدم آخر مضمّن في الرابط | أُزيل المصدر ومفتاحه، وصار مضيفه متقاعدًا فيُقلَّم عند الإقلاع وعند السحب من الخادم |
| `catalogs[].optionsLimit` | `AddonCatalog` | يُقرأ ولا يُستخدم في بناء الرابط | استخدامه في الطلبات المجمّعة |

---

## المخاطر القانونية والأمنية

**١. طبقات المحتوى:**
- **آمن:** Public Domain Movies (ملكية عامة، MIT)، WatchHub/`official-sources` (روابط الخدمات الرسمية فقط)، Internet Archive (Archivio)، Cinemeta (ميتاداتا).
- **رمادي:** كل متتبّعات التورنت (Torrentio، 1337x، RARBG clones، TorrentsDB، TorrentClaw...) — أفلام ومسلسلات محمية بحقوق في معظم الدول. التطبيق عميل ولا يستضيف محتوى، لكن توزيعه كتطبيق «مصادر جاهزة» يضعه في موقع مختلف عن تطبيق فارغ.
- **مستبعد:** debrid مدفوع — قرارك، وهو الأقل خطرًا قانونيًا لكنه الأغلى.

**٢. المفاتيح المضمّنة:** `DefaultAddonSources.kt` كان يحتوي مفتاح Orion (`"api":"LEJ8..."`) و`:98` كان يحتوي مفتاح Real-Debrid (أُزيل). المفتاح المضمّن ليس للمستخدم: يستهلك حصة شخص آخر، ويتوقف المصدر بلا إنذار عند إبطاله، ويكشف بيانات اعتماد طرف ثالث في ملفات التطبيق. القاعدة الصحيحة: **لا مفاتيح في القائمة الافتراضية**؛ نفس السبب الذي أُخرج به AutoStream وJackettio. مفتاح Orion أُزيل الآن أيضًا، ومضيفه في `RetiredDefaultAddonHosts`.

**٣. الروابط الخارجية (`externalUrl`):** أي إضافة قد تعيد رابطًا يفتح تطبيقًا آخر عبر Universal Link (بنك، محفظة، متجر). مع دليل إضافات يُثبّت بلمسة، يرتفع هذا الخطر. الحد الأدنى: HTTPS فقط، عرض اسم المضيف قبل الخروج، تأكيد صريح، ورفض صفحات الجذر العامة (كما يفعل `official-sources`).

**٤. الخصوصية تجاه الإضافات:** كل إضافة بث ترى معرّف ما تشاهده (IMDb/TMDB) — أي قائمة مشاهدة بالوكالة. الدليل يجب أن يعرض «هذا المصدر سيعرف ما تشاهد» قبل التثبيت، ويعرض `behaviorHints.p2p` (متاح في نموذجنا) كتحذير.

**٥. p2p:** بث التورنت يكشف عنوان IP للأقران. عندنا شاشة إجماع (`P2pConsentDialog`) — يجب أن تبقى إلزامية مع أي مصدر تورنت جديد يُضاف من الدليل.

**٦. Watchmode:** خطة مجانية = ٢٥٠٠ طلب/شهر و٣ دول فقط وبلا روابط حلقات أو deep links؛ شروطهم تسمح بتخزين البيانات غير المصوّرة ٣٠ يومًا كحد أقصى وتطلب إسنادًا. سحب بيانات JustWatch مباشرة يخالف شروطهم.

**٧. الترجمات:** الترجمات عمل مشتق؛ مزوّداتنا (SubDL/SubSource/Subtito/SubMaker) تعمل في منطقة رمادية، وOpenSubtitles عبر بروكسي Stremio يتجنّب الحاجة لمفتاح.

**٨. كود الإضافة محليًا:** لا يوجد في التطبيق أي تنفيذ JS من شبكة (الملفات `QuickJS` مخصّصة لإضافات «المزوّد» الداخلية). أي حل على طريقة PimpMyStremio يجب ألا يُضاف.

---

## أفضل ٥ ميزات يُنصح بإضافتها — وخطوات التنفيذ

### ١) دليل إضافات داخل التطبيق (الأولوية ٥)
1. `AddonModels.kt`: أضف `AddonCatalogRef(type, id, name)` وحقل `addonCatalogs: List<AddonCatalogRef>` إلى `AddonManifest`.
2. `AddonManifestParser.kt`: اقرأ `addonCatalogs` بنفس أسلوب `catalogs()`.
3. ملف جديد `features/addons/AddonDirectoryRepository.kt`: ابنِ الرابط `{transportUrl base}/catalog/{type}/{id}/addon_catalog.json` (أعد استخدام `buildCatalogUrl` في `features/catalog/`)، واجلبه عبر `AddonHttpClient` مع `retryAddonFetch` وسقف ١٥ ثانية.
4. شاشة `features/addons/AddonDirectoryScreen.kt`: قائمة بمجموعة المجتمع، كل سطر فيه الاسم والوصف والشارات (كتالوج/بث/ترجمات، p2p، adult) وزر «تثبيت» يستدعي `AddonRepository.addAddon` الموجود.
5. الشاشة تُفتح من `AddonsScreen` بزر «تصفّح الإضافات»، ونصوصها في `composeResources/values/strings.xml` و`values-ar/strings.xml`.
6. تحقّق: `AddonTransportTest` و`DefaultAddonSourcesTest` يجب أن يبقيا أخضر، والدليل لا يلمس القائمة الافتراضية.

### ٢) خانة «مجاني وقانوني» (الأولوية ٥)
1. المصدر موجود أصلًا في القائمة الافتراضية (`caching.stremio.net/publicdomainmovies`)، فلا حاجة لإضافة جديدة.
2. `DefaultAddonSources.kt`: أضف `LegalCatalogAddonHosts` بقيم `publicdomainmovies` و`archivio.elfhosted.com` وامنع تقليمها (نفس نمط `KeptCatalogAddonHosts`).
3. `HomeCatalogDefinitions.kt`: أعطِ كتالوجات هذه المضيفين وسمًا محليًا («مجاني وقانوني») بدل اسم الإضافة فقط.
4. `HomeScreen.kt`: قسم ثابت أعلى الشاشة يقرأ هذا الكتالوج إن كان مفعّلًا، ويُخفى بهدوء إن أُوقف.
5. فحص حيّ أولًا لـ `archivio.elfhosted.com` قبل إضافته إلى القائمة (قاعدة المصادر: لا شيء يُضاف بلا فحص).

### ٣) «أين أشاهد رسميًا» + تحصين `externalUrl` (الأولوية ٥)
1. `StreamModels.kt`: أضف `externalOpenUrl` موسومًا بمضيف الوجهة، وارفض غير `https` (اليوم `externalUrl` يمرّ كما هو).
2. `StreamCard.kt`: شارة «رسمي · Netflix» بدل شارة تورنت، وتحذير قبل الخروج من التطبيق.
3. `AddonTransport.kt`: أضف `Official` (WatchHub ومضيفات availability) فيظهر في مجموعة رابعة واضحة داخل شاشة المصادر.
4. `features/details/`: قسم «متوفر على» مبني على TMDB `watch/providers` (المفتاح والتخزين موجودان في `TmdbSettings`)، بلا Watchmode ولا مفتاح جديد؛ الروابط تفتح صفحات الخدمة الرسمية فقط.
5. إن أردت لاحقًا دقة الحلقات والمنطقة: أضف `official-sources` كمصدر مستضاف بمفتاح Watchmode على حسابك (٢٥٠٠ طلب/شهر).

### ٤) فلترة المحتوى: `adult` + قائمة إخفاء (الأولوية ٤)
1. `AddonManifestParser.kt` يقرأ العَلَم بالفعل — استخدمه في `HomeCatalogDefinitions.excludingPausedSources` وأضف `excludingAdult(enabled: Boolean)` بنفس النمط.
2. مخزن جديد `features/library/HiddenTitlesRepository.kt` على نمط `AddonPauseExceptions` (نفس محرك التخزين لكل ملف شخصي، مع `clear` عند حذف الملف الشخصي كما فعلنا في `ProfileRepository`).
3. فلترة في `HomeRepository` و`SearchRepository` قبل عرض العناصر، لا بعدها.
4. زر «إخفاء هذا العنوان» في `PosterActionSheet.kt`، وعرض القائمة مع إمكانية الاسترجاع في إعدادات المكتبة.
5. اختبارات في `commonTest/features/library/` على نمط `HomePausedSourceFilterTest`.

### ٥) نموذج إعداد الإضافة من `manifest.config[]` (الأولوية ٤)
1. `AddonModels.kt`: `AddonConfigField(key, type, title, description, default, options, required)`.
2. `AddonManifestParser.kt`: اقرأ مصفوفة `config` (النمط ظاهر في `api.strem.io/addonscollection.json`: حقول `text`/`select`/`number`).
3. `AddonsScreen.kt`: إن وُجدت حقول، أظهر «تهيئة» نموذجًا داخليًا بدل `uriHandler.openUri(configureUrl)` الحالي (يبقى كخيار ثانٍ).
4. أضف قيم النموذج كـ query params إلى رابط المانيفست، تمامًا كما تفعل روابطنا المهيّأة اليوم (`cinema.debrid.in/sort=...`)، وثبّت الإضافة عبر `AddonRepository.addAddon`.
5. لا تخزّن أي قيمة إعداد في مكان مشترك بين الملفات الشخصية — استخدم `ProfileScopedKey` الموجود.

---

## ما لم يُتحقق منه

- لم أشغّل أي إضافة من هذه المستودعات داخل التطبيق؛ التحقق كان على مستوى المانيفستات الحيّة (٤ طلبات HTTP) وقراءة الشيفرة.
- `official-sources` يحتاج نشرًا ومفتاح Watchmode، فلم أختبر استجاباته.
- مجموعة المجتمع `api.strem.io/addonscollection.json` غير مُصفّاة: كثير منها متوقف أو يحتاج مفاتيح. أي دليل يُبنى عليها يجب أن يمرّ على `AddonHealthCheckRepository` قبل العرض، أو أن نبني قائمة منسّقة بأسمائنا.
- لم أعدّل أي ملف في التطبيق؛ هذا الملف تحليلي فقط.


## داخل التطبيق

هذا المستند معروض داخل التطبيق: **الإعدادات ← الاكتشاف والمحتوى ← الإضافات ← «افتح مراجعة المنظومة»**.

- المحتوى المنظّم (الجرد والأولويات والإصلاحات والمخاطر والخطة) في `composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonEcosystemReviewContent.kt`.
- الشاشة في `AddonEcosystemReviewScreen.kt`: ورقة ملء الشاشة تعرض الأولويات أولًا، ثم الجرد بحسب المستودع، ثم الإصلاحات والمخاطر والخطة.
- نصوص التنقل والشارات في `composeResources/values/strings.xml` و`values-ar/strings.xml`؛ أما نص الجرد فبالعربية لأن بقية ملفات `docs/` كذلك.

المستند يبقى السجل المطوّل، والتطبيق يعرض نفس المحتوى مرتّبًا حسب الأولوية.


## ما نُفِّذ من الخطة

آخر تحديث: بعد تطبيق البنود القابلة للتنفيذ بأمان. الحالة في الجدول أعلاه تصف ما كان قبل التنفيذ.

**١) `addon_catalog` و`config` — صارا مقروءين**
- `AddonModels.kt`: `AddonCatalogRef`، `AddonConfigField`، حقلَا `addonCatalogs` و`configFields` في `AddonManifest`، ودالة `servesAddonDirectory()`.
- `AddonManifestParser.kt`: قراءة `addonCatalogs` و`config` بنفس أسلوب `catalogs()`.
- `HomeCatalogDefinitions.kt`: الحقلان يدخلان بصمة الكاش، فلا تبقى مانيفستات قديمة بعد تحديث مصدر.
- ما لم يُبنَ بعد: شاشة تصفّح الدليل، ونموذج تهيئة الإضافة. البنية جاهزة لهما.

**٢) تصنيف «توفّر رسمي» رابع**
- `AddonTransport.kt`: `Official` و`OfficialSourceHosts`، فيظهر WatchHub ومضيفات availability في مجموعة منفصلة عن ملفات الفيديو.
- `AddonsScreen.kt`: عنوان المجموعة الجديد.

**٣) تحصين `externalUrl`**
- `StreamModels.kt`: `externalOpenUrl` لا يقبل إلا صفحة ويب (`https` أو `http`) ويرفض مخططات التطبيقات والتورنت، و`externalOpenHost` يعطي المضيف للعرض قبل الخروج. راجع «اكتمال الروابط الخارجية» أدناه للتوسعة الأخيرة.

**٤) فلترة المحتوى**
- `ContentFilterRepository.kt` (جديد): عناوين مخفية لكل ملف شخصي، ومفتاح «إخفاء المصادر المصنّفة للبالغين» الذي يحترم `behaviorHints.adult`، مع `clear` عند حذف الملف الشخصي على نمط `AddonPauseExceptions`.
- `HomeRepository.kt` و`SearchRepository.kt`: المصادر الموسومة للبالغين تُستبعد قبل الجلب، والعناوين المخفية تُسقط من الرئيسية والأقسام والبحث والاكتشاف.
- `AddonsScreen.kt`: قسم «فلاتر المحتوى» فيه المفتاح وعدد العناوين المخفية وزر «أظهرها مرة أخرى».
- `DetailActionButtons.kt` + `MetaDetailsScreen.kt`: «إخفاء هذا العنوان» في قائمة إجراءات صفحة التفاصيل.

**٥) اختبارات**
- `AddonManifestExtrasTest.kt` (جديد): قراءة `addonCatalogs` و`config`، وأن مانيفستًا بلا دليل لا يُعدّ دليلًا.
- `ContentFilterTest.kt` (جديد): إسقاط العناوين المخفية، أن الإخفاء لكل نوع، وأن المصادر الموسومة للبالغين تُسقط فقط عند تشغيل المفتاح.
- `AddonTransportTest.kt`: حالة `Official`.

**ما بقي من الخطة ولم يُنفَّذ**
- خانة «مجاني وقانوني» في الرئيسية: فحص Archivio أظهر أنه **متقاعد** (`docs.elfhosted.com/app/archivio`، ٢٤ سبتمبر ٢٠٢٦: «Retired and unsupported»)، فلم يُضف. Public Domain Movies مثبّتة بالفعل وتكفي.
- كتالوجات IMDb وكتالوجات الجوائز.
- استخدام `catalogs[].optionsLimit` في الطلبات المجمّعة: يحتاج أولًا دعم اختيار متعدد في حقول `extra`، فالعمل ليس صغيرًا.
- مفتاح Orion المضمّن أُزيل مع مصدره: القائمة الآن ٤١ مصدرًا (٣ كتالوجات + ٥ ترجمات + ٣٣ بث)، ومضيف `orion.baby-beamup.club` في `RetiredDefaultAddonHosts` فلا يعود بتحديث ولا بسحب من الخادم، وتغطّيه اختبارات `DefaultAddonSourcesTest`.

**`background` وملصقات MetaHub وقائمة الحجب — نُفِّذت**
- `AddonModels.kt` / `AddonManifestParser.kt`: حقل `backgroundUrl` مقروء من المانيفست ويُحلّ نسبةً إلى رابطه، ويُعرض شريطًا خافتًا أعلى بطاقة المصدر في `AddonsScreen.kt`.
- `MetaHubArtwork.kt` (جديد): `MetaDetails.withMetahubArtwork()` يملأ الملصق والخلفية والشعار من `images.metahub.space` بالمعرّف IMDb حين يتركها المصدر فارغة، ويُطبَّق في `MetaDetailsParser`، فلا يبقى عنوان بلا صورة إن كان له `imdb_id`. لا يلمس ما جاء من المصدر.
- `HiddenTitleImport.kt` (جديد): قارئ قوائم الاستبعاد (`exclude/meta.json`) بأشكالها: `metas` أو `exclude` أو `items`، وعنصر مغلّف بـ`meta` أو مسطّح أو نص `type:id`. `ContentFilterRepository.importHiddenTitles` يدمجها في العناوين المخفية ويعيد عدد الجديد، وواجهته في «فلاتر المحتوى»: زر «استيراد قائمة حجب» ونافذة لصق.
- اختبارات: `MetaHubArtworkTest.kt` و`HiddenTitleImportTest.kt`.

**دليل الإضافات ونموذج التهيئة — نُفِّذا**
- `AddonDirectoryModels.kt` (جديد): `AddonDirectoryEntry` و`AddonDirectoryUiState`، و`AddonDirectoryParser` الذي يقرأ `metas` أو `addons`، ويقبل `manifestUrl` أو `transportUrl` (بإضافة `/manifest.json`) أو مانيفستًا مضمّنًا، ويستخرج النوع و`p2p` و`adult`.
- `AddonDirectoryRepository.kt` (جديد): يجلب `{base}/catalog/{type}/{id}.json` عبر `buildAddonResourceUrl` و`fetchAddonResponseText` (مع إعادة المحاولة)، ويعرض حالة الجلب والخطأ ويعلّم المثبّت.
- `AddonDirectoryScreen.kt` (جديد): ورقة ملء الشاشة من قسم «دليل الإضافات» في شاشة المصادر — بحث، شارات النوع والتورنت والبالغين والمثبّت، وتحذير p2p، وزر «تثبيت» يستدعي `AddonRepository.addAddon` الموجود.
- `AddonConfigUrl.kt` (جديد): `buildConfiguredManifestUrl` يبني رابط المانيفست بزرّ الحقول في الاستعلام، ويحفظ استعلام الرابط الأصلي ويستبدل المفاتيح التي يغيّرها النموذج، مع `hasAllRequiredValues` للتحقق.
- `AddonConfigDialog.kt` (جديد): نموذج داخلي لحقول `config[]` (نص واختيار، والمطلوب)، يثبّت النسخة المهيّأة ويبقي «صفحة التهيئة» خيارًا ثانيًا. زر «تهيئة» في بطاقة المصدر يفتح النموذج حين يعلن المانيفست حقولًا، وإلا يبقى المتصفح.
- `AddonDirectoryTest.kt` (جديد): أشكال الدليل الثلاثة، الإسقاط والتكرار، العَلَم المثبّت، وبناء رابط التهيئة وتشفيره ودمج الاستعلام.


**«متوفر على» من TMDB — نُفِّذ**
- `WhereToWatchRepository.kt` (جديد): يجلب `{movie|tv}/{id}/watch/providers` بمنطقة المشاهدة، ويحوّل معرّف العنوان إلى معرّف TMDB عبر `TmdbService.ensureTmdbId` (بـ`imdb_id` عند الحاجة)، ويخزّن النتيجة لكل عنوان ومنطقة. لا يعمل إلا حين تكون إضافة TMDB مفعّلة، ويستخدم مفتاحها أو المفتاح المضمّن.
- `WhereToWatchSection.kt` (جديد): قسم في صفحة التفاصيل يعرض المزوّدين بشعاراتهم مجمّعين حسب الطريقة (بثّ باشتراك، مجاني، مع إعلانات، إيجار، شراء)، وزرًا يفتح صفحة TMDB الكاملة. لا يرسم شيئًا حين لا توجد بيانات، بدل عنوان فارغ.
- `DetailAdditionalInfoSection.kt`: صار القسم يُعرض بعد «تفاصيل الفيلم/المسلسل» مباشرة، حتى لو لم تكن هناك صفوف معلومات.
- منطقة المشاهدة: منطقة اللغة إن ذكرتها (`ar-SA`، `pt-BR`)، وإلا منطقة افتراضية للغة (`ar` → `SA`)، وإلا `US`. اختبارات: `WhereToWatchRegionTest.kt`.

**السحب لإعادة الترتيب وخانة «مجاني وقانوني» — نُفِّذا**
- `AddonsScreen.kt`: مقبض `DragHandle` في بطاقة كل مصدر؛ ضغط مطوّل ثم سحب يحرّك المصدر خانة واحدة كل 72 نقطة، عبر `moveAddon` الموجود، فبقي زرّا أعلى/أسفل كبديل وصول. `addons_drag_handle` في النصوص.
- `HomeCatalogDefinitions.kt`: كتالوجات مضيف `publicdomainmovies` تحمل الوسم المحلي «مجاني وقانوني · …» في عنوان الرف بدل اسم الإضافة وحدها، فلا تُخلط مع مصادر تحتاج مفاتيح أو تحمل خطر حقوق.


**اكتمال المتتبعات العامة والترتيب والملصقات — نُفِّذ**
- `PublicTrackers.kt` (جديد): قائمة announce عامة واحدة (أكثر من ثلاثين متتبعًا، بلا تكرار) يشاركها iOS وأندرويد، ودالة `withStreamTrackers` تضمّها إلى متتبعات المصدر وتزيل التكرار، فلا يبقى مصدران بقائمتين مختلفتين.
- `P2pStreamingEngine.ios.kt` و`P2pStreamingEngine.android.kt`: استبدال القائمتين المحليتين المتباعدتين (٢٠ في أندرويد و٣٦ مع تكرار في iOS) بالقائمة المشتركة، فصار كل تورنت يعلن للقائمة الكاملة لا لجزء منها.
- `StreamsRepository` و`PlayerStreamsRepository`: `sortedForGroupedDisplay` و`deduplicatedAcrossSources` كانا يطبّقان إزالة التكرار وترتيب الجودة داخل كل مصدر؛ وأضيف في `StreamsScreen.kt` ترتيب **رؤوس المصادر** نفسها بأقوى جودة لديها بدل الترتيب الأبجدي، فلا يظهر مصدر 480p فوق مصدر 4K.
- `StreamModels.kt`: `externalOpenUrl` صار يقبل `http` و`https` (صفحة ويب فقط)، و`externalOpenHost` يستخرج المضيف من المخططين، فلم تبق روابط «شاهد على الموقع» غير المشفّرة ميتة.
- `MetaHubArtwork.kt`: `metahubPosterUrl(imdbId)` بلا مفتاح، و`HomePosterCard.kt` يستخدمه حين يترك المصدر الملصق فارغًا، فالرفوف تُرسم بلا TMDB.
- `AddonModels.kt` + `AddonConfigDialog.kt`: حقل `config[]` الذي اسمه أو نوعه يدل على رمز/كلمة مرور (`token`, `apikey`, `password`, `secret`, `type: password`) يُعرض بحقل مخفي `SettingsSecretTextField` بدل حقل نص مكشوف.
- اختبارات: `PublicTrackersTest.kt` (الطول، شكل الرابط، عدم التكرار، الدمج) وإضافة حالة في `MetaHubArtworkTest.kt` و`AddonModelsTest.kt`.
