package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATIONS_VIEW
import org.jooq.Field

enum class EventRegistrationViewSort : Sortable {
    EVENT_NAME,
    CREATED_AT;

    override fun toField(): Field<*> = when (this) {
        EVENT_NAME -> EVENT_REGISTRATIONS_VIEW.EVENT_NAME
        CREATED_AT -> EVENT_REGISTRATIONS_VIEW.CREATED_AT
    }
}