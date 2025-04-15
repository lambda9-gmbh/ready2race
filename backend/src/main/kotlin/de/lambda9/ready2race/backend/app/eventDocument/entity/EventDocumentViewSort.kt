package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_VIEW
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import org.jooq.Field

enum class EventDocumentViewSort : Sortable {
    NAME,
    DOCUMENT_TYPE,
    CREATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        NAME -> listOf(EVENT_DOCUMENT_VIEW.NAME)
        DOCUMENT_TYPE -> listOf(EVENT_DOCUMENT_VIEW.DOCUMENT_TYPE)
        CREATED_AT -> listOf(EVENT_DOCUMENT_VIEW.CREATED_AT)
    }
}