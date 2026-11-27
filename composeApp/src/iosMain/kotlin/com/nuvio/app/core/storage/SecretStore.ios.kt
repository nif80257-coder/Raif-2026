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

    actual fun write(name: String, value: String?) {
        writeSecret(name, value)
    }

    actual fun writeChecked(name: String, value: String?): Boolean = writeSecret(name, value)

    private fun writeSecret(name: String, value: String?): Boolean = query(name) { query ->
        if (value == null) {
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                log.w { "Keychain delete refused (OSStatus $status)" }
            }
            return@query status == errSecSuccess || status == errSecItemNotFound
        }
        val bytes = value.encodeToByteArray().toUByteArray()
        if (bytes.isEmpty()) return@query false
        val data = CFDataCreate(null, bytes.refTo(0), bytes.size.toLong()) ?: return@query false
        val attributes = dictionary()
        try {
            CFDictionarySetValue(attributes, kSecValueData, data)
            when (val update = SecItemUpdate(query, attributes)) {
                errSecSuccess -> true
                errSecItemNotFound -> {
                    val addQuery = CFDictionaryCreateMutableCopy(null, 0, query)
                    if (addQuery == null) {
                        log.w { "Keychain add query could not be created" }
                        false
                    } else {
                        try {
                            CFDictionarySetValue(addQuery, kSecValueData, data)
                            CFDictionarySetValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                            when (val added = SecItemAdd(addQuery, null)) {
                                errSecSuccess -> true
                                errSecDuplicateItem -> SecItemUpdate(query, attributes) == errSecSuccess
                                else -> {
                                    log.w { "Keychain add refused (OSStatus $added)" }
                                    false
                                }
                            }
                        } finally {
                            CFRelease(addQuery)
                        }
                    }
                }
                else -> {
                    log.w { "Keychain update refused (OSStatus $update)" }
                    false
                }
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
        require(name.isNotEmpty() && '\u0000' !in name) { "Invalid Keychain account name" }
        val service = CFStringCreateWithCString(null, "com.raif.stream.secrets", kCFStringEncodingUTF8)
            ?: error("Unable to encode the Keychain service")
        try {
            val account = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8)
                ?: error("Unable to encode the Keychain account")
            try {
                val query = dictionary()
                try {
                    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                    CFDictionarySetValue(query, kSecAttrService, service)
                    CFDictionarySetValue(query, kSecAttrAccount, account)
                    return block(query)
                } finally {
                    CFRelease(query)
                }
            } finally {
                CFRelease(account)
            }
        } finally {
            CFRelease(service)
        }
    }
}
