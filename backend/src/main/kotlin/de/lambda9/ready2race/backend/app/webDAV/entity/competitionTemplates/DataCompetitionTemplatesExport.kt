package de.lambda9.ready2race.backend.app.webDAV.entity.competitionTemplates

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesHasFeeExport
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties.CompetitionPropertiesHasNamedParticipantExport
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataCompetitionTemplatesExport(
    val competitionTemplates: List<CompetitionTemplateExport>,
    val competitionProperties: List<CompetitionPropertiesExport>,
    val competitionPropertiesHasFees: List<CompetitionPropertiesHasFeeExport>,
    val competitionPropertiesHasNamedParticipants: List<CompetitionPropertiesHasNamedParticipantExport>,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val templates = !CompetitionTemplateRepo.all().orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val properties = !CompetitionPropertiesRepo.getByCompetitionOrTemplateIds(templates.map { it.id }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val propertiesHasFees = !CompetitionPropertiesHasFeeRepo.getByProperties(properties.map { it.id }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val propertiesHasNamedParticipants =
                !CompetitionPropertiesHasNamedParticipantRepo.getByProperties(properties.map { it.id }).orDie()
                    .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataCompetitionTemplatesExport(
                competitionTemplates = templates,
                competitionProperties = properties,
                competitionPropertiesHasFees = propertiesHasFees,
                competitionPropertiesHasNamedParticipants = propertiesHasNamedParticipants,
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_TEMPLATES), bytes = json))
        }

        fun importData(data: DataCompetitionTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                // COMPETITION TEMPLATES
                val overlappingTemplates =
                    !CompetitionTemplateRepo.getOverlapIds(data.competitionTemplates.map { it.id }).orDie()
                val templateRecords = !data.competitionTemplates
                    .filter { !overlappingTemplates.contains(it.id) }
                    .traverse { it.toRecord() }

                if (templateRecords.isNotEmpty()) {
                    !CompetitionTemplateRepo.create(templateRecords).orDie()
                }

                // COMPETITION PROPERTIES
                val overlappingProperties =
                    !CompetitionPropertiesRepo.getOverlapIds(data.competitionProperties.map { it.id }).orDie()
                val propertiesRecords = !data.competitionProperties
                    .filter { !overlappingProperties.contains(it.id) }
                    .traverse { it.toRecord() }

                if (propertiesRecords.isNotEmpty()) {
                    !CompetitionPropertiesRepo.create(propertiesRecords).orDie()
                }

                // PROPERTIES HAS FEES
                val overlappingPropsHasFees =
                    !CompetitionPropertiesHasFeeRepo.getOverlapIds(data.competitionPropertiesHasFees.map { it.id })
                        .orDie()
                val propsHasFeeRecords = !data.competitionPropertiesHasFees
                    .filter { overlappingPropsHasFees.contains(it.id) }
                    .traverse { it.toRecord() }

                if (propsHasFeeRecords.isNotEmpty()) {
                    !CompetitionPropertiesHasFeeRepo.create(propsHasFeeRecords).orDie()
                }

                // PROPERTIES HAS NAMED PARTICIPANT
                val overlappingPropsHasNamedParticipants =
                    !CompetitionPropertiesHasNamedParticipantRepo.getOverlaps(data.competitionPropertiesHasNamedParticipants.map { it.competitionProperties to it.namedParticipant })
                        .orDie()

                val propsHasNamedParticipantRecords =
                    !data.competitionPropertiesHasNamedParticipants
                        .filter { propsHasNP -> overlappingPropsHasNamedParticipants.any { it.competitionProperties == propsHasNP.competitionProperties && it.namedParticipant == propsHasNP.namedParticipant } }
                        .traverse { it.toRecord() }

                if (propsHasNamedParticipantRecords.isNotEmpty()) {
                    !CompetitionPropertiesHasNamedParticipantRepo.create(propsHasNamedParticipantRecords).orDie()
                }


                unit
            }
    }
}