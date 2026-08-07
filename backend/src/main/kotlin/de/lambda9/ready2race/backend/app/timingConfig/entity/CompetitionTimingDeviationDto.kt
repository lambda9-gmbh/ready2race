package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

/**
 * Ein Wettkampf, der die Zeitnahme-Voreinstellung seiner Veranstaltung nicht erbt, sondern eigene
 * Werte gesetzt hat. Nur die drei vererbbaren Felder stehen hier; die Spalten-Presets bleiben ohnehin
 * pro Wettkampf und sind deshalb keine Abweichung.
 *
 * Gedacht für die Veranstaltungs-Ansicht: wer dort die Voreinstellung pflegt, soll sehen, welche
 * Wettkämpfe ihr nicht folgen — sonst ändert man die Adresse für alle und wundert sich am Renntag,
 * warum drei Wettkämpfe weiterhin ins alte Rennen zeigen. Ein leeres [timingSystem] mit gesetzter URL
 * ist ein Teil-Override und genauso gemeint: der Wettkampf erbt das System und hat ein eigenes Rennen.
 */
data class CompetitionTimingDeviationDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
)
