package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import org.jooq.Field

enum class ParticipantForEventSort : Sortable {
    FIRSTNAME,
    LASTNAME,
    EXTERNAL_CLUB_NAME,
    CLUB_NAME,
    YEAR,
    GENDER;

    override fun toFields(): List<Field<*>> = when (this) {
        FIRSTNAME -> listOf(PARTICIPANT_FOR_EVENT.FIRSTNAME)
        LASTNAME -> listOf(PARTICIPANT_FOR_EVENT.LASTNAME)
        EXTERNAL_CLUB_NAME -> listOf(PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME)
        CLUB_NAME -> listOf(PARTICIPANT_FOR_EVENT.CLUB_NAME)
        YEAR -> listOf(PARTICIPANT_FOR_EVENT.YEAR)
        GENDER -> listOf(PARTICIPANT_FOR_EVENT.GENDER)
    }

    fun comparator(): Comparator<ParticipantForEventDto> = when (this) {
        FIRSTNAME -> compareBy { it.firstname }
        LASTNAME -> compareBy { it.lastname }
        EXTERNAL_CLUB_NAME -> compareBy { it.externalClubName ?: "" }
        CLUB_NAME -> compareBy { it.clubName }
        YEAR -> compareBy { it.year ?: Int.MIN_VALUE }
        GENDER -> compareBy { it.gender }
    }
}