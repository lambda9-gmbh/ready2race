package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.qrCodeApp.control.ParticipantQrAssignmentRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.GroupedParticipantQrAssignmentDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object ParticipantQrAssignmentService {
    
    fun getGroupedParticipants(eventId: UUID, clubId: UUID): App<ServiceError, ApiResponse> = KIO.comprehension {
        val participants = !ParticipantQrAssignmentRepo.findByEventAndClub(eventId, clubId).orDie()
        
        val grouped = participants
            .groupBy { it.competitionRegistration }
            .map { (competitionRegistration, participantList) ->
                GroupedParticipantQrAssignmentDto(
                    competitionRegistration = competitionRegistration,
                    competitionName = participantList.firstOrNull()?.competitionName ?: "",
                    participants = participantList
                )
            }
        
        KIO.ok(ApiResponse.ListDto(grouped))
    }
}