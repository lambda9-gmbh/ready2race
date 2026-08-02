package de.lambda9.ready2race.backend.app.eventInfo.entity

/**
 * Aufgelöste Konfiguration der Athleten-Anzeige. Entsteht aus einer Zeile in
 * `info_view_configuration` vom Typ ATHLETE_BOARD oder aus den Vorgabewerten,
 * wenn keine solche Zeile existiert.
 */
data class AthleteBoardConfig(
    val runningLimit: Int,
    val upcomingLimit: Int,
    val resultsLimit: Int,
    val showCountdown: Boolean,
    val refreshIntervalSeconds: Int,
)
