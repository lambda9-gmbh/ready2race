package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.InfoViewConfigurationRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toRecord
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import org.jooq.JSONB
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object EventInfoService {

    // Info View Configuration Methods

    fun getInfoViews(
        eventId: UUID,
        includeInactive: Boolean = false
    ): App<EventInfoProblem, ApiResponse.ListDto<InfoViewConfigurationDto>> =
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
        competitionId: UUID?,
    ): App<Nothing, ApiResponse.ListDto<LatestMatchResultInfo>> = KIO.comprehension {

        val matches = !CompetitionMatchRepo.getMatchResults(eventId, competitionId, limit).orDie()

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
                startTime = match[COMPETITION_MATCH.START_TIME],
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

        val matches =
            !CompetitionMatchRepo.getUpcomingMatches(eventId, eventDayId, competitionId, roundName, limit).orDie()

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

        val matches = !CompetitionMatchRepo.getRunningMatches(eventId, eventDayId, competitionId, limit).orDie()

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

    private fun getMatchResultTeams(matchId: UUID): App<Nothing, List<MatchResultTeamInfo>> = KIO.comprehension {
        val records = !CompetitionMatchTeamRepo.getTeamsForMatchResult(matchId).orDie()

        // TODO: calculate substitutions (Participants) and mark deregistered, failed or out
        // now matches are filtered by having a place set for all teams - this is not up to date

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
        val records = !CompetitionMatchTeamRepo.getTeamsForUpcomingMatch(matchId).orDie()

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
        val records = !CompetitionMatchTeamRepo.getTeamForRunningMatch(matchId).orDie()

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