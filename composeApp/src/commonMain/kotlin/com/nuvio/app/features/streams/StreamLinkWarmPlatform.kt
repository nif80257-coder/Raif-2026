package com.nuvio.app.features.streams

/**
 * Opens [url] with the request headers the player itself would send, asks for the first
 * [maxBytes] bytes only and lets go of the connection.
 *
 * Returns true when a byte arrived, which means the host was reached: the name was resolved,
 * the connection was opened, every redirect was followed and the server started answering.
 * Nothing of the body is kept — this is not a download, it is the wait before one.
 */
internal expect suspend fun warmStreamLinkConnection(
    url: String,
    headers: Map<String, String>,
    maxBytes: Int,
): Boolean
