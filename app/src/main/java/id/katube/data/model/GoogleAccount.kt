package id.katube.data.model

/**
 * Model data sesi akun Google / YouTube.
 */
data class GoogleAccount(
    val email: String = "",
    val displayName: String = "",
    val handle: String? = null,
    val avatarUrl: String? = null,
    val cookies: String = "",
    val isLoggedIn: Boolean = false,
)
