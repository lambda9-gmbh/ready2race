package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.UUID

/**
 * Ein Wettkampf, für den an der Waage abgehakt werden kann - schmal gehalten, weil die Scan-App
 * daraus nur eine Auswahlliste baut.
 */
data class ParticipantScanCompetitionDto(
    val id: UUID,
    val identifier: String?,
    val name: String,
    val shortName: String?,
)

/**
 * Der Bezugsrahmen, in dem die Scan-App gerade abhakt: der heutige Wettkampftag und die
 * Wettkämpfe, in denen die gescannte Person gemeldet ist.
 *
 * Warum beides zusammen und nicht aus dem vorhandenen Bedingungs-Aufruf abgeleitet: Eine
 * Bedingung mit `perCompetition` braucht den Wettkampf, den nur die Person kennt (wer in zwei
 * Wettkämpfen startet, muss zweimal auf die Waage), und eine mit `perEventDay` hängt am Tag,
 * den ausschließlich der Server bestimmen darf - das Tablet an der Waage kann eine falsch
 * gestellte Uhr haben, und `eventDayOf` entscheidet ohnehin nach denselben Regeln wie das
 * Speichern der Bestätigung.
 *
 * [todayEventDayId] ist null, wenn sich der heutige Tag keinem Wettkampftag zuordnen lässt -
 * dann ist an einer tagesbezogenen Bedingung nichts abgedeckt, und die App sagt das auch.
 */
data class ParticipantScanScopeDto(
    val todayEventDayId: UUID?,
    val competitions: List<ParticipantScanCompetitionDto>,
)
