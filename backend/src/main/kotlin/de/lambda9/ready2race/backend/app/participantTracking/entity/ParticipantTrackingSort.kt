package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_TRACKING_VIEW
import org.jooq.Field


enum class ParticipantTrackingSort : Sortable {
    FIRSTNAME,
    LASTNAME,
    CLUB_NAME,
    EXTERNAL_CLUB_NAME,
    SCAN_TYPE,
    SCANNED_AT;

    override fun toFields(): List<Field<*>> = when (this) {
        FIRSTNAME -> listOf(PARTICIPANT_TRACKING_VIEW.FIRSTNAME)
        LASTNAME -> listOf(PARTICIPANT_TRACKING_VIEW.LASTNAME)
        CLUB_NAME -> listOf(PARTICIPANT_TRACKING_VIEW.CLUB_NAME)
        EXTERNAL_CLUB_NAME -> listOf(PARTICIPANT_TRACKING_VIEW.EXTERNAL_CLUB_NAME)
        SCAN_TYPE -> listOf(PARTICIPANT_TRACKING_VIEW.SCAN_TYPE)
        SCANNED_AT -> listOf(PARTICIPANT_TRACKING_VIEW.SCANNED_AT)
    }
}