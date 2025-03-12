package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchOutcomeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionSetupService {
    fun createCompetitionSetup(
        userId: UUID,
        competitionPropertiesId: UUID,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionSetupRepo.create(LocalDateTime.now().let { now ->
            CompetitionSetupRecord(
                competitionProperties = competitionPropertiesId,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }).orDie()

        noData
    }

    fun updateCompetitionSetup(
        request: CompetitionSetupDto,
        userId: UUID,
        key: UUID,
    ): App<CompetitionSetupError, ApiResponse.NoData> = KIO.comprehension {
        val competitionPropertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        !CompetitionSetupRepo.update(competitionPropertiesId) {
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { CompetitionSetupError.NotFound }


        !CompetitionSetupRoundRepo.delete(competitionPropertiesId).orDie()

        data class Batches(
            val rounds: MutableList<CompetitionSetupRoundRecord> = mutableListOf(),
            val matches: MutableList<CompetitionSetupMatchRecord> = mutableListOf(),
            val outcomes: MutableList<CompetitionSetupMatchOutcomeRecord> = mutableListOf()
        )

        val records = Batches()

        request.rounds.reversed().forEach { round ->
            val next = records.rounds.lastOrNull()
            val roundRecord = round.toRecord(competitionPropertiesId, next?.id)
            records.rounds.add(roundRecord)

            round.matches.forEach { match ->
                val matchRecord = match.toRecord(roundRecord.id)
                records.matches.add(matchRecord)

                match.outcomes.mapIndexed { index, weighting ->
                    val outcomeRecord = CompetitionSetupMatchOutcomeRecord(
                        competitionSetupMatch = matchRecord.id,
                        weighting = weighting,
                        ranking = index + 1
                    )
                    records.outcomes.add(outcomeRecord)
                }
            }
        }

        !CompetitionSetupRoundRepo.create(records.rounds).orDie()
        !CompetitionSetupMatchRepo.create(records.matches).orDie()
        !CompetitionSetupMatchOutcomeRepo.create(records.outcomes).orDie()

        noData
    }

    fun getCompetitionSetup(
        key: UUID,
    ): App<CompetitionSetupError, ApiResponse.Dto<CompetitionSetupDto>> = KIO.comprehension {
        val competitionPropertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        val roundRecords = !CompetitionSetupRoundRepo.get(competitionPropertiesId).orDie()
        val matchRecords = !CompetitionSetupMatchRepo.get(roundRecords.map { it.id }).orDie()
        val outcomeRecords = !CompetitionSetupMatchOutcomeRepo.get(matchRecords.map { it.id }).orDie()

        val roundDtos = roundRecords.map { round ->
            round.toDto(matchRecords.filter { match -> match.competitionSetupRound == round.id }.map { match ->
                match.toDto(outcomeRecords.filter { outcome -> outcome.competitionSetupMatch == match.id }
                    .sortedBy {
                        it.ranking
                    }.map {
                        it.weighting
                    })
            })
        }


        KIO.ok(
            ApiResponse.Dto(CompetitionSetupDto(roundDtos))
        )
    }
}