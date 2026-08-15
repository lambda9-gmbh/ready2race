package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.*

data class ParticipantRequirementForEventDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val active: Boolean,
    val checkInApp: Boolean,
    val publiclyVisible: Boolean,
    // Die beiden Schalter aus V202608141900 gehören hier mit hinein, weil die Scan-App an der
    // Waage sonst nicht unterscheiden kann, wofür sie gerade abhakt: eine Bedingung mit
    // `perCompetition` braucht den Wettkampf, sonst landet die Bestätigung ohne Bezug und deckt
    // bewusst keinen Lauf ab (siehe RequirementScopeLogic.covers).
    val perEventDay: Boolean,
    val perCompetition: Boolean,
    val requirements: List<NamedParticipantRequirementForEventDto>
)