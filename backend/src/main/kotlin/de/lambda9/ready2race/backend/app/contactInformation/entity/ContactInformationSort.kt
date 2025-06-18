package de.lambda9.ready2race.backend.app.contactInformation.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.CONTACT_INFORMATION
import org.jooq.Field

enum class ContactInformationSort : Sortable {
    ID,
    NAME;

    override fun toFields(): List<Field<*>> = when(this) {
        ContactInformationSort.ID -> listOf(CONTACT_INFORMATION.ID)
        ContactInformationSort.NAME -> listOf(CONTACT_INFORMATION.NAME)
    }
}