package de.lambda9.ready2race.backend.app.eventDocumentType.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TYPE
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class EventDocumentTypeSort : Sortable {
    NAME,
    REQUIRED,
    CONFIRMATION_REQUIRED;

    override fun toFields(): List<Field<*>> = when(this) {
        NAME -> listOf(EVENT_DOCUMENT_TYPE.NAME)
        REQUIRED -> listOf(EVENT_DOCUMENT_TYPE.REQUIRED)
        CONFIRMATION_REQUIRED -> listOf(EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED)
    }
}