package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataCompetitionTemplatesExport(
    val competitionTemplates: JsonNode,
    val competitionProperties: JsonNode,
    val competitionPropertiesHasFees: JsonNode,
    val competitionPropertiesHasNamedParticipants: JsonNode,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val templateIds = !CompetitionTemplateRepo.allIds().orDie()
            val templates = !CompetitionTemplateRepo.allAsJson().orDie()

            val propertiesIds = !CompetitionPropertiesRepo.getIdsByCompetitionOrTemplateIds(templateIds).orDie()
            val properties = !CompetitionPropertiesRepo.getByCompetitionOrTemplateIdsAsJson(templateIds).orDie()

            val propertiesHasFees = !CompetitionPropertiesHasFeeRepo.getByPropertiesAsJson(propertiesIds).orDie()

            val propertiesHasNamedParticipants =
                !CompetitionPropertiesHasNamedParticipantRepo.getByPropertiesAsJson(propertiesIds).orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record, mapOf(
                    "competitionTemplates" to templates,
                    "competitionProperties" to properties,
                    "competitionPropertiesHasFees" to propertiesHasFees,
                    "competitionPropertiesHasNamedParticipants" to propertiesHasNamedParticipants
                )
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_TEMPLATES), bytes = json))
        }

        fun importData(data: DataCompetitionTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                // COMPETITION TEMPLATES
                !CompetitionTemplateRepo.insertJsonData(data.competitionTemplates.toString()).orDie()

                // COMPETITION PROPERTIES
                !CompetitionPropertiesRepo.insertJsonData(data.competitionProperties.toString()).orDie()

                // PROPERTIES HAS FEES
                !CompetitionPropertiesHasFeeRepo.insertJsonData(data.competitionPropertiesHasFees.toString()).orDie()

                // PROPERTIES HAS NAMED PARTICIPANT
                !CompetitionPropertiesHasNamedParticipantRepo
                    .insertJsonData(data.competitionPropertiesHasNamedParticipants.toString())
                    .orDie()


                unit
            }
    }
}