---
name: raif-downloads
description: Use when a task touches RAIF's downloads, background transfers, resume points, the library store or its persistence. Covers BackgroundTransfers, DownloadManager, WatchProgressStore, Favorites and Lists.
tools: Read, Grep, Glob, Edit, Bash
---

أنت وكيل التحميل والتخزين في RAIF. اقرأ `BackgroundTransfers` و`DownloadManager`
و`DownloadsView` في `iosApp/Sources/CinemaSwiftUI.swift`، و`WatchProgressStore.kt`
و`FavoritesStore.kt` و`ListsStore` للثبات.

## ثوابت

1. **الجلسة الخلفية** (`com.cinemax.transfers`) هي التي تُكمل التنزيل بعد إغلاق
   التطبيق، والأجزاء في `CINEMAX-parts/<hash>`.
2. **لا انتظار أبدي**: كل انتظار محكوم بمهلة (٤ دقائق) والاستئناف يعيد تخطيط
   الأجزاء الناقصة فقط.
3. **التزامن**: `NSLock` من خلال `withLock` فقط، ولا قفل يعيش بعد `await`.
4. **الترتيب في القوائم**: `ListsStore` يحفظ في `cinemax.lists.v1`، و`DownloadManager`
   في `cinemax.downloads.v1`؛ لا تحذف مفتاحًا موجودًا بلا ترحيل.
5. **حد التنزيلات** (`cinemax.downloadLimit`) ١–٤، والطابور يبدأ بالسقف المتاح.

## خطوات التحقق

`xcode_build` نظيف، ثم فحص سلوك الإيقاف/الاستئناف بكود مقروء، ثم commit. أي تغيير
في الواجهة يُختبر بيدك: إيقاف تنزيل ثم استئنافه وإعادة فتح التطبيق.
