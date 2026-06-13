package com.myuntis.app.data.network

// =============================================================
// NETWORK RESULT - Sealed Class
// =============================================================
// A sealed class is like an enum but each variant can hold
// different data. This is the standard Kotlin pattern for
// handling API responses with three possible states.
//
// Usage:
//   when (result) {
//       is NetworkResult.Success -> showData(result.data)
//       is NetworkResult.Error   -> showError(result.message)
//       is NetworkResult.Loading -> showSpinner()
//   }
// =============================================================
sealed class NetworkResult<out T> {

    // Loading: request is in progress, no data yet
    object Loading : NetworkResult<Nothing>()

    // Success: request completed, data is available
    // 'out T' means T is covariant (read-only), safer for generics
    data class Success<T>(val data: T) : NetworkResult<T>()

    // Error: request failed, contains error message and optional HTTP code
    data class Error(
        val message: String,
        val code: Int? = null           // HTTP status code (404, 401, etc.)
    ) : NetworkResult<Nothing>()
}

// =============================================================
// EXTENSION FUNCTIONS
// =============================================================
// These make working with NetworkResult more convenient.
// Instead of writing 'is NetworkResult.Success', just use
// result.isSuccess or result.dataOrNull
// =============================================================

// Returns true only when the result is a Success
val <T> NetworkResult<T>.isSuccess: Boolean
    get() = this is NetworkResult.Success

// Returns the data if Success, null otherwise
// Safe way to access data without crashing
val <T> NetworkResult<T>.dataOrNull: T?
    get() = (this as? NetworkResult.Success)?.data

// Returns the error message if Error, null otherwise
val <T> NetworkResult<T>.errorMessage: String?
    get() = (this as? NetworkResult.Error)?.message

// Transform Success data to a different type (like map() for lists)
// Example: NetworkResult<ApiUser>.map { it.toDomain() }
inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
        is NetworkResult.Loading -> NetworkResult.Loading
        is NetworkResult.Success -> NetworkResult.Success(transform(data))
        is NetworkResult.Error -> NetworkResult.Error(message, code)
    }
}