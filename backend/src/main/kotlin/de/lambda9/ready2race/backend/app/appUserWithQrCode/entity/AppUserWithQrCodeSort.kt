package de.lambda9.ready2race.backend.app.appUserWithQrCode.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_QR_CODE_FOR_EVENT
import org.jooq.Field

enum class AppUserWithQrCodeSort : Sortable {
    FIRSTNAME, LASTNAME, EMAIL, CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        FIRSTNAME -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.FIRSTNAME)
        LASTNAME -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.LASTNAME)
        EMAIL -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.EMAIL)
        CREATED_AT -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.CREATED_AT)
    }
}