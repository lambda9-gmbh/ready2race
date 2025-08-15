package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_TEAM
import org.jooq.Field

enum class CompetitionRegistrationTeamSort : Sortable {
    CLUB_NAME,
    NAME;

    override fun toFields(): List<Field<*>> = when (this) {
        CLUB_NAME -> listOf(COMPETITION_REGISTRATION_TEAM.CLUB_NAME)
        NAME -> listOf(COMPETITION_REGISTRATION_TEAM.TEAM_NAME)
    }
}