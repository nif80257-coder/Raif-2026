package com.cinemax.app

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun readBundledText(name: String, extension: String): String? {
    val path = NSBundle.mainBundle.pathForResource(name, ofType = extension) ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return null
    val raw = data.bytes ?: return null
    return raw.reinterpret<ByteVar>().readBytes(length).decodeToString()
}
