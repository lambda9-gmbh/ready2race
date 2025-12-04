package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.*
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionRegistrationTeam
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamRecord

object CompetitionRegistrationTeamRepo {

    private fun CompetitionRegistrationTeam.searchFields() = listOf(CLUB_NAME, TEAM_NAME, PARTICIPANTS, RATING_CATEGORY)

    fun get(competitionRegistrationId: UUID) =
        COMPETITION_REGISTRATION_TEAM.selectOne { COMPETITION_REGISTRATION_ID.eq(competitionRegistrationId) }

    fun teamCountForCompetition(
        competitionId: UUID,
        search: String?,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
        onlyUnverified: Boolean,
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_REGISTRATION_TEAM) {
            fetchCount(
                this,
                DSL.and(
                    COMPETITION_ID.eq(competitionId),
                    filterScope(scope, user.club),
                    search.metaSearch(searchFields()),
                    if (onlyUnverified) filterUnverified else DSL.trueCondition(),
                ),
            )
        }
    }


    fun getCompetitionRegistrationTeams(
        eventId: UUID
    ): JIO<List<CompetitionRegistrationTeamRecord>> = COMPETITION_REGISTRATION_TEAM.select { EVENT_ID.eq(eventId) }

    fun teamPageForCompetition(
        competitionId: UUID,
        params: PaginationParameters<CompetitionRegistrationTeamSort>,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
        onlyUnverified: Boolean,
    ): JIO<List<CompetitionRegistrationTeamRecord>> = Jooq.query {
        with(COMPETITION_REGISTRATION_TEAM) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        COMPETITION_ID.eq(competitionId),
                        filterScope(scope, user.club),
                        if (onlyUnverified) filterUnverified else DSL.trueCondition(),
                    )
                }
                .fetch()
        }
    }

    private val filterUnverified: Condition =
        DSL.exists(
            DSL.selectOne().from(COMPETITION_MATCH_TEAM)
                .where(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION_TEAM.COMPETITION_REGISTRATION_ID))
                .and(COMPETITION_MATCH_TEAM.RESULT_VALUE.isNotNull)
                .and(COMPETITION_MATCH_TEAM.RESULT_VERIFIED_AT.isNull)
        )

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition =
        if (scope == Privilege.Scope.OWN) COMPETITION_REGISTRATION_TEAM.CLUB_ID.eq(clubId) else DSL.trueCondition()

}