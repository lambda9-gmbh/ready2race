package de.lambda9.ready2race.backend.app.participantTracking.entity

/**
 * Ob eine Zeile der Änderungsspur einen neuen Eintrag anlegte oder einen bestehenden berichtigte.
 * Nur [UPDATE] hat einen Vorher-Stand; die Datenbank hält das über `chk_ptc_previous_matches_type`
 * fest.
 */
enum class ParticipantTrackingChangeType {
    CREATE,
    UPDATE
}
