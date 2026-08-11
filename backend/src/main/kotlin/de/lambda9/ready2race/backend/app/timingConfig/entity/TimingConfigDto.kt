package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

data class TimingConfigDto(
    val timingSystem: TimingSystem?,
    val raceQualification: UUID?,
    val raceRounds: UUID?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
    /**
     * Ob der Ablauf dieses Wettkampfs eine Qualifikationsrunde enthaelt. Steht hier und nicht in einer
     * eigenen Abfrage, weil der Zeitnahme- und der Durchfuehrungs-Tab diese Konfiguration ohnehin beide
     * laden: nur so kennen beide den Zustand ohne zusaetzlichen Roundtrip. Das Frontend entscheidet
     * daran, ob es das fehlende Qualifikations-Preset und das fehlende Zeitfahren-Rennen anmahnt --
     * ohne Qualifikationsrunde werden beide nie gebraucht, mit einer sind sie Pflicht (siehe
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget]).
     */
    val hasQualificationRound: Boolean,
    /**
     * Die Zeitnahme-Voreinstellung der Veranstaltung: System und die beiden Dateiformate erben
     * Wettkaempfe von dort (null oben = geerbt); diese Felder zeigen der Oberflaeche, WAS geerbt
     * wuerde. Die Rennen-Zuordnung erbt NICHT mehr (seit 11.08.2026 nur noch pro Rennen am
     * Wettkampf, kein Veranstaltungs-Default) - deshalb kein eventRaceQualification/-Rounds hier.
     */
    val eventTimingSystem: TimingSystem?,
    val eventStartlistConfigQualification: UUID?,
    val eventStartlistConfigRounds: UUID?,
    val eventResultImportConfig: UUID?,
)
