package de.lambda9.ready2race.backend.app.event.entity

import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Die Grenzen des automatischen Datenabgleichs auf der Durchführungsseite.
 *
 * Sie stehen hier und nicht je Request, weil Anlegen und Bearbeiten einer Veranstaltung dieselbe
 * Regel brauchen und die Oberfläche dieselben Zahlen als Feldgrenzen zeigt (siehe autoRefresh.ts
 * im Frontend). Zusätzlich erzwingt sie ein Check-Constraint in der Datenbank - das greift auch
 * für Seeds und Handkorrekturen, die nicht durch die API kommen.
 *
 * Unter 5 Sekunden wäre der Takt schneller, als ein Schiedsrichter ein Ergebnis eintippt, und
 * belastete den Server ohne Gewinn. Über 60 Sekunden ist es kein Abgleich mehr, sondern ein
 * gelegentliches Nachschauen - dann kann die Automatik auch aus bleiben.
 */
object ExecutionAutoRefresh {

    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 60
    const val DEFAULT_SECONDS = 5

    /**
     * Geprüft wird immer, auch bei ausgeschaltetem Abgleich: Der Takt bleibt gespeichert, und wer
     * ihn später einschaltet, soll nicht auf einen Wert stoßen, den niemand mehr geprüft hat.
     */
    fun validateSeconds(value: Int, field: String = "executionAutoRefreshSeconds"): ValidationResult =
        if (value < MIN_SECONDS || value > MAX_SECONDS) {
            ValidationResult.Invalid.Message { "$field must be between $MIN_SECONDS and $MAX_SECONDS seconds" }
        } else {
            ValidationResult.Valid
        }
}
