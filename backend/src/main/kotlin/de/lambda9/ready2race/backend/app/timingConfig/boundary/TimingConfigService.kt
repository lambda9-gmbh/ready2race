package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerRaceRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceError
import de.lambda9.ready2race.backend.app.timingConfig.control.TimingConfigRepo
import de.lambda9.ready2race.backend.app.timingConfig.entity.EventTimingConfigDto
import de.lambda9.ready2race.backend.app.timingConfig.entity.EventTimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigDto
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object TimingConfigService {

    fun getTimingConfig(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<TimingConfigDto>> = KIO.comprehension {

        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }

        val hasQualificationRound = !CompetitionSetupRoundRepo.existsQualificationRound(competitionId).orDie()

        val event = !EventRepo.get(competition.event!!).orDie()
            .onNullFail { EventError.NotFound }

        KIO.ok(
            ApiResponse.Dto(
                TimingConfigDto(
                    timingSystem = competition.timingSystem?.let { TimingSystem.valueOf(it) },
                    raceQualification = competition.raceclockerRaceQualification,
                    raceRounds = competition.raceclockerRaceRounds,
                    startlistConfigQualification = competition.startlistConfigQualification,
                    startlistConfigRounds = competition.startlistConfigRounds,
                    resultImportConfig = competition.resultImportConfig,
                    hasQualificationRound = hasQualificationRound,
                    eventTimingSystem = event.timingSystem?.let { TimingSystem.valueOf(it) },
                    eventStartlistConfigQualification = event.startlistConfigQualification,
                    eventStartlistConfigRounds = event.startlistConfigRounds,
                    eventResultImportConfig = event.resultImportConfig,
                )
            )
        )
    }

    fun getEventTimingConfig(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.Dto<EventTimingConfigDto>> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }

        val deviations = !TimingConfigRepo.getDeviations(eventId).orDie()

        KIO.ok(
            ApiResponse.Dto(
                EventTimingConfigDto(
                    timingSystem = event.timingSystem?.let { TimingSystem.valueOf(it) },
                    startlistConfigQualification = event.startlistConfigQualification,
                    startlistConfigRounds = event.startlistConfigRounds,
                    resultImportConfig = event.resultImportConfig,
                    // Spalten sind in der Datenbank NOT NULL (Migration V202608071600); jOOQ generiert
                    // Record-Felder dennoch nullable - dasselbe Muster wie bei den übrigen
                    // Nicht-Null-Spalten des Events (siehe Conversions.kt).
                    autoPull = event.raceclockerAutoPull!!,
                    intervalActiveSeconds = event.raceclockerIntervalActiveSeconds!!,
                    intervalUpcomingSeconds = event.raceclockerIntervalUpcomingSeconds!!,
                    watchBeforeMinutes = event.raceclockerWatchBeforeMinutes!!,
                    watchAfterMinutes = event.raceclockerWatchAfterMinutes!!,
                    deviatingCompetitions = deviations,
                )
            )
        )
    }

    fun updateEventTimingConfig(
        eventId: UUID,
        userId: UUID,
        request: EventTimingConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }

        !EventRepo.update(event) {
            timingSystem = request.timingSystem?.name
            startlistConfigQualification = request.startlistConfigQualification
            startlistConfigRounds = request.startlistConfigRounds
            resultImportConfig = request.resultImportConfig
            raceclockerAutoPull = request.autoPull
            raceclockerIntervalActiveSeconds = request.intervalActiveSeconds
            raceclockerIntervalUpcomingSeconds = request.intervalUpcomingSeconds
            raceclockerWatchBeforeMinutes = request.watchBeforeMinutes
            raceclockerWatchAfterMinutes = request.watchAfterMinutes
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }

    fun updateTimingConfig(
        competitionId: UUID,
        userId: UUID,
        request: TimingConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }

        // Ein Rennen gehört einer Veranstaltung. Der Fremdschlüssel allein hindert niemanden daran,
        // das Rennen einer FREMDEN Veranstaltung anzuwählen -- dann liefe dieser Wettkampf gegen ein
        // Rennen, das mit seiner Regatta nichts zu tun hat.
        !ensureRacesBelongToEvent(competition.event!!, request.raceQualification, request.raceRounds)

        !CompetitionRepo.update(competitionId) {
            timingSystem = request.timingSystem?.name
            raceclockerRaceQualification = request.raceQualification
            raceclockerRaceRounds = request.raceRounds
            startlistConfigQualification = request.startlistConfigQualification
            startlistConfigRounds = request.startlistConfigRounds
            resultImportConfig = request.resultImportConfig
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
    }

    /**
     * Beide angewählten Rennen müssen zu dieser Veranstaltung gehören.
     *
     * Die Datenbank erzwingt das nicht: Dafür bräuchte es einen zusammengesetzten Fremdschlüssel
     * über (event, id), der den übrigen Tabellen dieses Projekts fremd wäre. Also fragt der Service.
     */
    private fun ensureRacesBelongToEvent(
        eventId: UUID,
        vararg raceIds: UUID?,
    ): App<ServiceError, Unit> = KIO.comprehension {
        raceIds.filterNotNull().forEach { raceId ->
            val belongs = !RaceClockerRaceRepo.belongsToEvent(raceId, eventId).orDie()
            if (!belongs) return@comprehension KIO.fail(RaceClockerRaceError.NotFound)
        }
        KIO.ok(Unit)
    }
}
