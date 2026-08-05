package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import java.util.UUID

/**
 * Mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 *
 * Gegenstueck zu [de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget] auf der
 * Export-Seite und aus demselben Grund zweigeteilt: RaceClocker braucht pro Wettkampf zwei Rennen, und
 * das Zeitfahren-Rennen darf keine Lauf-Spalte bekommen, sonst kippt es in den Wave-Modus und verliert
 * den Countdown. [isQualification] waehlt zwischen den beiden Presets.
 *
 * Der Rueckfall auf [roundsConfig] ist nur fuer Webscorer richtig, das die Zweiteilung nicht kennt und
 * nur den Runden-Slot fuellt. Fuer RaceClocker waere er falsch: ein leerer Zeitfahren-Slot wuerde die
 * Startliste mit dem Laeufe-Preset und damit mit Lauf-Spalte exportieren, und genau die kippt das
 * Zeitfahren-Rennen in den Wave-Modus. Deshalb faellt [timingSystem] `== RACECLOCKER` nicht zurueck,
 * sondern liefert `null` -- das fuehrt zu
 * [de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError.NotConfigured] und
 * einer Fehlermeldung, die auf den Zeitnahme-Tab verweist, statt eine falsche Startliste zu erzeugen.
 */
data class StartListConfigTarget(
    val isQualification: Boolean,
    val timingSystem: TimingSystem?,
    val qualificationConfig: UUID?,
    val roundsConfig: UUID?,
) {
    val configId: UUID? get() = when {
        !isQualification -> roundsConfig
        // Kein Rueckfall bei RaceClocker: das Laeufe-Preset traegt die Lauf-Spalte, und die kippt
        // das Zeitfahren-Rennen in den Wave-Modus -- der Countdown waere am Start weg. Lieber ein
        // klarer Fehler, der auf den Zeitnahme-Tab verweist.
        timingSystem == TimingSystem.RACECLOCKER -> qualificationConfig
        // Webscorer kennt die Zweiteilung nicht und fuellt nur den Runden-Slot; ohne gesetztes
        // System bleibt es beim bisherigen, durchlaessigen Verhalten.
        else -> qualificationConfig ?: roundsConfig
    }
}
