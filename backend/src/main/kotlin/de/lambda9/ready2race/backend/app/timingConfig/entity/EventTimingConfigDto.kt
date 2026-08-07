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
)
