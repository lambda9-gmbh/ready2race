package de.lambda9.ready2race.backend.app.event.entity

/**
 * Ab welchem Zustand ein Lauf als Ergebnis auf den öffentlichen Ansichten erscheint:
 * [FINISHED_ONLY] nur beendete Läufe (`competition_match.finished_at` gesetzt), [RESULTS_COMPLETE]
 * zusätzlich die, deren Boote alle gewertet sind
 * (`LiveDashboardMatchState.AWAITING_FINISH`).
 *
 * Die Einstellung sitzt an der Veranstaltung und nicht in `info_view_configuration.filters`,
 * obwohl dort schon Einstellungen je Ansicht liegen (Limits, `showCountdown`). Zwei Gründe:
 * 1. Der Endpoint `/latest-match-results` bedient auch die öffentliche Ergebnisseite, die gar
 *    keine `info_view_configuration`-Zeile hat. Eine Einstellung je Ansicht könnte ihn nicht
 *    erreichen — dieselbe Veranstaltung veröffentlichte dann je nach geöffneter Seite zu
 *    unterschiedlichen Zeitpunkten.
 * 2. "Ist dieser Stand final genug zum Veröffentlichen?" ist eine Aussage über die Regatta, keine
 *    Darstellungsvorliebe einer einzelnen Anzeige. Kiosk und Athleten-Anzeige sollen keine
 *    unterschiedlichen Wahrheiten zeigen.
 *
 * Vorbild ist `Event.showBreaksOnPublicBoards` (Migration V202608041900): ebenfalls ein Schalter
 * je Veranstaltung, der regelt, was öffentliche Anzeigen zeigen dürfen, und ebenfalls in
 * `EventInfoService` ausgewertet.
 *
 * Voreinstellung [FINISHED_ONLY], Begründung in Migration V202608061200.
 */
enum class PublicResultsVisibility { FINISHED_ONLY, RESULTS_COMPLETE }
