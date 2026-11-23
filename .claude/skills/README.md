# مهارات هذا المشروع — دليل الاستخدام

هذا المجلد هو المكان الذي يقرأ منه التطبيق مهارات المشروع (`.claude/skills`).
كل مجلد يحوي `SKILL.md` بمقدمة YAML فيها `name` و`description`.

## متى تُستخدم كل مهارة في CINEMAX

| المهارة | تُستخدم عند |
|---|---|
| `swiftui-pro` | مراجعة أي كود SwiftUI، الأداء، الهيجين، مراجعة ما قبل الإصدار |
| `swift-concurrency` | `async/await`، `@MainActor`، `Task`، التسريبات، Swift 6 |
| `swiftui-animation` | انتقالات المشغّل، الحركة، احترام Reduce Motion |
| `swiftui-navigation` | `NavigationStack`، الشيتات، التبويبات، الروابط العميقة |
| `swiftui-layout-components` | القوائم، الشبكات، `ScrollView`، النماذج، شاشات الإعدادات |
| `swiftui-uikit-interop` | `UIViewRepresentable` حول mpv/Metal، ودمج UIKit في SwiftUI |
| `swiftui-iphone-duo` | تغيّر مقاسات النافذة والشاشات القابلة للطي |
| `anti-ui-slop` | تدقيق جودة الواجهة قبل الشحن |
| `find-skills` | البحث عن مهارة إضافية |
| `xcode-project-setup` | أي عمل على `project.pbxproj`: حزم SPM، ربط ملفات، قواعد Xcode الحديثة |
| `swiftui-performance-audit` | تدقيق أداء SwiftUI من الكود: `body` الثقيل، `ForEach`، الهوية، التمرير |
| `swiftui-view-refactor` | تقسيم الملفات الضخمة إلى مشاهد فرعية ونماذج، بلا تغيير سلوك |
| `app-intents` | App Intents كاملًا (نسخة n0an، ١٧ مرجعًا): Siri AI 27.3، Spotlight، الاختصارات، الويدجت، الاختبار |
| `swiftdata` | SwiftData: النماذج والوراثة والاستعلامات وتخزين أسرع من `UserDefaults` |
| `kmp-ktor` | عميل Ktor في Kotlin Multiplatform لطبقة المصادر: مهلات، إعادة محاولة، بث |
| `kmp-network-layer` | طبقة شبكة كاملة في KMP: محركات المنصات، تجديد التوتر، خرائط الأخطاء |
| `avkit` | AVKit/AVPlayer: صورة داخل صورة، AirPlay، معاينة الفيديو، ترجمة ووسائط |

مهارات مفعّلة على مستوى التطبيق (خارج هذا المجلد) وتخص هذا المشروع:
`addon-manifest-engine` · `stream-fetching-parser` · `swift-swiftui-ios-engineer` ·
`tmdb-metadata-catalog` · `watchlist-trakt-sync` · `apple-ios-hig-designer` ·
`apple-ondevice-ai-integration` · `ios-appstore-release-qa`

## قواعد مكتسبة من هذا الكود

1. ملفات Swift تُسجَّل **صراحةً** في `iosApp/CINEMAX.xcodeproj/project.pbxproj`،
   والهدف **ليس** مجلدًا متزامنًا بعد، فملف `.swift` جديد لا يدخل الهدف ويُفشل
   البناء بـ`cannot find in scope`. مهارة `xcode-project-setup` تعالج هذا بالتحويل
   إلى PBXFileSystemSynchronizedRootGroup (المشروع بصيغة objectVersion 77 فالتحويل
   مدعوم)، وهو تغيير واحد على المشروع يلغي الحاجة لتعديله بعد ذلك.
2. طبقة البيانات في Kotlin (`AppleBridge`) والواجهة SwiftUI فقط؛ لا نماذج Kotlin في Swift.
3. كل مصدر يُعرض للمشاهد: لا حذف صامت لرابط بلا ملف — يُعرض كصفحة أو يُشرح سببه.
4. رفع رقم البناء في `project.yml` و`project.pbxproj` قبل كل رابط تثبيت.

## مهارات مطلوبة (لم تُضف بعد)

| الاسم المطلوب | لماذا |
|---|---|
| `avfoundation-hls-pip` | صورة داخل صورة، ABR حقيقي، واستعادة أخطاء AVPlayer بجانب mpv |
| `libmpv-ffmpeg-ios` | ضبط libmpv والـFFmpeg على iOS والصورة داخل صورة لـmpv |
| `swiftui-liquid-glass` | استخدام زجاج iOS 26 الأصلي في الأزرار والشريط العائم |

مهارات من `openai/plugins/build-ios-apps` لم تُضف لأنها تحتاج ماكًا ومحاكيًا
وأدوات Instruments (`ios-ettrace-performance`، `ios-memgraph-leaks`،
`ios-simulator-browser`، `ios-debugger-agent`)، أو لأنها تكرر مهارات موجودة
(`swiftui-ui-patterns`).
