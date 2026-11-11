import Foundation
import Security

/// مخزن مفاتيح محلي باستخدام Keychain فقط.
/// لا تُضمّن مفاتيح مزوّدي الخدمة داخل التطبيق؛ مرّرها من خادم آمن أو خزّنها
/// محلياً بعد إدخال المستخدم عبر `set(key:value:)`.
final class ApiKeyManager {
    static let shared = ApiKeyManager()

    private init() {}

    private var service: String {
        Bundle.main.bundleIdentifier ?? "com.raif.apiKeys"
    }

    func get(key: String) -> String? {
        guard !key.isEmpty else { return nil }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func has(key: String) -> Bool {
        get(key: key) != nil
    }

    @discardableResult
    func set(key: String, value: String) -> Bool {
        guard !key.isEmpty, let data = value.data(using: .utf8) else { return false }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let lookup: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: key
            ]
            return SecItemUpdate(lookup as CFDictionary, [kSecValueData as String: data] as CFDictionary) == errSecSuccess
        }
        return status == errSecSuccess
    }

    @discardableResult
    func remove(key: String) -> Bool {
        guard !key.isEmpty else { return false }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
