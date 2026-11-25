package com.nuvio.app.core.storage

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.*
import platform.Security.*

/**
 * The Keychain, one generic-password item per name.
 *
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` is the right class for a key the app reads
 * while playing: it survives a reboot without being unlocked, it is not restored onto another
 * device from a backup, and it never leaves this phone. Every failure is logged and turned into a
 * null read rather than a crash — a keychain that refuses is the user's key to re-enter, not a
 * reason for the settings screen to fail.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object SecretStore {
    private val log = Logger.withTag("SecretStore")

    actual fun read(name: String): String? = query(name) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            if (status != errSecSuccess) {
                log.w { "Keychain read refused (OSStatus $status)" }
                return@memScoped null
            }
            val data: CFDataRef = result.value?.reinterpret() ?: return@memScoped null
            try {
                val bytes = CFDataGetBytePtr(data) ?: return@memScoped null
                ByteArray(CFDataGetLength(data).toInt()) { bytes[it].toByte() }.decodeToString()
            } finally {
                CFRelease(data)
            }
        }
    }

    actual fun write(name: String, value: String?) = query(name) { query ->
        if (value == null) {
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                log.w { "Keychain delete refused (OSStatus $status)" }
            }
            return@query
        }
        val bytes = value.encodeToByteArray().toUByteArray()
        if (bytes.isEmpty()) return@query
        val data = CFDataCreate(null, bytes.refTo(0), bytes.size.toLong()) ?: return@query
        val attributes = dictionary()
        try {
            CFDictionarySetValue(attributes, kSecValueData, data)
            val update = SecItemUpdate(query, attributes)
            if (update == errSecItemNotFound) {
                CFDictionarySetValue(query, kSecValueData, data)
                CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                val added = SecItemAdd(query, null)
                if (added != errSecSuccess) log.w { "Keychain add refused (OSStatus $added)" }
            } else if (update != errSecSuccess) {
                log.w { "Keychain update refused (OSStatus $update)" }
            }
        } finally {
            CFRelease(attributes)
            CFRelease(data)
        }
    }

    private fun dictionary(): CFMutableDictionaryRef =
        CFDictionaryCreateMutable(null, 0L, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            ?: error("Unable to create a Keychain query")

    private inline fun <T> query(name: String, block: (CFMutableDictionaryRef) -> T): T {
        val service = CFStringCreateWithCString(null, "com.raif.stream.secrets", kCFStringEncodingUTF8)
            ?: error("Unable to encode the Keychain service")
        val account = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8)
        val query = dictionary()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            account?.let { CFDictionarySetValue(query, kSecAttrAccount, it) }
            return block(query)
        } finally {
            CFRelease(query)
            account?.let { CFRelease(it) }
            CFRelease(service)
        }
    }
}
