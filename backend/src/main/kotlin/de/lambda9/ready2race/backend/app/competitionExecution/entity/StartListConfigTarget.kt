package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

/**
 * Mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 *
 * Gegenstueck zu [de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget] auf der
 * Export-Seite und aus demselben Grund zweigeteilt: RaceClocker braucht pro Wettkampf zwei Rennen, und
 * das Zeitfahren-Rennen darf keine Lauf-Spalte bekommen, sonst kippt es in den Wave-Modus und verliert
 * den Countdown. [isQualification] waehlt zwischen den beiden Presets.
 *
 * Der Rueckfall auf [roundsConfig] ist kein Komfort, sondern der Grund, warum Webscorer nur ein Preset
 * braucht: dort gibt es die Zweiteilung nicht. Umgekehrt gibt es keinen Rueckfall -- ein Laeufe-Rennen
 * mit dem Zeitfahren-Preset zu bestuecken wuerde die Lauf-Zuordnung verlieren.
 */
data class StartListConfigTarget(
    val isQualification: Boolean,
    val qualificationConfig: UUID?,
    val roundsConfig: UUID?,
) {
    val configId: UUID? get() = if (isQualification) qualificationConfig ?: roundsConfig else roundsConfig
}
