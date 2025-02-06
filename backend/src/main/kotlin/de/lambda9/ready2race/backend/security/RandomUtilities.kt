package de.lambda9.ready2race.backend.security

import java.security.SecureRandom

object RandomUtilities {

    private const val URL_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val DEFAULT_LENGTH = 30

    fun alphanumerical(length: Int = DEFAULT_LENGTH, fixLength: Boolean = true) =
        SecureRandom().run {
            nextString(
                URL_CHARS.toCharArray(),
                if (fixLength) {
                    length
                } else {
                    length + nextInt(4)
                }
            )
        }

    private fun SecureRandom.nextString(alphabet: CharArray, length: Int): String =
        List(length) { alphabet[nextInt(alphabet.size)] }.joinToString("")
}