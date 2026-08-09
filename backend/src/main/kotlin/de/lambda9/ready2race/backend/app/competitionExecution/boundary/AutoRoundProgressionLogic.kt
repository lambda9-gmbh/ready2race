package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches

/**
 * Die beiden Entscheidungen der Folgerunden-Automatik, ohne Datenbank: Gilt sie für diesen
 * Wettkampf, und ist die laufende Runde durch?
 *
 * Bewusst rein und getrennt vom Service. An dieser Stelle hängt jedes Wettkampfformat — K.-o.,
 * Vor-, Zwischen- und Finalrunde, Qualifikation mit Freilosen —, und jedes davon lässt sich hier
 * als eigener Fall festnageln, ohne dafür eine Regatta in einer Datenbank aufzubauen.
 *
 * Was hier NICHT steht: wie die Folgerunde aussieht. Das rechnet unverändert
 * [CompetitionExecutionService.createNewRound] aus der Kette der Setup-Runden — formatunabhängig,
 * getestet und von dieser Automatik unberührt.
 */
object AutoRoundProgressionLogic {

    /**
     * Welche Einstellung für diesen Wettkampf gilt. `null` am Wettkampf heißt „der Veranstaltung
     * folgen"; das ist der Vorgabezustand und derselbe Vererbungsweg wie bei der
     * Zeitnahme-Konfiguration (`competition.timing_system` über `event.timing_system`).
     */
    fun effectiveAutoCreate(eventDefault: Boolean, competitionOverride: Boolean?): Boolean =
        competitionOverride ?: eventDefault

    /**
     * Ob ein einzelner Lauf erledigt ist.
     *
     * Ein Freilos ([bye]) wird nie gefahren und bekommt nie ein `finished_at` — es hält die Runde
     * trotzdem nicht auf. Ein abgesagter Lauf ist ebenfalls erledigt. Sonst zählt ausschließlich
     * der Beenden-Stempel: Vollständige Ergebnisse allein reichen nicht, weil bis zum
     * Beenden-Klick noch eine Zeitstrafe kommen kann (Entscheidung C1).
     */
    private fun matchIsDone(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean =
        bye(match, roundRequired) || match.skipped || match.finishedAt != null

    /**
     * Ein Freilos: ein einziges Boot in einer nicht erforderlichen Runde. In einer erforderlichen
     * Runde wird auch allein gefahren (Zeitfahren) — dort ist ein einzelnes Boot kein Freilos.
     */
    private fun bye(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean =
        !roundRequired && match.teams.size == 1

    /**
     * Ob in einem Lauf alle Plätze vergeben sind. Wörtlich dieselbe Bedingung wie in
     * `CompetitionExecutionService.checkRoundCreation`, der Prüfung hinter dem Knopf „Nächste Runde
     * erstellen": die Plätze müssen die Folge `1..n` über alle Boote enthalten, die überhaupt
     * gewertet werden. Abgemeldete, ausgeschiedene und ausgefallene Boote (DNF, Disqualifikation,
     * Nichtantritt) zählen nicht mit — für sie kommt kein Ergebnis mehr.
     */
    private fun placesAreSet(match: CompetitionMatchWithTeams): Boolean {
        val scoring = match.teams.filter { !it.deregistered && !it.failed && !it.out }
        return match.teams.map { it.place }.containsAll((1..scoring.size).toList())
    }

    /**
     * Ob die Runde als Ganzes durch ist — die Bedingung, die die Folgerunde auslöst.
     *
     * Eine Runde ohne Läufe ist ausdrücklich nicht abgeschlossen, sondern noch gar nicht gesetzt.
     * Ohne diesen Fall erklärte die Automatik jede leere Runde für fertig und liefe die ganze
     * Kette in einem Rutsch durch.
     */
    fun roundIsComplete(round: CompetitionSetupRoundWithMatches): Boolean =
        round.matches.isNotEmpty() &&
            round.matches.all { match ->
                (bye(match, round.required) || match.skipped) || (match.finishedAt != null && placesAreSet(match))
            }
}
