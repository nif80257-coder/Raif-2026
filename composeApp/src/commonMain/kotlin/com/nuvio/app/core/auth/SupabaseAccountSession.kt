package com.nuvio.app.core.auth

internal fun currentSupabaseAccountUserId(): String? =
    (AuthRepository.state.value as? AuthState.Authenticated)
        ?.takeIf { authenticated -> !authenticated.isAnonymous }
        ?.userId
        ?.takeIf(String::isNotBlank)

internal fun isCurrentSupabaseAccount(userId: String): Boolean =
    currentSupabaseAccountUserId() == userId

internal fun requireSupabaseAccountUserId(): String =
    currentSupabaseAccountUserId()
        ?: throw IllegalStateException("A signed-in account is required to sync this data.")
