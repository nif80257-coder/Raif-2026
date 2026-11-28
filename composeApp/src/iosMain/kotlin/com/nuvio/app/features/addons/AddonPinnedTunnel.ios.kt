@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.AF_UNSPEC
import platform.posix.AI_NUMERICHOST
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.accept
import platform.posix.addrinfo
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.timeval

private val tunnelLog = Logger.withTag("AddonPinnedTunnel")

/** The loopback address in the order a socket address stores it: 127.0.0.1, byte-swapped. */
private const val LOOPBACK_ADDRESS = 0x0100007Fu

/** `htons` is a macro on Darwin and is not bound here, so the swap is written out. */
private fun bigEndianShort(value: Int): UShort =
    (((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)).toUShort()

/**
 * A loopback proxy that puts the address on the connection itself.
 *
 * Every add-on request is checked against the addresses its name answers with, but the transport
 * resolves the name again when it connects, so a host could answer public while it was inspected and
 * private when the socket opened. Here the app owns the connection: NSURLSession is pointed at this
 * proxy for a TLS request, the proxy reads the `CONNECT host:port` line, resolves the name itself,
 * refuses it unless *every* answer is public, and opens the socket to exactly that address. The TLS
 * handshake still runs end to end between the session and the server, with the real host name in the
 * handshake and the certificate checked against it, so nothing about the connection's security is
 * re-implemented here — only the address it is made to.
 *
 * Only a TLS tunnel is carried: a plain request is refused rather than rewritten, because that would
 * need a second parser in a path every add-on depends on. A proxy that cannot listen leaves its port
 * at null and the session keeps the resolver it had; the weaker check is not replaced by a bypass.
 */
internal object AddonPinnedTunnel {
    /** A port high enough to stay clear of the system services and of the app's own sockets. */
    private const val LISTEN_PORT = 48_721
    private const val HEAD_LIMIT_BYTES = 8 * 1024
    private const val READ_TIMEOUT_SECONDS = 30
    private const val RELAY_BUFFER_BYTES = 16 * 1024

    /**
     * A tunnel holds one thread in each direction while it relays, so this is its own pool:
     * parking a handful of threads must never be what starves the rest of the app's coroutines.
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val tunnelDispatcher = kotlinx.coroutines.newFixedThreadPoolContext(
        name = "addon-pinned-tunnel",
        nThreads = 32,
    )
    private val scope = CoroutineScope(SupervisorJob() + tunnelDispatcher)
    private var started = false
    private var listenPort = 0

    /** The port the proxy listens on, or null when it could not start. */
    fun portOrNull(): Int? {
        start()
        return listenPort.takeIf { it > 0 }
    }

    private fun start() {
        if (started) return
        started = true
        val listener = openListener() ?: return
        listenPort = LISTEN_PORT
        scope.launch {
            while (true) {
                val client = accept(listener, null, null)
                if (client < 0) {
                    delay(50)
                    continue
                }
                scope.launch { serve(client) }
            }
        }
    }

    private fun openListener(): Int? {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return null
        val bound = memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_len = sizeOf<sockaddr_in>().convert()
            address.sin_family = AF_INET.convert()
            address.sin_port = bigEndianShort(LISTEN_PORT)
            address.sin_addr.s_addr = LOOPBACK_ADDRESS
            bind(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
        }
        if (!bound || listen(fd, 64) != 0) {
            close(fd)
            tunnelLog.w { "Pinned tunnel could not listen; add-on requests keep their resolver" }
            return null
        }
        return fd
    }

    private suspend fun serve(client: Int) {
        var upstream = -1
        try {
            setReceiveTimeout(client)
            val head = readHead(client)
            val target = head?.let { text ->
                parseAddonTunnelTarget(text.substringBefore("\r\n"))
            }
            if (target == null) {
                respond(client, "400 Bad Request")
                return
            }
            val address = chooseAddonTunnelAddress(resolveHostAddresses(target.host))
            if (address == null) {
                tunnelLog.w { "Pinned tunnel refused ${target.authority}: no public address" }
                respond(client, "403 Forbidden")
                return
            }
            upstream = connectTo(address, target.port)
            if (upstream < 0) {
                respond(client, "502 Bad Gateway")
                return
            }
            setReceiveTimeout(upstream)
            if (!writeAll(client, "HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray())) {
                return
            }
            coroutineScope {
                launch { relay(client, upstream) }
                launch { relay(upstream, client) }
            }
        } catch (error: Throwable) {
            tunnelLog.w { "Pinned tunnel failed: ${error.message}" }
        } finally {
            closeQuietly(upstream)
            closeQuietly(client)
        }
    }

    private fun relay(from: Int, to: Int) {
        val buffer = ByteArray(RELAY_BUFFER_BYTES)
        while (true) {
            val count = buffer.usePinned { pinned ->
                recv(from, pinned.addressOf(0), buffer.size.convert(), 0).toInt()
            }
            if (count <= 0) return
            if (!writeAll(to, buffer, count)) return
        }
    }

    private fun connectTo(address: String, port: Int): Int = memScoped {
        val hints = alloc<addrinfo>()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        hints.ai_flags = AI_NUMERICHOST
        val result = alloc<kotlinx.cinterop.CPointerVar<addrinfo>>()
        if (getaddrinfo(address, port.toString(), hints.ptr, result.ptr) != 0) return@memScoped -1
        val head = result.value ?: return@memScoped -1
        try {
            val entry = head.pointed
            val fd = socket(entry.ai_family, entry.ai_socktype, entry.ai_protocol)
            if (fd < 0) return@memScoped -1
            if (connect(fd, entry.ai_addr, entry.ai_addrlen) != 0) {
                close(fd)
                return@memScoped -1
            }
            fd
        } finally {
            freeaddrinfo(head)
        }
    }

    private fun readHead(client: Int): String? {
        val buffer = ByteArray(HEAD_LIMIT_BYTES)
        var total = 0
        while (total < buffer.size) {
            val count = buffer.usePinned { pinned ->
                recv(client, pinned.addressOf(total), (buffer.size - total).convert(), 0).toInt()
            }
            if (count <= 0) return null
            total += count
            val text = buffer.decodeToString(0, total)
            if (text.contains("\r\n\r\n")) return text
        }
        return null
    }

    private fun respond(client: Int, status: String) {
        writeAll(client, "HTTP/1.1 $status\r\nContent-Length: 0\r\n\r\n".encodeToByteArray())
    }

    private fun writeAll(fd: Int, bytes: ByteArray, count: Int = bytes.size): Boolean {
        var written = 0
        while (written < count) {
            val sent = bytes.usePinned { pinned ->
                send(fd, pinned.addressOf(written), (count - written).convert(), 0).toInt()
            }
            if (sent <= 0) return false
            written += sent
        }
        return true
    }

    private fun setReceiveTimeout(fd: Int) {
        memScoped {
            val timeout = alloc<timeval>()
            timeout.tv_sec = READ_TIMEOUT_SECONDS.convert()
            timeout.tv_usec = 0
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
        }
    }

    private fun closeQuietly(fd: Int) {
        if (fd >= 0) close(fd)
    }
}
