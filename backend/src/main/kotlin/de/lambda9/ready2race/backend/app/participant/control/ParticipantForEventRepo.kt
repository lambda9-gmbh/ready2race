package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementReducedDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object ParticipantForEventRepo {

    private fun ParticipantForEvent.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    fun getByClub(
        clubId: UUID,
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .where(CLUB_ID.eq(clubId))
                .fetch()
        }
    }

    fun count(
        search: String?,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(EVENT_ID.eq(eventId))
                    .and(filterScope(scope, user.club))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantForEventSort>,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<List<ParticipantForEventDto>> = Jooq.query {
        val participantTracking = CompetitionRegistrationRepo.selectParticipantTrackings()

        with(PARTICIPANT_FOR_EVENT) {
            select(
                this.asterisk(),
                participantTracking
            )
                .from(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                        .and(filterScope(scope, user.club))
                }
                .fetch {
                    ParticipantForEventDto(
                        id = it[PARTICIPANT_FOR_EVENT.ID]!!,
                        clubId = it[PARTICIPANT_FOR_EVENT.CLUB_ID]!!,
                        clubName = it[PARTICIPANT_FOR_EVENT.CLUB_NAME]!!,
                        firstname = it[PARTICIPANT_FOR_EVENT.FIRSTNAME]!!,
                        lastname = it[PARTICIPANT_FOR_EVENT.LASTNAME]!!,
                        year = it[PARTICIPANT_FOR_EVENT.YEAR],
                        gender = it[PARTICIPANT_FOR_EVENT.GENDER]!!,
                        external = it[PARTICIPANT_FOR_EVENT.EXTERNAL],
                        externalClubName = it[PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME],
                        participantRequirementsChecked = it[PARTICIPANT_FOR_EVENT.PARTICIPANT_REQUIREMENTS_CHECKED]?.filterNotNull()
                            ?.map { pr ->
                                ParticipantRequirementReducedDto(
                                    id = pr.id,
                                    name = pr.name,
                                )
                            } ?: emptyList(),
                        qrCodeId = it[PARTICIPANT_FOR_EVENT.QR_CODE_ID],
                        namedParticipantIds = it[PARTICIPANT_FOR_EVENT.NAMED_PARTICIPANT_IDS]?.filterNotNull()
                            ?: emptyList(),
                        currentStatus = it[participantTracking]?.firstOrNull()
                            ?.let { latestScan -> ParticipantScanType.valueOf(latestScan.value1()!!) },
                        lastScanAt = it[participantTracking]?.firstOrNull()?.value2(),
                        lastScanBy = it[participantTracking]?.firstOrNull().let { tracking ->
                            if (tracking?.value3() != null && tracking.value4() != null && tracking.value5() != null) {
                                AppUserNameDto(
                                    id = tracking.value3()!!,
                                    firstname = tracking.value4()!!,
                                    lastname = tracking.value5()!!,
                                )
                            } else null
                        }
                    )
                }
        }
    }

    fun getParticipantsForEventWithMissingRequirement(
        eventId: UUID,
        participantRequirementId: UUID
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .where(
                    EVENT_ID.eq(eventId)
                        .andNotExists(
                            selectFrom(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT)
                                .where(
                                    PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.EVENT.eq(this.EVENT_ID)
                                        .and(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT.eq(this.ID))
                                        .and(
                                            PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT.eq(
                                                participantRequirementId
                                            )
                                        )
                                )
                        )
                )
                .fetch()
        }

    }

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) PARTICIPANT_FOR_EVENT.CLUB_ID.eq(clubId) else DSL.trueCondition()

}