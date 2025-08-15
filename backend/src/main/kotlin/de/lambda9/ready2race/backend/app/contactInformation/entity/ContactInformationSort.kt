package de.lambda9.ready2race.backend.app.contactInformation.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION
import org.jooq.Field

enum class ContactInformationSort : Sortable {
    ID,
    NAME,
    ADDRESS_ZIP,
    ADDRESS_CITY,
    ADDRESS_STREET,
    EMAIL;

    override fun toFields(): List<Field<*>> = when(this) {
        ID -> listOf(CONTACT_INFORMATION.ID)
        NAME -> listOf(CONTACT_INFORMATION.NAME)
        ADDRESS_ZIP -> listOf(CONTACT_INFORMATION.ADDRESS_ZIP)
        ADDRESS_CITY -> listOf(CONTACT_INFORMATION.ADDRESS_CITY)
        ADDRESS_STREET -> listOf(CONTACT_INFORMATION.ADDRESS_STREET)
        EMAIL -> listOf(CONTACT_INFORMATION.EMAIL)
    }
}