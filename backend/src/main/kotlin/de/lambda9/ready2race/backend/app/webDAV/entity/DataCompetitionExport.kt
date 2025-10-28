package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupGroupRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupGroupStatisticEvaluationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupPlaceRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie


data class DataCompetitionExport(
    val competition: JsonNode,
    val competitionProperties: JsonNode,
    val competitionPropertiesHasFees: JsonNode,
    val competitionPropertiesHasNamedParticipants: JsonNode,
    val competitionSetup: JsonNode,
    val competitionSetupGroups: JsonNode,
    val competitionSetupGroupStatisticEvaluation: JsonNode,
    val competitionSetupMatches: JsonNode,
    val competitionSetupParticipants: JsonNode,
    val competitionSetupPlaces: JsonNode,
    val competitionSetupRounds: JsonNode,
    val eventDayHasCompetitions: JsonNode
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val competitionId = record.dataReference
                ?: return@comprehension KIO.fail(WebDAVError.FileNotFound(record.id, null))

            val competition = !CompetitionRepo.getAsJson(competitionId).orDie()

            val propertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
                .onNullFail {
                    WebDAVError.FileNotFound(
                        record.id,
                        competitionId
                    )
                } // this also implies that there is no competition

            val competitionProperties = !CompetitionPropertiesRepo.getAsJson(propertiesId).orDie()

            val competitionPropertiesHasFees =
                !CompetitionPropertiesHasFeeRepo.getByPropertiesAsJson(listOf(propertiesId)).orDie()

            val competitionPropertiesHasNamedParticipants =
                !CompetitionPropertiesHasNamedParticipantRepo.getByPropertiesAsJson(listOf(propertiesId)).orDie()

            // SETUP
            val competitionSetup = !CompetitionSetupRepo.getAsJson(propertiesId).orDie()
            val setupData = !WebDAVExportService.getSetupRoundsWithDependenciesAsJson(listOf(propertiesId))


            val eventDayHasCompetitions = !EventDayHasCompetitionRepo.getAsJson(competitionId).orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record, mapOf(
                    "competition" to competition,
                    "competitionProperties" to competitionProperties,
                    "competitionPropertiesHasFees" to competitionPropertiesHasFees,
                    "competitionPropertiesHasNamedParticipants" to competitionPropertiesHasNamedParticipants,
                    "competitionSetup" to competitionSetup,
                    "competitionSetupGroups" to setupData[CompetitionSetupDataType.GROUPS]!!,
                    "competitionSetupGroupStatisticEvaluation" to setupData[CompetitionSetupDataType.EVALUATIONS]!!,
                    "competitionSetupMatches" to setupData[CompetitionSetupDataType.MATCHES]!!,
                    "competitionSetupParticipants" to setupData[CompetitionSetupDataType.PARTICIPANTS]!!,
                    "competitionSetupPlaces" to setupData[CompetitionSetupDataType.PLACES]!!,
                    "competitionSetupRounds" to setupData[CompetitionSetupDataType.ROUNDS]!!,
                    "eventDayHasCompetitions" to eventDayHasCompetitions
                )
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION), bytes = json))
        }

        fun importData(data: DataCompetitionExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            !CompetitionRepo.insertJsonData(data.competition.toString()).orDie()

            !CompetitionPropertiesRepo.insertJsonData(data.competitionProperties.toString()).orDie()

            !CompetitionPropertiesHasFeeRepo.insertJsonData(data.competitionPropertiesHasFees.toString()).orDie()
            !CompetitionPropertiesHasNamedParticipantRepo
                .insertJsonData(data.competitionPropertiesHasNamedParticipants.toString()).orDie()

            !CompetitionSetupRepo.insertJsonData(data.competitionSetup.toString()).orDie()
            !CompetitionSetupGroupRepo.insertJsonData(data.competitionSetupGroups.toString()).orDie()
            !CompetitionSetupGroupStatisticEvaluationRepo
                .insertJsonData(data.competitionSetupGroupStatisticEvaluation.toString()).orDie()
            !CompetitionSetupMatchRepo.insertJsonData(data.competitionSetupMatches.toString()).orDie()
            !CompetitionSetupParticipantRepo.insertJsonData(data.competitionSetupParticipants.toString()).orDie()
            !CompetitionSetupPlaceRepo.insertJsonData(data.competitionSetupPlaces.toString()).orDie()
            !CompetitionSetupRoundRepo.insertJsonData(data.competitionSetupRounds.toString()).orDie()

            !EventDayHasCompetitionRepo.insertJsonData(data.eventDayHasCompetitions.toString()).orDie()

            unit
        }
    }
}
