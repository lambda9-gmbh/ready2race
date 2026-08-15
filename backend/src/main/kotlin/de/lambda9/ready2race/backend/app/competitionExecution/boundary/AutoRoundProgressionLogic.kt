package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import java.time.LocalDateTime

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
     * Ob ein einzelner Lauf abgehakt ist.
     *
     * Ein Freilos ([bye]) ist unbedingt erledigt — es wird nie gefahren und bekommt nie ein
     * `finished_at`, eine Platzprüfung wäre dort sinnlos.
     *
     * Für jeden anderen Lauf — auch einen abgesagten — gilt zuerst [placesAreSet]: dieselbe
     * Platzbedingung wie beim Knopf „Nächste Runde erstellen". Ein abgesagter Lauf mit
     * ungewerteten Booten hält die Runde damit genauso auf wie beim Knopf; die frühere Ausnahme für
     * `skipped` blendete diese Prüfung aus und ließ die Automatik „durch" sagen, wo
     * `createNewRound` anschließend mit `NotAllPlacesSet` scheiterte.
     *
     * Sind die Plätze gesetzt, genügt einer von drei Wegen zum Abschluss:
     * - der Beenden-Stempel (`finished_at`) — der Normalfall nach einem gefahrenen Lauf;
     * - `skipped` — ein abgesagter Lauf findet nicht statt und bekommt deshalb nie einen
     *   Beenden-Klick; waren seine Plätze schon vor der Absage vollständig (etwa eine Korrektur,
     *   die danach zurückgenommen wurde), darf er nicht auf einen Stempel warten, der nie kommt;
     * - kein wertbares Boot mehr ([hasNoScoringTeams]) — für ihn kommt ebenfalls kein Ergebnis
     *   mehr, und niemand wird ihn je beenden.
     *
     * In jedem anderen Fall reichen vollständige Plätze allein nicht, weil bis zum Beenden-Klick
     * noch eine Zeitstrafe kommen kann (Entscheidung C1).
     */
    private fun matchIsSettled(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean {
        if (bye(match, roundRequired)) return true
        if (!placesAreSet(match)) return false
        return match.finishedAt != null || match.skipped || hasNoScoringTeams(match)
    }

    /**
     * Ein Freilos: ein einziges Boot in einer nicht erforderlichen Runde. In einer erforderlichen
     * Runde wird auch allein gefahren (Zeitfahren) — dort ist ein einzelnes Boot kein Freilos.
     *
     * Ein Freilos mit "muss gefahren werden" ([CompetitionMatchWithTeams.byeMustRace]) zählt hier
     * ausdrücklich NICHT als Freilos: Es wird gefahren, bekommt ein Ergebnis und einen
     * Beenden-Stempel — die Automatik wartet darauf wie bei jedem Lauf.
     */
    private fun bye(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean =
        !roundRequired && match.teams.size == 1 && !match.byeMustRace

    /**
     * Ob ein Lauf kein einziges wertbares Boot mehr hat — alle Boote sind abgemeldet, ausgefallen
     * oder ausgeschieden. Ein solcher Lauf wird nie gefahren: Es gibt niemanden mehr, der an den
     * Start könnte, also auch niemanden, der ihn beendet.
     */
    private fun hasNoScoringTeams(match: CompetitionMatchWithTeams): Boolean =
        match.teams.all { it.deregistered || it.failed || it.out }

    /**
     * Ob in einem Lauf alle Plätze vergeben sind — dieselbe Bedingung wie in
     * `CompetitionExecutionService.checkRoundCreation`, der Prüfung hinter dem Knopf „Nächste Runde
     * erstellen": die Plätze müssen die Folge `1..n` über alle Boote enthalten, die überhaupt
     * gewertet werden. Abgemeldete, ausgeschiedene und ausgefallene Boote (DNF, Disqualifikation,
     * Nichtantritt) zählen nicht mit — für sie kommt kein Ergebnis mehr. Für einen Lauf ganz ohne
     * wertbares Boot ist die Bedingung damit trivial erfüllt: `1..0` ist die leere Folge.
     */
    private fun placesAreSet(match: CompetitionMatchWithTeams): Boolean {
        val scoring = match.teams.filter { !it.deregistered && !it.failed && !it.out }
        return match.teams.map { it.place }.containsAll((1..scoring.size).toList())
    }

    /**
     * Ob die Runde als Ganzes durch ist — die Bedingung, die die Folgerunde auslöst.
     *
     * [matchIsSettled] ist absichtlich eine VERSCHÄRFUNG von `checkRoundCreation`, nicht dieselbe
     * Bedingung: Was die Automatik durchlässt, lässt der Knopf immer auch durch. Die Platzbedingung
     * ([placesAreSet]) ist dabei die gemeinsame Untergrenze, die für jeden Lauf gilt außer dem
     * Freilos — kein Weg zum Abschluss kommt an ihr vorbei. Oben drauf verlangt die Automatik für
     * den Normalfall zusätzlich den Beenden-Stempel (`finished_at`, Entscheidung C1); `skipped` und
     * `hasNoScoringTeams` sind keine Aufweichung dieses Zügels, sondern decken die beiden Fälle ab,
     * in denen ein Stempel prinzipiell nie kommt — abgesagt oder ohne wertbares Boot wird schlicht
     * nicht gefahren. Diese Sicherheitsmarge lässt die Automatik seltener auslösen, aber niemals
     * dort, wo der Knopf selbst ablehnen würde. Das ist die sichere Richtung: Lieber einmal zu
     * wenig automatisch erzeugen als eine Runde erzeugen, die `createNewRound` verweigert hätte —
     * ein Fehler, der sonst nur im Log landet, während in der Oberfläche nichts zu sehen ist.
     *
     * Eine Runde ohne Läufe ist ausdrücklich nicht abgeschlossen, sondern noch gar nicht gesetzt.
     * Ohne diesen Fall erklärte die Automatik jede leere Runde für fertig und liefe die ganze
     * Kette in einem Rutsch durch.
     */
    fun roundIsComplete(round: CompetitionSetupRoundWithMatches): Boolean =
        round.matches.isNotEmpty() &&
            round.matches.all { matchIsSettled(it, round.required) }

    /**
     * Der Vermerk, den eine Ansicht zeigen darf: Er gilt nur, solange der Lauf noch unberührt ist.
     * Sobald er aufgerufen, gestartet oder beendet wurde, hat sich die Frage erledigt — wer am
     * Start stand, ist in dieser Aufstellung gefahren.
     *
     * Dass hier auch `startedAt` und `finishedAt` geprüft werden und nicht nur die Aktivierung, ist
     * kein Übereifer: Beenden nimmt die Aktivierung zurück (siehe
     * `LiveDashboardService.finishMatchInternal`), und ohne die beiden anderen Zeitstempel käme der
     * Vermerk am beendeten Lauf wieder hervor.
     */
    fun visibleRecalculationNotice(
        pairingsRecalculatedAt: LocalDateTime?,
        activatedAt: LocalDateTime?,
        startedAt: LocalDateTime?,
        finishedAt: LocalDateTime?,
    ): LocalDateTime? =
        pairingsRecalculatedAt?.takeIf { activatedAt == null && startedAt == null && finishedAt == null }
}
