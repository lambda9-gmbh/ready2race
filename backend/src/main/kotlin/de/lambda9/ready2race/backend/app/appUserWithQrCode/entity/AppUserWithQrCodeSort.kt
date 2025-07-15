package de.lambda9.ready2race.backend.app.appUserWithQrCode.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_WITH_QR_CODE_FOR_EVENT
import org.jooq.Field

enum class AppUserWithQrCodeSort : Sortable {
    ID, FIRSTNAME, LASTNAME, EMAIL, QR_CODE_ID, CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.ID)
        FIRSTNAME -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.FIRSTNAME)
        LASTNAME -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.LASTNAME)
        EMAIL -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.EMAIL)
        QR_CODE_ID -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.QR_CODE_ID)
        CREATED_AT -> listOf(APP_USER_WITH_QR_CODE_FOR_EVENT.CREATED_AT)
    }
}