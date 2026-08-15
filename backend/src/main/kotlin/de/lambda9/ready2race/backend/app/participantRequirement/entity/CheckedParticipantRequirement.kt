package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

/**
 * Eine abgehakte Bedingung einer Person.
 *
 * Seit V202608141900 kann dieselbe Bedingung mehrfach vorkommen - je Tag und/oder je Wettkampf
 * eine Zeile. [eventDayId] und [competitionId] sagen, welchen Bezugsrahmen die Zeile abdeckt;
 * null heißt "ohne diese Einschränkung eingetragen". Wer nur wissen will, ob überhaupt etwas
 * abgehakt ist, ignoriert beide Felder wie vorher. Wer wie die Scan-App an der Waage einen
 * bestimmten Wettkampf im Blick hat, vergleicht sie nach der Regel aus
 * `RequirementScopeLogic.covers`.
 *
 * Beide Felder haben eine Vorbelegung, damit die Massen-Pflege im Verwaltungs-UI ihre
 * Transfer-Listen weiterhin ohne Dimensionen bauen kann.
 */
data class CheckedParticipantRequirement(
    val id: UUID,
    val note: String?,
    val eventDayId: UUID? = null,
    val competitionId: UUID? = null,
)
