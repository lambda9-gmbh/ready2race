package de.lambda9.ready2race.backend.app.matchStatus

import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeCause
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchStatusLogicTest {

    private val start = LocalDateTime.of(2026, 8, 7, 10, 0)

    private fun open() = MatchStatusTeam(place = null, failed = false, deregistered = false)
    private fun placed(place: Int) = MatchStatusTeam(place = place, failed = false, deregistered = false)
    private fun failed() = MatchStatusTeam(place = null, failed = true, deregistered = false)
    private fun deregistered() = MatchStatusTeam(place = null, failed = false, deregistered = true)

    // --- scoredCount ---

    @Test
    fun noTeamsScoresNothing() {
        assertEquals(0, MatchStatusLogic.scoredCount(emptyList()))
    }

    @Test
    fun onlyTeamsWithAPlaceCount() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), placed(2), open(), open())))
    }

    /** Für eine abgemeldete Mannschaft kommt kein Ergebnis mehr - sie gilt als erledigt. */
    @Test
    fun deregisteredCountsAsScored() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), deregistered())))
    }

    /** Ausgeschieden ist ebenfalls ein Ergebnis, nur eben ohne Platz. */
    @Test
    fun failedCountsAsScored() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), failed())))
    }

    @Test
    fun scoredCountMatchesLiveDashboardRule() {
        val teams = listOf(placed(1), failed(), deregistered(), open())
        assertEquals(3, MatchStatusLogic.scoredCount(teams))
    }

    // --- racedCount / deregisteredCount: der Vorfall vom 14.08.2026 ---

    /**
     * Der Vorfall selbst: Ein Fünferlauf, aus dem eine Mannschaft abgemeldet wurde und in dem
     * sonst niemand gefahren ist. "Erledigt" ist 1 - dagegen ist nichts zu sagen, für dieses Boot
     * kommt kein Ergebnis mehr. "Gefahren" muss 0 sein; nur daran hängt "Teilweise gewertet",
     * und genau deshalb stand auf dem Schiedsrichter-Board fälschlich "Teilweise gewertet 1/5".
     */
    @Test
    fun oneDeregisteredOfFiveHasNotRaced() {
        val teams = listOf(deregistered(), open(), open(), open(), open())
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = teams,
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(5, status.teamsTotal)
        assertEquals(1, status.teamsScored)
        assertEquals(0, status.teamsRaced)
        assertEquals(1, status.teamsDeregistered)
    }

    /** Eine Abmeldung neben drei gefahrenen Booten: das IST eine Teilwertung, 3 von 4 erwarteten. */
    @Test
    fun oneDeregisteredAndThreePlacedOfFiveIsPartiallyRaced() {
        val teams = listOf(deregistered(), placed(1), placed(2), placed(3), open())
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = teams,
        )
        assertEquals(4, status.teamsScored)
        assertEquals(3, status.teamsRaced)
        assertEquals(1, status.teamsDeregistered)
        // 3 gefahren von (5 - 1) erwarteten: die Ablesung "Teilweise gewertet" greift.
        assertEquals(3, status.teamsRaced)
        assertEquals(4, status.teamsTotal - status.teamsDeregistered)
    }

    /**
     * Alle abgemeldet: Der Lauf gilt weiterhin als erledigt und wartet nur noch auf seinen
     * Beenden-Klick - sonst bliebe die Aktivierungskette an ihm hängen. Gefahren ist niemand.
     */
    @Test
    fun allDeregisteredStillAwaitsFinish() {
        val teams = listOf(deregistered(), deregistered(), deregistered())
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = teams,
        )
        assertEquals(MatchState.AWAITING_FINISH, status.state)
        assertEquals(3, status.teamsScored)
        assertEquals(0, status.teamsRaced)
        assertEquals(3, status.teamsDeregistered)
    }

    @Test
    fun racedCountIgnoresDeregistrations() {
        assertEquals(0, MatchStatusLogic.racedCount(listOf(deregistered(), open())))
        assertEquals(2, MatchStatusLogic.racedCount(listOf(placed(1), failed(), deregistered(), open())))
        assertEquals(1, MatchStatusLogic.deregisteredCount(listOf(placed(1), deregistered(), open())))
    }

    // --- matchStatus ---

    /**
     * Ein Block je Zustand: das ist die Liste, gegen die jede Oberfläche geprüft wird. Fällt hier
     * ein Zweig um, zeigen Durchführung, Zeitplan, Dashboard, Athleten-Anzeige UND die öffentliche
     * Ergebnisanzeige gemeinsam etwas Falsches - genau deshalb steht die Ableitung an einem Ort.
     */
    @Test
    fun upcomingIsTheDefaultForAScheduledMatch() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(2, status.teamsTotal)
        assertEquals(0, status.teamsScored)
    }

    /** Aktiviert, aber ohne Ist-Start: der Lauf ist an den Start gerufen und liegt noch am Steg. */
    @Test
    fun activatedWithoutARealStartIsPreparing() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start.minusMinutes(3),
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.PREPARING, status.state)
        assertNull(status.startedAt)
    }

    @Test
    fun activatedAndStartedIsRunning() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start.minusMinutes(3),
            startTime = start,
            startedAt = start.plusMinutes(1),
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.RUNNING, status.state)
        assertEquals(start.plusMinutes(1), status.startedAt)
    }

    @Test
    fun runningBeatsEverythingElse() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = start.minusMinutes(5),
            startTime = start,
            startedAt = start.plusMinutes(2),
            finishedAt = null,
            skipped = true,
            teams = listOf(placed(1), placed(2)),
        )
        // Was tatsächlich passiert, schlägt den zurückgenommenen Plan - siehe deriveMatchState.
        assertEquals(MatchState.RUNNING, status.state)
        assertEquals(start.plusMinutes(2), status.startedAt)
    }

    @Test
    fun finishedOnlyMeansFinishedAtIsSet() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = start,
            finishedAt = start.plusMinutes(6),
            skipped = false,
            teams = listOf(placed(1), open()),
        )
        assertEquals(MatchState.FINISHED, status.state)
    }

    @Test
    fun fullyScoredButNotFinishedAwaitsFinish() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = start,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1), deregistered()),
        )
        assertEquals(MatchState.AWAITING_FINISH, status.state)
        assertEquals(2, status.teamsTotal)
        assertEquals(2, status.teamsScored)
    }

    @Test
    fun partiallyScoredStaysUpcoming() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1), open(), open()),
        )
        // "Teilweise gewertet" ist kein Zustand, sondern die Ablesung 0 < scored < total.
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(3, status.teamsTotal)
        assertEquals(1, status.teamsScored)
    }

    @Test
    fun skippedWithoutRunOrFinish() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = true,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.SKIPPED, status.state)
    }

    @Test
    fun withoutStartTimeUnscheduled() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = null,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
        )
        assertEquals(MatchState.UNSCHEDULED, status.state)
    }

    /** Ohne Mannschaften gibt es nichts zu werten - kein AWAITING_FINISH aus dem Nichts. */
    @Test
    fun matchWithoutTeamsIsNotAwaitingFinish() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = emptyList(),
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(0, status.teamsTotal)
        assertEquals(0, status.teamsScored)
    }

    /** null heißt "nicht erhoben" und ist etwas anderes als 0 ("erhoben, niemand draußen"). */
    @Test
    fun teamsInArenaDefaultsToNotCollected() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
        )
        assertNull(status.teamsInArena)

        val withArena = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
            teamsInArena = 0,
        )
        assertEquals(0, withArena.teamsInArena)
    }

    // --- teamsInArenaPerMatch ---

    private fun exit(minute: Int) = ParticipantScanType.EXIT.name to start.plusMinutes(minute.toLong())
    private fun entry(minute: Int) = ParticipantScanType.ENTRY.name to start.plusMinutes(minute.toLong())

    /** Ohne einen einzigen Scan der Runde gibt es keine Grundlage - null, nicht 0. */
    @Test
    fun roundWithoutAnyScanReportsNothing() {
        val round = listOf(
            listOf(listOf(null, null), listOf(null, null)),
            listOf(listOf(null, null)),
        )
        assertEquals(listOf(null, null), MatchStatusLogic.teamsInArenaPerMatch(round))
    }

    /** Ein Lauf ohne Mannschaften bleibt bei 0, sobald die Runde überhaupt erhoben ist. */
    @Test
    fun matchWithoutTeamsCountsZeroInATrackedRound() {
        val round = listOf(
            listOf(listOf(entry(1), entry(2))),
            emptyList(),
        )
        assertEquals(listOf(1, 0), MatchStatusLogic.teamsInArenaPerMatch(round))
    }

    /** Genau die Regel des Dashboards: mindestens eine Person zuletzt ENTRY genügt. */
    @Test
    fun aSingleCheckedInPersonCountsTheCrew() {
        val round = listOf(
            listOf(
                // vollständig draußen
                listOf(entry(1), entry(3)),
                // eine Person wieder ausgecheckt - die andere trägt das Boot
                listOf(entry(1), exit(4)),
                // eine Person nie gescannt - die andere trägt das Boot
                listOf(entry(1), null),
                // niemand (mehr) eingecheckt
                listOf(exit(2), null),
                // keine Crew bekannt - lässt sich nichts belegen
                emptyList(),
            )
        )
        assertEquals(listOf(3), MatchStatusLogic.teamsInArenaPerMatch(round))
    }

    /** Ein einziger Scan irgendwo in der Runde macht die ganze Runde erhoben. */
    @Test
    fun oneScanAnywhereMakesTheWholeRoundCounted() {
        val round = listOf(
            listOf(listOf(exit(1), null)),
            listOf(listOf(null, null)),
        )
        assertEquals(listOf(0, 0), MatchStatusLogic.teamsInArenaPerMatch(round))
    }

    @Test
    fun emptyRoundStaysEmpty() {
        assertEquals(emptyList(), MatchStatusLogic.teamsInArenaPerMatch(emptyList()))
    }

    // --- roundCounters ---

    private fun status(state: MatchState) = MatchStatusDto(
        state = state,
        startedAt = null,
        teamsTotal = 2,
        teamsScored = 0,
    )

    @Test
    fun countersAreEmptyForAnEmptyRound() {
        val counters = MatchStatusLogic.roundCounters(emptyList())
        assertEquals(0, counters.total)
        assertEquals(0, counters.running)
        assertEquals(0, counters.open)
        assertEquals(0, counters.finished)
        assertEquals(0, counters.skipped)
    }

    @Test
    fun countersSortEveryStateIntoExactlyOneBucket() {
        val counters = MatchStatusLogic.roundCounters(
            listOf(
                status(MatchState.RUNNING),
                status(MatchState.FINISHED),
                status(MatchState.FINISHED),
                status(MatchState.FINISHED),
                status(MatchState.SKIPPED),
                status(MatchState.UPCOMING),
            )
        )
        assertEquals(6, counters.total)
        assertEquals(1, counters.running)
        assertEquals(1, counters.open)
        assertEquals(3, counters.finished)
        assertEquals(1, counters.skipped)
        assertEquals(
            counters.total,
            counters.preparing + counters.running + counters.open + counters.finished + counters.skipped
        )
    }

    @Test
    fun `ein Lauf in Vorbereitung zaehlt weder als laufend noch als offen`() {
        val counters = MatchStatusLogic.roundCounters(
            listOf(
                MatchStatusDto(MatchState.PREPARING, startedAt = null, teamsTotal = 6, teamsScored = 0),
                MatchStatusDto(MatchState.RUNNING, startedAt = null, teamsTotal = 6, teamsScored = 0),
            )
        )
        assertEquals(1, counters.preparing)
        assertEquals(1, counters.running)
        assertEquals(0, counters.open)
        assertEquals(2, counters.total)
    }

    /** Ein Lauf, auf dessen Beenden alles wartet, ist offen - nicht beendet. */
    @Test
    fun awaitingFinishCountsAsOpen() {
        val counters = MatchStatusLogic.roundCounters(
            listOf(status(MatchState.AWAITING_FINISH), status(MatchState.UNSCHEDULED))
        )
        assertEquals(2, counters.open)
        assertEquals(0, counters.finished)
    }

    // --- deriveBye ---

    private fun racing(name: String = "RC Bergedorf", seed: Int? = null) =
        MatchByeTeam(racing = true, name = name, deregistered = false, deregistrationReason = null, seed = seed)

    /** Aus der Vorrunde mitgeführt, aber nicht abgemeldet: ausgeschieden oder nicht weitergekommen. */
    private fun eliminated(name: String = "RV Hansa") =
        MatchByeTeam(racing = false, name = name, deregistered = false, deregistrationReason = null)

    private fun withdrawn(name: String = "RV Hansa", reason: String? = null) =
        MatchByeTeam(racing = false, name = name, deregistered = true, deregistrationReason = reason)

    @Test
    fun requiredRoundIsNeverABye() {
        assertNull(MatchStatusLogic.deriveBye(roundRequired = true, teams = listOf(racing())))
    }

    @Test
    fun twoRacingTeamsAreNoBye() {
        assertNull(MatchStatusLogic.deriveBye(false, listOf(racing("A"), racing("B"))))
    }

    @Test
    fun aMatchWithoutTeamsIsNoBye() {
        assertNull(MatchStatusLogic.deriveBye(false, emptyList()))
    }

    @Test
    fun aSingleSeededTeamIsAStructuralBye() {
        assertEquals(
            MatchByeDto(MatchByeCause.NO_OPPONENT, null, null),
            MatchStatusLogic.deriveBye(false, listOf(racing())),
        )
    }

    /** Ausgeschieden ist keine Abmeldung - ohne Datensatz wird auch keine behauptet. */
    @Test
    fun anEliminatedOpponentStaysNeutral() {
        assertEquals(
            MatchByeDto(MatchByeCause.NO_OPPONENT, null, null),
            MatchStatusLogic.deriveBye(false, listOf(racing(), eliminated())),
        )
    }

    @Test
    fun aWithdrawnOpponentNamesTeamAndReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", "Krankheit"),
            MatchStatusLogic.deriveBye(false, listOf(racing(), withdrawn("RV Hansa", "Krankheit"))),
        )
    }

    @Test
    fun aWithdrawnOpponentWithoutAStoredReasonStillNamesTheTeam() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", null),
            MatchStatusLogic.deriveBye(false, listOf(racing(), withdrawn("RV Hansa"))),
        )
    }

    // --- Setzungszahl im Freilos-Label ("Freilos 1") ---

    /**
     * Die Setzungszahl der fahrenden Mannschaft wandert ins DTO - "Freilos 1" ist das Freilos
     * des Bootes, das als Erstes weiterkam. Die Zahl der nicht fahrenden Zeilen zählt nicht.
     */
    @Test
    fun theRacingTeamsSeedTravelsIntoTheBye() {
        val withSeed = MatchStatusLogic.deriveBye(
            false,
            listOf(
                racing(seed = 1),
                MatchByeTeam(
                    racing = false,
                    name = "RV Hansa",
                    deregistered = false,
                    deregistrationReason = null,
                    seed = 8,
                ),
            ),
        )
        assertEquals(1, withSeed?.seed)

        // Auch bei einer Abmeldung trägt das Label die Zahl des fahrenden Bootes.
        val withdrawnOpponent = MatchStatusLogic.deriveBye(
            false,
            listOf(racing(seed = 3), withdrawn("RV Hansa", "Krankheit")),
        )
        assertEquals(3, withdrawnOpponent?.seed)
    }

    /**
     * Ohne passenden Setup-Platz (Erstrunden-Freilos durch Abmeldung, umgetragene Startnummer)
     * bleibt die Zahl null - das Label sagt dann schlicht "Freilos" statt zu raten.
     */
    @Test
    fun withoutASeatTheSeedStaysNull() {
        assertNull(MatchStatusLogic.deriveBye(false, listOf(racing()))?.seed)
    }

    /** Bei mehreren Abmeldungen wäre die Zuordnung Name -> Grund geraten. */
    @Test
    fun severalWithdrawalsDropTheReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa, RC Favorite", null),
            MatchStatusLogic.deriveBye(
                false,
                listOf(
                    racing(),
                    withdrawn("RV Hansa", "Krankheit"),
                    withdrawn("RC Favorite", "Materialschaden"),
                ),
            ),
        )
    }

    /** Eine Abmeldung neben einer Ausscheidung reicht für die Ursache - und für den Grund. */
    @Test
    fun aWithdrawalNextToAnEliminationStillCarriesTheReason() {
        assertEquals(
            MatchByeDto(MatchByeCause.DEREGISTRATION, "RV Hansa", "Krankheit"),
            MatchStatusLogic.deriveBye(
                false,
                listOf(racing(), eliminated("RG Wandsbek"), withdrawn("RV Hansa", "Krankheit")),
            ),
        )
    }

    @Test
    fun matchStatusCarriesTheBye() {
        val bye = MatchByeDto(MatchByeCause.NO_OPPONENT, null, null)
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1)),
            bye = bye,
        )
        assertEquals(bye, status.bye)
    }

    @Test
    fun matchStatusWithoutAByeSaysSo() {
        val status = MatchStatusLogic.matchStatus(
            activatedAt = null,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open(), open()),
        )
        assertNull(status.bye)
    }
}
