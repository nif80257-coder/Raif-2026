# RAIF — مراجعة وتحسين

## ما تم إصلاحه

- استبدال شاشة `Hello, world!` بواجهة RAIF عربية RTL تعرض متابعة المشاهدة، مختارات، البحث، التنزيلات وقائمة المشاهدة.
- إضافة `accessibilityIdentifier` و`accessibilityLabel` ثابتة للعناصر الأساسية لتسهيل Appium/XCUITest.
- حذف `ENCRYPTED_KEYS.plist` من حزمة التطبيق. أي سر داخل تطبيق iOS يمكن استخراجه؛ التشفير بمفتاح مشتق من اسم المشروع كان إخفاءً وليس حماية.
- إعادة كتابة `ApiKeyManager` ليستخدم Keychain فقط، مع `WhenUnlockedThisDeviceOnly`، وتحديث آمن للقيم الموجودة.
- إضافة اختبارات UI لمسار التشغيل والعناصر الرئيسية.

## ملاحظة IPA

بيئة المراجعة الحالية Linux ولا تحتوي Xcode أو `xcodebuild`، لذلك لا يمكن إنتاج IPA فعلياً هنا. كما أن IPA غير الموقّع لا يُثبّت على جهاز iPhone. لإخراج IPA قابل للتثبيت يجب تشغيل سكربت البناء على macOS مع:

1. Xcode وإصدار iOS SDK مناسب.
2. تثبيت submodule `MPVKit` وتشغيل `scripts/prepare-ios-dependencies.sh`.
3. Team وBundle ID وشهادة توزيع وProvisioning Profile.
4. إنشاء Archive ثم Export عبر Xcode أو `xcodebuild -exportArchive`.

الأمر الحالي للنسخة غير الموقعة (للاختبار/الفحص فقط):

```bash
./scripts/build-ios-ipa.sh 1.0.1
```

ولنسخة قابلة للتوزيع، استخدم Archive موقّعاً من Xcode أو عدّل إعدادات التوقيع في بيئة macOS بدلاً من تعطيل `CODE_SIGNING_ALLOWED`.

## إضافة غرفة المشاهدة عبر المتصفح

تمت إضافة مسار Web Watch Party إلى `NuvioMobile/web/party/index.html` ونسخه إلى `RaifPartyWeb/index.html`:

- دخول الضيف من رابط أو رمز بدون تثبيت التطبيق.
- إنشاء غرفة من المتصفح للمضيف.
- إدخال رابط بث مسموح ومشاركته مع أعضاء الغرفة.
- مزامنة التشغيل والإيقاف والتقديم والدردشة عبر Supabase Realtime.
- إبقاء تطبيق RAIF خياراً للمصادر التي لا يدعمها المتصفح.
