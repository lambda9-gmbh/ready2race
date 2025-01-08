package de.lambda9.ready2race.backend.security

import com.password4j.Password
import de.lambda9.ready2race.backend.app.App
import de.lambda9.tailwind.core.KIO

object PasswordUtilities {

    fun hash(plain: String): App<Nothing, String> = KIO.access { env ->

        Password
            .hash(plain)
            .addRandomSalt()
            .addPepper(env.env.config.security.pepper)
            .withArgon2()
            .result
    }

    fun check(plain: String, password: String): App<Nothing, Boolean> = KIO.access { env ->

         Password
             .check(plain, password)
             .addPepper(env.env.config.security.pepper)
             .withArgon2()
    }

    fun generate(): String =
        RandomUtilities.alphanumerical(10, fixLength = false)

}