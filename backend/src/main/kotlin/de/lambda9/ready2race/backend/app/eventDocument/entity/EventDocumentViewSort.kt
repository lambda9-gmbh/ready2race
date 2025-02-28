package de.lambda9.ready2race.backend.app.eventDocument.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_VIEW
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class EventDocumentViewSort : Sortable {
    NAME,
    DOCUMENT_TYPE,
    CREATED_AT;

    override fun toField(): Field<*> = when (this) {
        NAME -> EVENT_DOCUMENT_VIEW.NAME
        DOCUMENT_TYPE -> EVENT_DOCUMENT_VIEW.DOCUMENT_TYPE
        CREATED_AT -> EVENT_DOCUMENT_VIEW.CREATED_AT
    }
}