package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

/**
 * Die Zeitnahme-Voreinstellung einer Veranstaltung: gilt fuer alle Wettkaempfe ohne eigene Werte
 * (siehe [TimingConfigDto] - dort sind die Wettkampf-Spalten der Override). System, Startlisten-
 * Export und Rennergebnisse-Import werden von allen Wettkaempfen einer Regatta geteilt.
 *
 * Die konkrete Rennen-Zuordnung (Zeitfahren-/Laeufe-Rennen) steht dagegen NICHT mehr hier: sie wird
 * seit dem 11.08.2026 ausschliesslich pro Rennen am Wettkampf gesetzt (RaceClockerRaceAssignments),
 * ein Veranstaltungs-Default dafuer entfaellt.
 */
data class EventTimingConfigDto(
    val timingSystem: TimingSystem?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
    val autoPull: Boolean,
    val intervalActiveSeconds: Int,
    val intervalUpcomingSeconds: Int,
    val watchBeforeMinutes: Int,
    val watchAfterMinutes: Int,
    /**
     * Die Wettkaempfe, die dieser Voreinstellung nicht folgen. Ohne sie waere die Voreinstellung eine
     * Einstellung, deren Reichweite man nicht sieht: wer hier eine Adresse aendert, muss wissen,
     * welche Wettkaempfe davon unberuehrt bleiben.
     */
    val deviatingCompetitions: List<CompetitionTimingDeviationDto>,
)
