package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.time.LocalTime

/**
 * Eine Zwischenzeit-Marke aus dem RaceClocker-Feed: der frei vergebene Spaltenname und der
 * Tageszeit-Stempel, zu dem das Boot die Marke passiert hat.
 *
 * Der Stempel bleibt hier bewusst eine Tageszeit: Erst beim Schreiben wird daraus - gegen den
 * gemessenen Start der Zeile - die kumulierte Fahrzeit, die in der Datenbank landet
 * (`competition_match_team_lap.lap_millis`).
 */
data class RaceClockerLapMark(
    val name: String,
    val time: LocalTime,
)
