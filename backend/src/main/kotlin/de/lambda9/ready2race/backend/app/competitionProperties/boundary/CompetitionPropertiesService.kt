package de.lambda9.ready2race.backend.app.competitionProperties.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesChallengeConfigRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionChallengeConfigRequest
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequest
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.CompetitionSetupTemplateRepo
import de.lambda9.ready2race.backend.app.fee.control.FeeRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionPropertiesChallengeConfig
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesChallengeConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object CompetitionPropertiesService {

    private fun checkNamedParticipantsExisting(
        namedParticipants: List<UUID>
    ): App<CompetitionPropertiesError, Unit> = KIO.comprehension {
        if (namedParticipants.isEmpty()) {
            unit
        } else {
            val found = !NamedParticipantRepo.getIfExist(namedParticipants).orDie()
            val notFound = namedParticipants.filter { id -> found.none { it.id == id } }
            if (notFound.isNotEmpty()) {
                KIO.fail(CompetitionPropertiesError.NamedParticipantsUnknown(notFound))
            } else {
                unit
            }
        }
    }

    private fun checkFeesExisting(
        fees: List<UUID>
    ): App<CompetitionPropertiesError, Unit> = KIO.comprehension {
        if (fees.isEmpty()) {
            unit
        } else {
            val found = !FeeRepo.getIfExist(fees).orDie()
            val notFound = fees.filter { id -> found.none { it.id == id } }
            if (notFound.isNotEmpty()) {
                KIO.fail(CompetitionPropertiesError.FeesUnknown(notFound))
            } else {
                unit
            }
        }
    }

    private fun checkCompetitionCategoryExisting(
        competitionCategory: UUID?
    ): App<CompetitionPropertiesError, Unit> =
        if (competitionCategory == null) {
            unit
        } else {
            CompetitionCategoryRepo.exists(competitionCategory).orDie()
                .onFalseFail { CompetitionPropertiesError.CompetitionCategoryUnknown }
        }

    fun checkCompetitionSetupTemplateExisting(
        competitionSetupTemplateId: UUID?
    ): App<CompetitionPropertiesError, Unit> = if (competitionSetupTemplateId == null) {
        unit
    } else {
        CompetitionSetupTemplateRepo.exists(competitionSetupTemplateId).orDie()
            .onFalseFail { CompetitionPropertiesError.CompetitionSetupTemplateUnknown }
    }

    fun checkRequestReferences(
        request: CompetitionPropertiesRequest,
    ): App<CompetitionPropertiesError, Unit> = KIO.comprehension {

        !checkNamedParticipantsExisting(request.namedParticipants.map { it.namedParticipant })
        !checkFeesExisting(request.fees.map { it.fee })
        !checkCompetitionCategoryExisting(request.competitionCategory)

        unit
    }

    fun addCompetitionPropertiesReferences(
        namedParticipants: Collection<CompetitionPropertiesHasNamedParticipantRecord>,
        fees: Collection<CompetitionPropertiesHasFeeRecord>,
        challengeConfig: CompetitionPropertiesChallengeConfigRecord?,
    ): App<Nothing, Unit> = KIO.comprehension {

        !CompetitionPropertiesHasNamedParticipantRepo.create(namedParticipants).orDie()
        !CompetitionPropertiesHasFeeRepo.create(fees).orDie()

        if (challengeConfig != null) {
            !CompetitionPropertiesChallengeConfigRepo.create(challengeConfig).orDie()
        }

        unit
    }

    // delete and re-add the named participant and fee entries
    fun updateCompetitionPropertiesReferences(
        competitionPropertiesId: UUID,
        namedParticipants: Collection<CompetitionPropertiesHasNamedParticipantRecord>,
        fees: Collection<CompetitionPropertiesHasFeeRecord>,
        challengeConfig: CompetitionChallengeConfigRequest?,
    ): App<Nothing, Unit> = KIO.comprehension {

        !CompetitionPropertiesHasNamedParticipantRepo.deleteByCompetitionPropertiesId(competitionPropertiesId).orDie()
        !CompetitionPropertiesHasFeeRepo.deleteManyByCompetitionProperties(competitionPropertiesId).orDie()

        !addCompetitionPropertiesReferences(namedParticipants, fees, null)

        challengeConfig?.let {
            !CompetitionPropertiesChallengeConfigRepo.update(competitionPropertiesId) {
                resultConfirmationImageRequired = it.resultConfirmationImageRequired
                startAt = it.startAt
                endAt = it.endAt
            }.orDie()
        }

        unit
    }
}