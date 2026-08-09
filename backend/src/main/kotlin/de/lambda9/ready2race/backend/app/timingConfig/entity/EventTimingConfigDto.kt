package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

/**
 * Die Zeitnahme-Voreinstellung einer Veranstaltung: gilt fuer alle Wettkaempfe ohne eigene Werte
 * (siehe [TimingConfigDto] - dort sind die Wettkampf-Spalten der Override). Sie umfasst dieselben
 * Felder wie der Wettkampf, weil in der Praxis einer Regatta alle Wettkaempfe in dieselben Rennen
 * im Fremdsystem exportiert werden - System, beide Ergebnis-Adressen sowie Startlisten-Export und
 * Rennergebnisse-Import.
 */
data class EventTimingConfigDto(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
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
