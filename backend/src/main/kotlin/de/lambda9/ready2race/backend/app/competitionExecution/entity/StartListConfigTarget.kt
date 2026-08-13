package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

/**
 * Mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 *
 * Frueher zweigeteilt (Qualifikations- vs. Runden-Preset mit einer Rueckfall-Weiche je
 * Zeitnahmesystem), weil das Zeitfahren-Rennen in RaceClocker keine Lauf-Spalte vertrug. Seit
 * RaceClocker keine Startarten mehr kennt (11.08.2026), gibt es genau EIN Preset je Wettkampf
 * (mit der Veranstaltung als Vorgabe, coalesce in der Abfrage) - uebrig bleibt nur noch die
 * Unterscheidung „Lauf nicht gefunden" (null-Ergebnis der Abfrage) gegen „kein Preset
 * konfiguriert" ([configId] null), und genau dafuer existiert diese Huelle.
 */
data class StartListConfigTarget(
    val configId: UUID?,
)
