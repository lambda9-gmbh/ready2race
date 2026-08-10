package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

data class TimingConfigDto(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
    /**
     * Ob der Ablauf dieses Wettkampfs eine Qualifikationsrunde enthaelt. Steht hier und nicht in einer
     * eigenen Abfrage, weil der Zeitnahme- und der Durchfuehrungs-Tab diese Konfiguration ohnehin beide
     * laden: nur so kennen beide den Zustand ohne zusaetzlichen Roundtrip. Das Frontend entscheidet
     * daran, ob es das fehlende Qualifikations-Preset und die fehlende Zeitfahren-Adresse anmahnt --
     * ohne Qualifikationsrunde werden beide nie gebraucht, mit einer sind sie Pflicht (siehe
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget]).
     */
    val hasQualificationRound: Boolean,
    /**
     * Die Zeitnahme-Voreinstellung der Veranstaltung (Migrationen V202608062100 und V202608071300):
     * RaceClocker-Rennen werden pro Veranstaltung angelegt, deshalb erben Wettkaempfe System, URLs
     * und die beiden Dateiformate von dort. Die Felder oben sind der lokale Override (null =
     * geerbt); diese hier zeigen der Oberflaeche, WAS geerbt wuerde.
     */
    val eventTimingSystem: TimingSystem?,
    val eventTimeTrialResultsUrl: String?,
    val eventHeatsResultsUrl: String?,
    val eventStartlistConfigQualification: UUID?,
    val eventStartlistConfigRounds: UUID?,
    val eventResultImportConfig: UUID?,
)
