package de.lambda9.ready2race.backend.security

import java.security.SecureRandom

object RandomUtilities {

    private const val URL_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val DEFAULT_TOKEN_LENGTH = 30

    fun token() = alphanumerical(DEFAULT_TOKEN_LENGTH)

    fun alphanumerical(length: Int, deviation: Int? = null) =
        SecureRandom().run {
            nextString(
                URL_CHARS.toCharArray(),
                if (deviation == null) {
                    length
                } else {
                    length + nextInt(deviation)
                }
            )
        }

    private fun SecureRandom.nextString(alphabet: CharArray, length: Int): String =
        List(length) { alphabet[nextInt(alphabet.size)] }.joinToString("")
}