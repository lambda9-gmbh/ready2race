package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.*

data class ParticipantRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val publicNote: String?,
    val optional: Boolean,
    val checkInApp: Boolean,
    val publiclyVisible: Boolean,
    // Zwei Schalter statt einer Aufzählung, siehe Migration V202608141900: aus beiden ergeben
    // sich die vier Geltungsbereiche (je Veranstaltung, je Tag, je Wettkampf, je Wettkampf
    // und Tag), ohne dass für den vierten ein neuer Aufzählungswert nötig wäre.
    val perEventDay: Boolean,
    val perCompetition: Boolean,
    val checkEarliestMinutesBefore: Int?,
    val checkLatestMinutesBefore: Int?,
)