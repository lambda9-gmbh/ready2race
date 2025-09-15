package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetupTemplates

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.CompetitionSetupTemplateRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup.*
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataCompetitionSetupTemplatesExport(
    val competitionSetupTemplates: List<CompetitionSetupTemplateExport>,
    val competitionSetupGroups: List<CompetitionSetupGroupExport>,
    val competitionSetupGroupStatisticEvaluation: List<CompetitionSetupGroupStatisticEvaluationExport>,
    val competitionSetupMatches: List<CompetitionSetupMatchExport>,
    val competitionSetupParticipants: List<CompetitionSetupParticipantExport>,
    val competitionSetupPlaces: List<CompetitionSetupPlaceExport>,
    val competitionSetupRounds: List<CompetitionSetupRoundExport>,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val templates = !CompetitionSetupTemplateRepo.all().orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            // get by templates
            val rounds = !CompetitionSetupRoundRepo.getBySetupIds(templates.map { it.id }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val roundIds = rounds.map { it.id }

            // get by rounds
            val evaluations = !CompetitionSetupGroupStatisticEvaluationRepo.get(roundIds).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            // get by rounds
            val matches = !CompetitionSetupMatchRepo.get(roundIds).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            // get by rounds
            val places = !CompetitionSetupPlaceRepo.get(roundIds).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            // get by groupIds in matches
            val groups = !CompetitionSetupGroupRepo.get(matches.mapNotNull { it.competitionSetupGroup }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            // get by matches and groups
            val participants =
                !CompetitionSetupParticipantRepo.get((matches.map { it.id } + groups.map { it.id })).orDie()
                    .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataCompetitionSetupTemplatesExport(
                competitionSetupTemplates = templates,
                competitionSetupGroups = groups,
                competitionSetupGroupStatisticEvaluation = evaluations,
                competitionSetupMatches = matches,
                competitionSetupParticipants = participants,
                competitionSetupPlaces = places,
                competitionSetupRounds = rounds
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(
                File(
                    name = getWebDavDataJsonFileName(WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES),
                    bytes = json
                )
            )
        }

        fun importData(data: DataCompetitionSetupTemplatesExport): App<WebDAVError.WebDAVImportNextError, Unit> =
            KIO.comprehension {

                // COMPETITION SETUP TEMPLATES
                val overlappingTemplates = !CompetitionSetupTemplateRepo
                    .getOverlapIds(data.competitionSetupTemplates.map { it.id })
                    .orDie()
                val templateRecords = !data.competitionSetupTemplates
                    .filter { !overlappingTemplates.contains(it.id) }
                    .traverse { it.toRecord() }

                if (templateRecords.isNotEmpty()) {
                    !CompetitionSetupTemplateRepo.create(templateRecords).orDie()
                }

                // COMPETITION SETUP ROUNDS
                val overlappingRounds = !CompetitionSetupRoundRepo
                    .getOverlapIds(data.competitionSetupRounds.map { it.id })
                    .orDie()
                val roundRecords = !data.competitionSetupRounds
                    .filter { !overlappingRounds.contains(it.id) }
                    .traverse { it.toRecord() }

                if (roundRecords.isNotEmpty()) {
                    !CompetitionSetupRoundRepo.create(roundRecords).orDie()
                }

                // COMPETITION SETUP GROUP STATISTIC EVALUATIONS
                val overlappingEvaluations = !CompetitionSetupGroupStatisticEvaluationRepo
                    .getOverlaps(data.competitionSetupGroupStatisticEvaluation.map { it.competitionSetupRound to it.name })
                    .orDie()
                val evaluationRecords = !data.competitionSetupGroupStatisticEvaluation
                    .filter { evaluation -> overlappingEvaluations.any { it.competitionSetupRound == evaluation.competitionSetupRound && it.name == evaluation.name } }
                    .traverse { it.toRecord() }

                if (evaluationRecords.isNotEmpty()) {
                    !CompetitionSetupGroupStatisticEvaluationRepo.create(evaluationRecords).orDie()
                }

                // COMPETITION SETUP MATCHES
                val existingMatchIds = !CompetitionSetupMatchRepo
                    .getOverlapIds(data.competitionSetupMatches.map { it.id })
                    .orDie()
                val matchRecords = !data.competitionSetupMatches
                    .filter { match -> !existingMatchIds.contains(match.id) }
                    .traverse { it.toRecord() }
                if (matchRecords.isNotEmpty()) {
                    !CompetitionSetupMatchRepo.create(matchRecords).orDie()
                }

                // COMPETITION SETUP PLACES
                val overlappingPlaces = !CompetitionSetupPlaceRepo
                    .getOverlaps(data.competitionSetupPlaces.map { it.competitionSetupRound to it.roundOutcome })
                    .orDie()
                val placeRecords = !data.competitionSetupPlaces
                    .filter { place -> overlappingPlaces.any { it.competitionSetupRound == place.competitionSetupRound && it.roundOutcome == place.roundOutcome } }
                    .traverse { it.toRecord() }
                if (placeRecords.isNotEmpty()) {
                    !CompetitionSetupPlaceRepo.create(placeRecords).orDie()
                }

                // COMPETITION SETUP GROUPS
                val overlappingGroupIds = !CompetitionSetupGroupRepo
                    .getOverlapIds(data.competitionSetupGroups.map { it.id }).orDie()
                val groupRecords = !data.competitionSetupGroups
                    .filter { group -> !overlappingGroupIds.contains(group.id) }
                    .traverse { it.toRecord() }
                if (groupRecords.isNotEmpty()) {
                    !CompetitionSetupGroupRepo.create(groupRecords).mapError {
                        WebDAVError.InsertFailed(
                            table = "CompetitionSetupGroup",
                            errorMsg = it.stackTraceToString()
                        )
                    }
                }


                // COMPETITION SETUP PARTICIPANTS
                val overlappingParticipantIds = !CompetitionSetupParticipantRepo
                    .getOverlapIds(data.competitionSetupParticipants.map { it.id })
                    .orDie()
                val participantRecords = !data.competitionSetupParticipants
                    .filter { participant -> !overlappingParticipantIds.contains(participant.id) }
                    .traverse { it.toRecord() }
                if (participantRecords.isNotEmpty()) {
                    !CompetitionSetupParticipantRepo.create(participantRecords).mapError {
                        WebDAVError.InsertFailed(
                            table = "CompetitionSetupParticipant",
                            errorMsg = it.stackTraceToString()
                        )
                    }
                }

                unit
            }
    }
}