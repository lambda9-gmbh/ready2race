package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.InfoViewConfigurationRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toRecord
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationRequest
import de.lambda9.ready2race.backend.app.eventInfo.entity.LatestMatchResultInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.MatchResultTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.ParticipantInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchParticipantInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchTeamInfo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.JSONB
import org.jooq.impl.DSL.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object EventInfoService {

    // Info View Configuration Methods

    fun getInfoViews(eventId: UUID, includeInactive: Boolean = false): App<EventInfoProblem, ApiResponse.ListDto<InfoViewConfigurationDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }

            val views = !InfoViewConfigurationRepo.findByEvent(eventId, includeInactive).orDie()
            KIO.ok(ApiResponse.ListDto(views.map { it.toDto() }))
        }

    fun createInfoView(
        eventId: UUID,
        request: InfoViewConfigurationRequest
    ): App<EventInfoProblem, ApiResponse.Created> = KIO.comprehension {
        // Validate event exists
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
        }

        // Validate request
        if (request.displayDurationSeconds <= 0) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InvalidViewConfiguration("Display duration must be positive"))
        }
        if (request.dataLimit <= 0 || request.dataLimit > 100) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InvalidViewConfiguration("Data limit must be between 1 and 100"))
        }

        val record = request.toRecord(eventId)
        val created = !InfoViewConfigurationRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(created))
    }

    fun updateInfoView(
        id: UUID,
        request: InfoViewConfigurationRequest
    ): App<EventInfoProblem, ApiResponse.NoData> = KIO.comprehension {
        val existing = !InfoViewConfigurationRepo.findById(id).orDie()
        if (existing == null) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        }

        val updated = !InfoViewConfigurationRepo.update(id) {
            viewType = request.viewType
            displayDurationSeconds = request.displayDurationSeconds
            dataLimit = request.dataLimit
            filters = request.filters?.let { JSONB.jsonb(it.toString()) }
            sortOrder = request.sortOrder
            isActive = request.isActive
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (updated == null) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        } else {
            KIO.ok(ApiResponse.NoData)
        }
    }

    fun deleteInfoView(id: UUID): App<EventInfoProblem, ApiResponse.NoData> = KIO.comprehension {
        val existing = !InfoViewConfigurationRepo.exists(id).orDie()

        if (!existing) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        }

        !InfoViewConfigurationRepo.delete(id).orDie()

        noData
    }

    // Data Fetching Methods




    fun getLatestMatchResults(
        eventId: UUID,
        limit: Int = 10,
        filters: JsonNode? = null
    ): App<Nothing, ApiResponse.ListDto<LatestMatchResultInfo>> = KIO.comprehension {
        val eventDayId = filters?.get("eventDayId")?.asText()?.let { UUID.fromString(it) }
        val competitionId = filters?.get("competitionId")?.asText()?.let { UUID.fromString(it) }

        val matches = !Jooq.query {
            select(
                COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
                COMPETITION_MATCH.UPDATED_AT,
                COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
                COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
                COMPETITION.ID.`as`("competition_id"),
                COMPETITION_VIEW.NAME.`as`("competition_name"),
                COMPETITION_VIEW.CATEGORY_NAME,
                EVENT_DAY.ID.`as`("event_day_id"),
                EVENT_DAY.DATE.`as`("event_day_date"),
                EVENT_DAY.NAME.`as`("event_day_name")
            )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH).on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND).on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES).on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                exists(
                    selectOne()
                    .from(COMPETITION_MATCH_TEAM)
                    .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(COMPETITION_MATCH_TEAM.PLACE.isNotNull)
                )
            )
            .and(
                notExists(
                    selectOne()
                    .from(COMPETITION_MATCH_TEAM)
                    .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                )
            )
            .and(
                field(
                    select(count())
                    .from(COMPETITION_MATCH_TEAM)
                    .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                ).gt(1)
            )
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
            }
            .orderBy(COMPETITION_MATCH.UPDATED_AT.desc())
            .limit(limit)
            .fetch()
        }.orDie()

        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val teams = !getMatchResultTeams(matchId)

            LatestMatchResultInfo(
                matchId = matchId,
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                matchNumber = null, // Could be parsed from match name if needed
                updatedAt = match[COMPETITION_MATCH.UPDATED_AT]!!,
                eventDayId = match.get("event_day_id", UUID::class.java),
                eventDayDate = match.get("event_day_date", LocalDate::class.java),
                eventDayName = match.get("event_day_name", String::class.java),
                teams = teams
            )
        }

        KIO.ok(ApiResponse.ListDto(result))
    }

    fun getUpcomingCompetitionMatches(
        eventId: UUID,
        limit: Int = 10,
        filters: JsonNode? = null
    ): App<Nothing, ApiResponse.ListDto<UpcomingCompetitionMatchInfo>> = KIO.comprehension {
        val eventDayId = filters?.get("eventDayId")?.asText()?.let { UUID.fromString(it) }
        val competitionId = filters?.get("competitionId")?.asText()?.let { UUID.fromString(it) }
        val roundName = filters?.get("roundName")?.asText()

        val matches = !Jooq.query {
            select(
                COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
                COMPETITION_MATCH.START_TIME,
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
                COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
                COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
                COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
                COMPETITION.ID.`as`("competition_id"),
                COMPETITION_VIEW.NAME.`as`("competition_name"),
                COMPETITION_VIEW.CATEGORY_NAME,
                EVENT_DAY.ID.`as`("event_day_id"),
                EVENT_DAY.DATE.`as`("event_day_date"),
                EVENT_DAY.NAME.`as`("event_day_name")
            )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH).on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND).on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES).on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.START_TIME.isNotNull)
            .and(COMPETITION_MATCH.START_TIME.gt(LocalDateTime.now()))
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
                if (roundName != null) {
                    and(COMPETITION_SETUP_ROUND.NAME.eq(roundName))
                }
            }
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
        }.orDie()

        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val teams = !getUpcomingMatchTeams(matchId)

            UpcomingCompetitionMatchInfo(
                matchId = matchId,
                matchNumber = null, // Could be parsed from match name if needed
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                eventDayId = match.get("event_day_id", UUID::class.java),
                eventDayDate = match.get("event_day_date", java.time.LocalDate::class.java),
                eventDayName = match.get("event_day_name", String::class.java),
                scheduledStartTime = match[COMPETITION_MATCH.START_TIME],
                placeName = null, // No place join in this query
                roundNumber = null, // No round number field available
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                teams = teams
            )
        }

        KIO.ok(ApiResponse.ListDto(result))
    }

    fun getRunningMatches(
        eventId: UUID,
        limit: Int = 10,
        filters: JsonNode? = null
    ): App<Nothing, ApiResponse.ListDto<RunningMatchInfo>> = KIO.comprehension {
        val eventDayId = filters?.get("eventDayId")?.asText()?.let { UUID.fromString(it) }
        val competitionId = filters?.get("competitionId")?.asText()?.let { UUID.fromString(it) }

        val matches = !Jooq.query {
            select(
                COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
                COMPETITION_MATCH.START_TIME,
                COMPETITION_MATCH.CURRENTLY_RUNNING,
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
                COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
                COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
                COMPETITION.ID.`as`("competition_id"),
                COMPETITION_VIEW.NAME.`as`("competition_name"),
                COMPETITION_VIEW.CATEGORY_NAME,
                EVENT_DAY.ID.`as`("event_day_id"),
                EVENT_DAY.DATE.`as`("event_day_date"),
                EVENT_DAY.NAME.`as`("event_day_name")
            )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH).on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND).on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES).on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.CURRENTLY_RUNNING.eq(true))
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
            }
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
        }.orDie()

        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val startTime = match[COMPETITION_MATCH.START_TIME]
            val elapsedMinutes = startTime?.let { 
                java.time.Duration.between(it, LocalDateTime.now()).toMinutes() 
            }
            val teams = !getRunningMatchTeams(matchId)

            RunningMatchInfo(
                matchId = matchId,
                matchNumber = null,
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                eventDayId = match.get("event_day_id", UUID::class.java),
                eventDayDate = match.get("event_day_date", LocalDate::class.java),
                eventDayName = match.get("event_day_name", String::class.java),
                startTime = startTime,
                elapsedMinutes = elapsedMinutes,
                placeName = null,
                roundNumber = null,
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                teams = teams
            )
        }

        KIO.ok(ApiResponse.ListDto(result))
    }

    // Helper Methods




    private fun getParticipantsForRegistration(registrationId: UUID): App<Nothing, List<ParticipantInfo>> = KIO.comprehension {
        val participants = !Jooq.query {
            select(
                PARTICIPANT.ID,
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
            .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .join(PARTICIPANT).on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(PARTICIPANT.ID))
            .join(NAMED_PARTICIPANT).on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(NAMED_PARTICIPANT.ID))
            .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(registrationId))
            .fetch()
        }.orDie()

        val result = participants.map { record ->
            ParticipantInfo(
                participantId = record[PARTICIPANT.ID]!!,
                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                namedRole = record.get("named_role", String::class.java)
            )
        }

        KIO.ok(result)
    }

    private fun getMatchResultTeams(matchId: UUID): App<Nothing, List<MatchResultTeamInfo>> = KIO.comprehension {
        val records = !Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.PLACE,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_REGISTRATION).on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT).on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .and(COMPETITION_MATCH_TEAM.PLACE.isNotNull)
            .orderBy(COMPETITION_MATCH_TEAM.PLACE.asc())
            .fetch()
        }.orDie()

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                MatchResultTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    teamNumber = null,
                    clubName = first.get("club_name", String::class.java),
                    place = first[COMPETITION_MATCH_TEAM.PLACE]!!,
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            ParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java)
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }

    private fun getUpcomingMatchTeams(matchId: UUID): App<Nothing, List<UpcomingMatchTeamInfo>> = KIO.comprehension {
        val records = !Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.START_NUMBER,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                PARTICIPANT.YEAR,
                PARTICIPANT.GENDER,
                PARTICIPANT.EXTERNAL_CLUB_NAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_REGISTRATION).on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT).on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .orderBy(
                COMPETITION_MATCH_TEAM.START_NUMBER.asc().nullsLast(),
                COMPETITION_REGISTRATION.NAME.asc().nullsLast()
            )
            .fetch()
        }.orDie()

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                UpcomingMatchTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                    clubName = first.get("club_name", String::class.java),
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            UpcomingMatchParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java),
                                year = record[PARTICIPANT.YEAR],
                                gender = record[PARTICIPANT.GENDER]?.name,
                                externalClubName = record[PARTICIPANT.EXTERNAL_CLUB_NAME]
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }

    private fun getRunningMatchTeams(matchId: UUID): App<Nothing, List<RunningMatchTeamInfo>> = KIO.comprehension {
        val records = !Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.START_NUMBER,
                COMPETITION_MATCH_TEAM.PLACE,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                PARTICIPANT.YEAR,
                PARTICIPANT.GENDER,
                PARTICIPANT.EXTERNAL_CLUB_NAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_REGISTRATION).on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT).on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .orderBy(
                COMPETITION_MATCH_TEAM.START_NUMBER.asc().nullsLast(),
                COMPETITION_REGISTRATION.NAME.asc().nullsLast()
            )
            .fetch()
        }.orDie()

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                RunningMatchTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                    clubName = first.get("club_name", String::class.java),
                    currentScore = null, // Could be calculated if scoring data is available
                    currentPosition = first[COMPETITION_MATCH_TEAM.PLACE],
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            UpcomingMatchParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java),
                                year = record[PARTICIPANT.YEAR],
                                gender = record[PARTICIPANT.GENDER]?.name,
                                externalClubName = record[PARTICIPANT.EXTERNAL_CLUB_NAME]
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }
}