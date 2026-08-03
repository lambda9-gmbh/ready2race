package de.lambda9.ready2race.backend.app.liveDashboard.entity

/**
 * Was beim Beenden eines Laufs mit den Mannschaften ohne Ergebnis geschieht. Der Name landet als
 * Ausscheidungsgrund in `competition_match_team.failed_reason`.
 *
 * Eine Abmeldung ist bewusst keine Option: die kommt allein aus dem Regattabüro.
 */
enum class OpenResultHandling {
    DNS,
    DNF,
    DSQ,
}
