package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.qrCodeApp.control.*
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.util.UUID

object ParticipantQrAssignmentService {

    fun getGroupedParticipants(eventId: UUID, clubId: UUID?): App<ServiceError, ApiResponse> = KIO.comprehension {
        val participants = !ParticipantQrAssignmentRepo.findByEventAndClub(eventId, clubId).orDie()

        val substitutionsForEventScoped = !SubstitutionRepo.getByEvent(
            eventId = eventId,
            clubId = clubId,
            scope = if (clubId != null) {
                Privilege.Scope.OWN
            } else {
                Privilege.Scope.GLOBAL
            }
        ).orDie()

        val actualTeams = participants.groupBy { it.competitionRegistrationId!! }
            .map { (registrationId, team) ->
                val participantsForExecution = !team.traverse { it.extendToParticipantForExecutionDto() }

                val actuallyParticipating = !CompetitionExecutionService.getActuallyParticipatingParticipants(
                    teamParticipants = participantsForExecution,
                    substitutionsForRegistration = substitutionsForEventScoped.filter { it.competitionRegistrationId == registrationId }
                )
                val participantsQrAssignment = !actuallyParticipating.traverse { p ->
                    KIO.comprehension {
                        val knownParticipant = team.find { it.participantId == p.id }
                        val qrCode = if (knownParticipant == null) {
                            !QrCodeRepo.getQrCodeByParticipant(p.id, eventId).orDie().map { it?.qrCodeId }
                        } else {
                            knownParticipant.qrCodeValue
                        }
                        p.toParticipantQrAssignmentDto(qrCode)
                    }
                }
                !team.first().toGroupedParticipantQrAssignmentDto(participantsQrAssignment)
            }

        KIO.ok(ApiResponse.ListDto(actualTeams))
    }
}