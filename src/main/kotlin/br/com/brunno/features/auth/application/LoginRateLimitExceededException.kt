package br.com.brunno.features.auth.application

/** Transport-neutral rate-limit rejection; never contains an email or address. */
class LoginRateLimitExceededException(val retryAfterSeconds: Long) :
    RuntimeException("Too many login attempts") {
    init {
        require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
    }
}
