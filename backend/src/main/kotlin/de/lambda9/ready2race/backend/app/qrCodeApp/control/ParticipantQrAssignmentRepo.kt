package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.qrCodeApp.entity.ParticipantQrAssignmentDto
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_QR_ASSIGNMENT_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ParticipantQrAssignmentRepo {
    
    fun findByEventAndClub(eventId: UUID, clubId: UUID?): JIO<List<ParticipantQrAssignmentDto>> = Jooq.query {
        val query = selectFrom(PARTICIPANT_QR_ASSIGNMENT_VIEW)
            .where(PARTICIPANT_QR_ASSIGNMENT_VIEW.EVENT_ID.eq(eventId))
        
        if (clubId != null) {
            query.and(PARTICIPANT_QR_ASSIGNMENT_VIEW.CLUB_ID.eq(clubId))
        }
        
        query.fetch { record ->
            ParticipantQrAssignmentDto(
                participantId = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.PARTICIPANT_ID].toString(),
                firstname = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.FIRSTNAME] ?: "",
                lastname = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.LASTNAME] ?: "",
                qrCodeValue = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.QR_CODE_VALUE],
                namedParticipant = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.NAMED_PARTICIPANT] ?: "",
                competitionRegistration = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.COMPETITION_REGISTRATION].toString(),
                competitionName = record[PARTICIPANT_QR_ASSIGNMENT_VIEW.COMPETITION_NAME] ?: ""
            )
        }
    }
}