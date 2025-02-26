package de.lambda9.ready2race.backend.app.eventDocumentType.entity

import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_TYPE
import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class EventDocumentTypeSort : Sortable {
    NAME,
    REQUIRED,
    CONFIRMATION_REQUIRED;

    override fun toField(): Field<*> = when(this) {
        NAME -> EVENT_DOCUMENT_TYPE.NAME
        REQUIRED -> EVENT_DOCUMENT_TYPE.REQUIRED
        CONFIRMATION_REQUIRED -> EVENT_DOCUMENT_TYPE.CONFIRMATION_REQUIRED
    }
}