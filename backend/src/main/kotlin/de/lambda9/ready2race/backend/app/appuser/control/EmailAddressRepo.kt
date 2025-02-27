package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ADDRESS

object EmailAddressRepo {

    fun exists(email: String) = EMAIL_ADDRESS.exists { EMAIL.equalIgnoreCase(email) }

}