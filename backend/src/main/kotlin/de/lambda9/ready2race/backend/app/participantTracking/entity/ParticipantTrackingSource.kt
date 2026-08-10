package de.lambda9.ready2race.backend.app.participantTracking.entity

/**
 * Wie ein Eintrag entstanden ist. Die Unterscheidung ist keine Buchhaltung, sondern der Kern der
 * Anforderung: ein von Hand nachgetragener Check-in darf nirgends wie ein Scan am Steg aussehen.
 *
 * Ob ein Eintrag seither *berichtigt* wurde, steht hier bewusst nicht - das ist eine zweite Frage
 * und beantwortet sie die Änderungsspur (`participant_tracking_change`). Ein per QR erfasster,
 * später korrigierter Eintrag bleibt [QR] und trägt zusätzlich seine Spur.
 */
enum class ParticipantTrackingSource {
    QR,
    MANUAL
}
