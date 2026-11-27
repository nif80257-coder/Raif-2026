# RAIF agents

هذه ملفات تعريف وكلاء (subagents) لمشروع RAIF/CINEMAX. يقرأها
**Claude Code** من `.claude/agents/`؛ تطبيق FreeMilk لا يشغّلها بنفسه،
فهو لا يوفّر أداة إنشاء وكلاء — بل يقرأ المهارات من `.claude/skills`
ويستخدم هذا الملف كمرجع للصلاحيات والحدود.

## الخريطة: الأولويات الـ١٤ ← المهارات/الوكيل

| # | التخصص | المهارات المثبّتة | الوكيل |
|---|---|---|---|
| 1 | GitHub Repository Analysis | **ناقص** (لا مهارة GitHub في المجموعة) | `raif-review` |
| 2 | Stremio Addon Development | `plugin-sandbox-validation`، `source-plugin-resilience` (جزئي) | `raif-sources` |
| 3 | API Integration & Web Scraping | `ios-networking`، `kmp-ktor`، `ktor-client-performance` | `raif-sources` |
| 4 | Video Streaming (HLS/DASH/HTTP) | `avkit`، `avfoundation-hls-pip`، `video-instant-startup`، `stream-cache-expiry` | `raif-player` |
| 5 | MPVKit & FFmpeg | `libmpv-ffmpeg-ios`، `subtitle-sync-parsing` | `raif-player` |
| 6 | Download Manager | `background-processing` (جزئي) | `raif-downloads` |
| 7 | iOS Native (Swift/SwiftUI) | `swiftui-pro`، `swiftui-patterns`، `swift-concurrency`، `uikit-expert` | `raif-player` |
| 8 | Kotlin Multiplatform & Compose | `kmp-ktor`، `kmp-network-layer` (شبكة فقط، لا Compose) | `raif-sources` |
| 9 | Automated QA & Testing | `swift-testing`، `test-modernizer`، `swiftdata-testing` | `raif-qa` |
| 10 | Security Engineering | `swift-security`، `authentication`، `cryptokit`، `device-integrity` | `raif-security` |
| 11 | Database Engineering | `swiftdata-pro`، `swiftdata-migration` (لا SQL) | `raif-downloads` |
| 12 | Performance Profiling | `swiftui-performance`، `swiftui-performance-audit`، `metrickit`، `ios-memgraph-analysis` | `raif-qa` |
| 13 | CI/CD & GitHub Actions | **ناقص** (`.github/workflows` موجودة، بلا مهارة) | `raif-review` |
| 14 | AI Code Review | `ios-code-audit`، `impeccable`، `anti-ui-slop` | `raif-review` |

## حدود ثابتة لكل وكيل

- **لا صلاحيات كتابة خارج `iosApp/Sources` و`composeApp/src`** إلا لملفات
  الوثائق والوكلاء أنفسها.
- **لا دفع إلى GitHub** ولا commit بلا طلب صريح.
- **لا تعديل `project.pbxproj` يدويًا** لإضافة ملفات (ملف المصادر داخل مجلد
  التطبيق يُضاف تلقائيًا)، والملفات غير المعروفة تُنقل إلى ملف مُترجم قائم.
- كل تغيير ينتهي بـ`xcode_build` ناجح، ثم رابط تثبيت جديد، ثم commit.
- لا يُعلن «صفر أخطاء» بلا دليل على الجهاز أو بناء نظيف.
