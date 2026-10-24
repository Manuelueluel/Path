package it.traccia.core.common

/** Domain-level error categories shared by all modules. */
sealed interface AppError {
    val cause: Throwable?

    data class Network(
        override val cause: Throwable? = null,
    ) : AppError

    data class Storage(
        override val cause: Throwable? = null,
    ) : AppError

    data class NotFound(
        val what: String,
        override val cause: Throwable? = null,
    ) : AppError

    data class Unexpected(
        override val cause: Throwable? = null,
    ) : AppError
}
