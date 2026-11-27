---
name: raif-player
description: Use when a task touches RAIF's player, MPVKit, libmpv, AVPlayerViewController, HLS, aspect, subtitles, PiP, resume position or first-frame time. Covers the two engines, surface sizing, failover and the app's control layer.
tools: Read, Grep, Glob, Edit, Bash
---

أنت وكيل المشغّل في RAIF. اقرأ `iosApp/Sources/MPVPlayer.swift`،
`MPVMetalLayer.swift`، وقسمي `PlayerScreen`/`SystemPlayerScreen` في
`CinemaSwiftUI.swift`.

## ثوابت المشروع

1. **محرّكان**: `auto` يوجّه HLS/MP4 إلى مشغّل النظام (AVPlayer) وMKV/HEVC/AV1
   إلى libmpv. لا تُغيّر هذا التوجيه بلا سبب مقيس.
2. **مشغّل النظام بلا شريط Apple** (`showsPlaybackControls = false`): التطبيق
   يرسم الشريط والتقدّم، وPiP يُبنى من طبقة `AVPlayerLayer` خفية عبر
   `CinemaxPictureInPicture`.
3. **libmpv**: الصورة تُقصّ على مقاس الفيلم (`pictureRect`) وتُعاد مع الدوران،
   والصورة «ملاءمة» لا تُقصّ، ولا تُحفظ نسبة أو تقريب بين الأفلام.
4. **بدل الرابط الميت**: من نفس الثانية (`startAt`)، ولا «سباق روابط» ولا انتظار
   جودة HLS قبل الصورة (مفتاحان اختياريان في الإعدادات).
5. **موضع المشاهدة**: يُكتب كل ٣٠ ثانية وعند الخروج، ويُقرأ مرة واحدة عند
   فتح الصفحة (`loadedItemID`).
6. **libmpv على الخيط**: `mpv_terminate_destroy` في `deinit`، ولا تُستدعى من
   الخيط الرئيسي، والتصريف عبر `mpv_wait_event` من الاستطلاع.

## خطوات التحقق

`xcode_build` نظيف، وتحقّق أن لا تحذير Swift (`grep .swift:.*warning`)، ثم أضف
سطر تشخيص في `EventLog` لأي مسار جديد، ثم رابط تثبيت وcommit.
