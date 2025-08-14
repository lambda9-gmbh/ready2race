package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantQrAssignmentViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_QR_ASSIGNMENT_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ParticipantQrAssignmentRepo {

    fun findByEventAndClub(eventId: UUID, clubId: UUID?): JIO<List<ParticipantQrAssignmentViewRecord>> = Jooq.query {
        val query = selectFrom(PARTICIPANT_QR_ASSIGNMENT_VIEW)
            .where(PARTICIPANT_QR_ASSIGNMENT_VIEW.EVENT_ID.eq(eventId))

        if (clubId != null) {
            query.and(PARTICIPANT_QR_ASSIGNMENT_VIEW.CLUB_ID.eq(clubId))
        }

        query.fetch()
    }
}