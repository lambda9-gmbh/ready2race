package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import org.jooq.Field

enum class CompetitionRegistrationSort : Sortable {
    CLUB_NAME,
    NAME,
    CREATED_AT,
    UPDATED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        CLUB_NAME -> listOf(CLUB.NAME)
        NAME -> listOf(COMPETITION_REGISTRATION.NAME)
        CREATED_AT -> listOf(COMPETITION_REGISTRATION.CREATED_AT)
        UPDATED_AT -> listOf(COMPETITION_REGISTRATION.UPDATED_AT)
    }
}