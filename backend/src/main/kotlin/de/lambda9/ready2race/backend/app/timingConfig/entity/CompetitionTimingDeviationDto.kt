package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

/**
 * Ein Wettkampf, der die Zeitnahme-Voreinstellung seiner Veranstaltung nicht erbt, sondern eigenes
 * System oder eigene Dateiformate gesetzt hat.
 *
 * Gedacht für die Veranstaltungs-Ansicht: wer dort die Voreinstellung pflegt, soll sehen, welche
 * Wettkämpfe ihr nicht folgen. Die Rennen-Zuordnung steht bewusst NICHT mehr hier — sie wird seit
 * dem 11.08.2026 pro Rennen zugewiesen und dort auch angezeigt (RaceClockerRaceAssignments), ein
 * Veranstaltungs-Default existiert nicht mehr, von dem man abweichen könnte.
 */
data class CompetitionTimingDeviationDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    val timingSystem: TimingSystem?,
    val startlistConfig: UUID?,
    val resultImportConfig: UUID?,
)
