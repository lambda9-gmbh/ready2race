package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.toRecord
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionError
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object SubstitutionService {

    fun addSubstitution(
        userId: UUID,
        request: SubstitutionRequest
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        SubstitutionRepo.create(record).orDie().map { ApiResponse.Created(it) }
    }

    fun updateSubstitution(
        userId: UUID,
        substitutionId: UUID,
        request: SubstitutionRequest,
    ): App<SubstitutionError, ApiResponse.NoData> =
        SubstitutionRepo.update(substitutionId) {
            competitionRegistration = request.competitionRegistrationId
            competitionSetupRound = request.competitionSetupRound
            participantOut = request.participantOut
            participantIn = request.participantIn
            reason = request.reason
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { SubstitutionError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteSubstitution(
        id: UUID,
    ): App<SubstitutionError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !SubstitutionRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(SubstitutionError.NotFound)
        } else {
            noData
        }
    }
}