package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
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

        val event = !EventRepo.get(competition.event!!).orDie()
            .onNullFail { EventError.NotFound }

        KIO.ok(
            ApiResponse.Dto(
                TimingConfigDto(
                    timingSystem = competition.timingSystem?.let { TimingSystem.valueOf(it) },
                    race = competition.raceclockerRace,
                    startlistConfig = competition.startlistConfig,
                    resultImportConfig = competition.resultImportConfig,
                    eventTimingSystem = event.timingSystem?.let { TimingSystem.valueOf(it) },
                    eventStartlistConfig = event.startlistConfig,
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
                    startlistConfig = event.startlistConfig,
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
            startlistConfig = request.startlistConfig
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
        !ensureRaceBelongsToEvent(competition.event!!, request.race)

        !CompetitionRepo.update(competitionId) {
            timingSystem = request.timingSystem?.name
            raceclockerRace = request.race
            startlistConfig = request.startlistConfig
            resultImportConfig = request.resultImportConfig
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
    }

    /**
     * Das angewählte Rennen muss zu dieser Veranstaltung gehören.
     *
     * Die Datenbank erzwingt das nicht: Dafür bräuchte es einen zusammengesetzten Fremdschlüssel
     * über (event, id), der den übrigen Tabellen dieses Projekts fremd wäre. Also fragt der Service.
     */
    private fun ensureRaceBelongsToEvent(
        eventId: UUID,
        raceId: UUID?,
    ): App<ServiceError, Unit> = KIO.comprehension {
        if (raceId != null) {
            val belongs = !RaceClockerRaceRepo.belongsToEvent(raceId, eventId).orDie()
            if (!belongs) return@comprehension KIO.fail(RaceClockerRaceError.NotFound)
        }
        KIO.ok(Unit)
    }
}
