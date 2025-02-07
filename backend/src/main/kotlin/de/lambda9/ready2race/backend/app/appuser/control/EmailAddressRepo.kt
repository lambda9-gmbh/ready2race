package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ADDRESS
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object EmailAddressRepo {

    fun exists(
        email: String,
    ): JIO<Boolean> = Jooq.query {
        with(EMAIL_ADDRESS) {
            fetchExists(this, EMAIL.equalIgnoreCase(email))
        }
    }

}