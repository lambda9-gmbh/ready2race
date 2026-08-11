package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

data class TimingConfigDto(
    val timingSystem: TimingSystem?,
    /**
     * Das eine angewählte RaceClocker-Rennen dieses Wettkampfs — für Qualifikation und alle
     * übrigen Runden gemeinsam (seit dem 11.08.2026, RaceClocker kennt keine Startarten mehr).
     */
    val race: UUID?,
    val startlistConfig: UUID?,
    val resultImportConfig: UUID?,
    /**
     * Die Zeitnahme-Voreinstellung der Veranstaltung: System und die beiden Dateiformate erben
     * Wettkaempfe von dort (null oben = geerbt); diese Felder zeigen der Oberflaeche, WAS geerbt
     * wuerde. Die Rennen-Zuordnung erbt NICHT (seit 11.08.2026 nur noch pro Rennen am Wettkampf,
     * kein Veranstaltungs-Default) - deshalb kein eventRace hier.
     */
    val eventTimingSystem: TimingSystem?,
    val eventStartlistConfig: UUID?,
    val eventResultImportConfig: UUID?,
)
