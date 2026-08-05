package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATIONS_VIEW
import org.jooq.Field
import org.jooq.impl.DSL

enum class EventRegistrationViewSort : Sortable {
    CLUB_NAME,
    CREATED_AT,
    MESSAGE;

    override fun toFields(): List<Field<*>> = when (this) {
        CLUB_NAME -> listOf(EVENT_REGISTRATIONS_VIEW.CLUB_NAME)
        CREATED_AT -> listOf(EVENT_REGISTRATIONS_VIEW.CREATED_AT)
        MESSAGE -> listOf(DSL.field(EVENT_REGISTRATIONS_VIEW.MESSAGE.isNull()))
    }
}