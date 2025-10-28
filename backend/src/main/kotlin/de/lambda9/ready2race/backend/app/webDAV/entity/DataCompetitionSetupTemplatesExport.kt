package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.CompetitionSetupTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataCompetitionSetupTemplatesExport(
    val competitionSetupTemplates: JsonNode,
    val competitionSetupGroups: JsonNode,
    val competitionSetupGroupStatisticEvaluation: JsonNode,
    val competitionSetupMatches: JsonNode,
    val competitionSetupParticipants: JsonNode,
    val competitionSetupPlaces: JsonNode,
    val competitionSetupRounds: JsonNode,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val templateIds = !CompetitionSetupTemplateRepo.allIds().orDie()
            val templates = !CompetitionSetupTemplateRepo.allAsJson().orDie()

            val setupData = !WebDAVExportService.getSetupRoundsWithDependenciesAsJson(templateIds)

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf(
                    "competitionSetupTemplates" to templates,
                    "competitionSetupGroups" to setupData[CompetitionSetupDataType.GROUPS]!!,
                    "competitionSetupGroupStatisticEvaluation" to setupData[CompetitionSetupDataType.EVALUATIONS]!!,
                    "competitionSetupMatches" to setupData[CompetitionSetupDataType.MATCHES]!!,
                    "competitionSetupParticipants" to setupData[CompetitionSetupDataType.PARTICIPANTS]!!,
                    "competitionSetupPlaces" to setupData[CompetitionSetupDataType.PLACES]!!,
                    "competitionSetupRounds" to setupData[CompetitionSetupDataType.ROUNDS]!!,
                )
            )

            KIO.ok(
                File(
                    name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES),
                    bytes = json
                )
            )
        }

        fun importData(data: DataCompetitionSetupTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                !CompetitionSetupTemplateRepo.insertJsonData(data.competitionSetupTemplates.toString()).orDie()
                !CompetitionSetupRoundRepo.insertJsonData(data.competitionSetupRounds.toString()).orDie()
                !CompetitionSetupGroupStatisticEvaluationRepo
                    .insertJsonData(data.competitionSetupGroupStatisticEvaluation.toString())
                    .orDie()
                !CompetitionSetupMatchRepo.insertJsonData(data.competitionSetupMatches.toString()).orDie()
                !CompetitionSetupPlaceRepo.insertJsonData(data.competitionSetupPlaces.toString()).orDie()
                !CompetitionSetupGroupRepo.insertJsonData(data.competitionSetupGroups.toString()).orDie()
                !CompetitionSetupParticipantRepo.insertJsonData(data.competitionSetupParticipants.toString()).orDie()


                unit
            }
    }
}