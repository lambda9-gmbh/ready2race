package de.lambda9.ready2race.backend.app.matchStatus.boundary

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.CrewLastScans
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeCause
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
import de.lambda9.ready2race.backend.app.matchStatus.entity.RoundCountersDto
import java.time.LocalDateTime

/**
 * Reine Funktionen, ohne Datenbank: aus den Rohwerten eines Laufs wird das
 * [MatchStatusDto], das alle Oberflächen lesen.
 *
 * Die Abhängigkeit matchStatus -> liveDashboard ist Absicht und der Preis dafür, die bereits
 * getestete [LiveDashboardLogic.deriveMatchState] nicht zu verschieben. Sie bleibt, wo sie ist;
 * dieses Modul ruft sie nur auf. Damit gibt es weiterhin genau eine Stelle, an der die
 * Zweig-Reihenfolge festgelegt ist - und genau einen Test, der sie festnagelt
 * (`LiveDashboardLogicTest`).
 */
object MatchStatusLogic {

    /**
     * Wie viele Mannschaften eines Laufs bereits gewertet sind. "Gewertet" heißt hier genau
     * dasselbe wie im Dashboard ([LiveDashboardLogic.teamHasResult]): abgemeldet ODER Platz
     * gesetzt ODER ausgeschieden. Abgemeldete zählen mit, weil für sie kein Ergebnis mehr kommt -
     * ohne diesen Fall erreichte ein Lauf mit einer Abmeldung nie "alle gewertet".
     */
    fun scoredCount(teams: List<MatchStatusTeam>): Int =
        teams.count { LiveDashboardLogic.teamHasResult(it.place, it.failed, it.deregistered) }

    /**
     * Setzt den Zustand eines Laufs zusammen. [teamsInArena] bleibt null, wo die Ansicht die
     * Check-in-Daten nicht erhebt (Zeitplan, öffentliche Anzeigen) - das ist etwas anderes als 0
     * ("erhoben, aber niemand draußen") und darf im Frontend nicht zum Arena-Chip führen.
     */
    fun matchStatus(
        activatedAt: LocalDateTime?,
        startTime: LocalDateTime?,
        startedAt: LocalDateTime?,
        finishedAt: LocalDateTime?,
        skipped: Boolean,
        teams: List<MatchStatusTeam>,
        teamsInArena: Int? = null,
        bye: MatchByeDto? = null,
    ): MatchStatusDto {
        val scored = scoredCount(teams)
        return MatchStatusDto(
            state = LiveDashboardLogic.deriveMatchState(
                activatedAt = activatedAt,
                startedAt = startedAt,
                startTime = startTime,
                finishedAt = finishedAt,
                // Dieselbe Eingabe wie im Dashboard: je Mannschaft ein Ja/Nein, ob sie gewertet
                // ist. Ein Lauf ohne Mannschaften bleibt damit ausdrücklich nicht
                // AWAITING_FINISH - deriveMatchState prüft auf isNotEmpty().
                teamResults = teams.map {
                    LiveDashboardLogic.teamHasResult(it.place, it.failed, it.deregistered)
                },
                skipped = skipped,
            ),
            startedAt = startedAt,
            teamsTotal = teams.size,
            teamsScored = scored,
            teamsInArena = teamsInArena,
            bye = bye,
        )
    }

    /**
     * Ob dieser Lauf ein Freilos ist - und wenn ja, warum.
     *
     * Die Regel - in einer nicht verpflichtenden Runde fährt genau ein Boot - ist dieselbe, nach
     * der die Durchführungsseite ein Freilos vorher schon anzeigte (`teams.length === 1` auf der
     * um `out`-Zeilen bereinigten Teamliste), und auf dem Folgerunden-Pfad auch dieselbe, nach der
     * `automaticFirstPlace` den automatischen ersten Platz vergibt: dort ist
     * `out = prevTeam.deregistered || prevTeam.out || prevTeam.failed` genau die Verneinung von
     * "fährt".
     *
     * Zwei Stellen weichen davon bewusst ab - keine Regression, die alte Frontend-Anzeigeregel
     * verhielt sich hier genauso:
     * - `CompetitionExecutionService.checkUpdateMatchResult` zählt `match.teams.size == 1` auf der
     *   UNGEFILTERTEN Teamliste. Ein fahrendes Boot plus eine aus einer Vorrunde mitgeführte
     *   `out`-Zeile - der Regelfall dieser Anzeige - ist für die Sperre keine 1, für [deriveBye]
     *   aber ein Freilos: die Ergebniseingabe bleibt offen, obwohl die Oberfläche "Freilos" zeigt.
     * - `automaticFirstPlace` zählt auf dem Erstrunden-Pfad die ausgelosten Plätze unabhängig von
     *   der Abmeldung. Ein Freilos der ersten Runde, das erst durch eine Abmeldung entsteht,
     *   bekommt dort keinen automatischen ersten Platz und bleibt UPCOMING ("Freilos · offen").
     *
     * Über die Ursache entscheidet allein der Abmelde-Datensatz. Eine Zeile, die nur `out` ist,
     * kann ausgeschieden oder nicht weitergekommen sein - daraus eine Abmeldung zu machen, wäre
     * geraten. Der Freitext-Grund überlebt nur bei genau einer abgemeldeten Zeile: bei zweien wäre
     * die Zuordnung Name -> Grund ebenfalls geraten.
     */
    fun deriveBye(
        roundRequired: Boolean,
        teams: List<MatchByeTeam>,
        /**
         * `competition_match.bye_must_race`: Der Lauf bleibt ein Freilos (dieses DTO bleibt
         * gesetzt, das Panel zeigt ihn weiter), wird aber operativ als echtes Rennen behandelt.
         * Nur durchgereicht, nicht Teil der Ableitung - ob ein Lauf ein Freilos IST, entscheidet
         * weiterhin allein die Besetzung.
         */
        mustRace: Boolean = false,
    ): MatchByeDto? {
        if (roundRequired) return null
        if (teams.count { it.racing } != 1) return null

        val withdrawn = teams.filter { !it.racing && it.deregistered }
        if (withdrawn.isEmpty()) {
            return MatchByeDto(MatchByeCause.NO_OPPONENT, teamName = null, reason = null, mustRace = mustRace)
        }
        return MatchByeDto(
            cause = MatchByeCause.DEREGISTRATION,
            teamName = withdrawn.joinToString(", ") { it.name },
            reason = withdrawn.singleOrNull()?.deregistrationReason,
            mustRace = mustRace,
        )
    }

    /**
     * Wie viele Mannschaften je Lauf einer Runde in der Arena sind - null, wenn die Runde dazu
     * nichts hergibt.
     *
     * [roundCrewScans] enthält je Lauf der Runde je Mannschaft die letzten Scans ihrer Crew, in
     * der Reihenfolge der Läufe. Ob eine einzelne Mannschaft draußen ist, entscheidet unverändert
     * [LiveDashboardLogic.teamInArenaAt] (mindestens eine Person zuletzt ENTRY) - dieselbe Regel
     * wie im Schiedsrichter-Dashboard, an genau einem Ort.
     *
     * Der Null-Fall ist der wichtigere Teil (Abschnitt 6 der Spec): hatte in der ganzen Runde
     * KEINE Person je einen Scan, läuft die Veranstaltung ohne Check-in. Dann ist 0 keine Aussage,
     * sondern eine Lücke - und bei jedem Lauf stünde dauerhaft "Arena 0/6". Die Runde als Ganzes
     * entscheidet, nicht der einzelne Lauf: ein Lauf, dessen Crews noch nicht am Steg waren,
     * gehört zur erhobenen Runde und soll seine ehrliche 0 zeigen.
     */
    fun teamsInArenaPerMatch(roundCrewScans: List<List<CrewLastScans>>): List<Int?> {
        val anyScan = roundCrewScans.any { match -> match.any { crew -> crew.any { it != null } } }
        return roundCrewScans.map { match ->
            if (anyScan) match.count { LiveDashboardLogic.teamInArenaAt(it) != null } else null
        }
    }

    /**
     * Die Zähler einer Runde. Jeder Lauf zählt in genau einen Topf; die Reihenfolge der Zweige
     * folgt der von [LiveDashboardLogic.deriveMatchState], damit die Leiste nichts anderes
     * behauptet als die Chips darunter.
     */
    fun roundCounters(statuses: List<MatchStatusDto>): RoundCountersDto = RoundCountersDto(
        total = statuses.size,
        preparing = statuses.count { it.state == MatchState.PREPARING },
        running = statuses.count { it.state == MatchState.RUNNING },
        open = statuses.count {
            it.state == MatchState.AWAITING_FINISH ||
                it.state == MatchState.UPCOMING ||
                it.state == MatchState.UNSCHEDULED
        },
        finished = statuses.count { it.state == MatchState.FINISHED },
        skipped = statuses.count { it.state == MatchState.SKIPPED },
    )
}
