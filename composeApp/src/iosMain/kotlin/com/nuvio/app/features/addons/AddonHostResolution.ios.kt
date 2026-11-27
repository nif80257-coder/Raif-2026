@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.addons

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_UNSPEC
import platform.posix.NI_NUMERICHOST
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getnameinfo

/** NI_MAXHOST on Darwin; the header only exposes it as a macro, so it is named here. */
private const val HOST_BUFFER_LENGTH = 1025

/**
 * Resolves [host] through the system resolver off the main thread, returning the addresses in the
 * numeric form `getnameinfo` reports so the caller can apply the same policy as a literal address.
 */
internal actual suspend fun resolveHostAddresses(host: String): List<String> =
    withContext(Dispatchers.Default) {
        memScoped {
            val hints = alloc<addrinfo>()
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_STREAM
            val result = alloc<CPointerVar<addrinfo>>()
            if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) {
                return@memScoped emptyList<String>()
            }
            val head: CPointer<addrinfo> = result.value ?: return@memScoped emptyList<String>()
            try {
                buildList {
                    var current: CPointer<addrinfo>? = head
                    while (current != null) {
                        val entry = current.pointed
                        val buffer = ByteArray(HOST_BUFFER_LENGTH)
                        val formatted = getnameinfo(
                            entry.ai_addr,
                            entry.ai_addrlen,
                            buffer.refTo(0),
                            HOST_BUFFER_LENGTH.convert(),
                            null,
                            0u,
                            NI_NUMERICHOST,
                        )
                        if (formatted == 0) {
                            val terminator = buffer.indexOf(0)
                            val end = if (terminator < 0) buffer.size else terminator
                            if (end > 0) add(buffer.decodeToString(0, end))
                        }
                        current = entry.ai_next
                    }
                }
            } finally {
                freeaddrinfo(head)
            }
        }
    }
