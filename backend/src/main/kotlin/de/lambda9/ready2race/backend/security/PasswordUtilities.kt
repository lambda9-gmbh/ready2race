package de.lambda9.ready2race.backend.security

import de.lambda9.ready2race.backend.toBase64
import org.mindrot.jbcrypt.BCrypt
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object PasswordUtilities {

    private val sha256Instance = MessageDigest.getInstance("SHA-256")

    fun hash(plain: String, pepper: String): String =
        BCrypt.hashpw(preHash(plain, pepper), BCrypt.gensalt(10))

    fun check(plain: String, passwort: String, pepper: String): Boolean =
        BCrypt.checkpw(preHash(plain, pepper), passwort)

    fun generate(): String =
        RandomUtilities.alphanumerical(10, fixLength = false)

    private fun preHash(plain: String, pepper: String): String {
        val pepperedInput = "$pepper$plain".toByteArray(StandardCharsets.UTF_8)
        val result = sha256Instance.digest(pepperedInput)
        return result.toBase64()
    }

}