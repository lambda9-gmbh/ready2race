package de.lambda9.ready2race.backend.app.timingConfig.entity

/**
 * Die Zeitnahme-Voreinstellung einer Veranstaltung: gilt fuer alle Wettkaempfe ohne eigene Werte
 * (siehe [TimingConfigDto] - dort sind die Wettkampf-Spalten der Override). Nur System und die
 * beiden RaceClocker-URLs sind veranstaltungsweit sinnvoll; die Spalten-Presets haengen an der
 * konkreten Startliste und bleiben pro Wettkampf.
 */
data class EventTimingConfigDto(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    /**
     * Die Wettkaempfe, die dieser Voreinstellung nicht folgen. Ohne sie waere die Voreinstellung eine
     * Einstellung, deren Reichweite man nicht sieht: wer hier eine Adresse aendert, muss wissen,
     * welche Wettkaempfe davon unberuehrt bleiben.
     */
    val deviatingCompetitions: List<CompetitionTimingDeviationDto>,
)
