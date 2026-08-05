package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
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

        KIO.ok(
            ApiResponse.Dto(
                TimingConfigDto(
                    timingSystem = competition.timingSystem?.let { TimingSystem.valueOf(it) },
                    timeTrialResultsUrl = competition.raceclockerTtResultsUrl,
                    heatsResultsUrl = competition.raceclockerHeatsResultsUrl,
                    startlistConfigQualification = competition.startlistConfigQualification,
                    startlistConfigRounds = competition.startlistConfigRounds,
                    resultImportConfig = competition.resultImportConfig,
                    hasQualificationRound = hasQualificationRound,
                )
            )
        )
    }

    fun updateTimingConfig(
        competitionId: UUID,
        userId: UUID,
        request: TimingConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // Normalisiert gespeichert (Schema ergaenzt, http auf https gehoben), damit der Tab hinterher
        // zeigt, was das Abholen tatsaechlich anfragt. Leer heisst "nicht konfiguriert" -- Leerstrings
        // wuerden das Abholen spaeter mit einem unbrauchbaren URL-Fehler scheitern lassen statt mit dem
        // klaren "keine URL hinterlegt".
        val timeTrialUrl = request.timeTrialResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }
        val heatsUrl = request.heatsResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }

        !CompetitionRepo.update(competitionId) {
            timingSystem = request.timingSystem?.name
            raceclockerTtResultsUrl = timeTrialUrl
            raceclockerHeatsResultsUrl = heatsUrl
            startlistConfigQualification = request.startlistConfigQualification
            startlistConfigRounds = request.startlistConfigRounds
            resultImportConfig = request.resultImportConfig
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
    }
}
