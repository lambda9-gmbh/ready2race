package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionLogic
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die eine Entscheidung, an der die Automatik hängt: Ist diese Runde durch?
 *
 * Ohne Datenbank geprüft, weil die Frage ausschließlich an Feldern hängt, die die Ansicht ohnehin
 * schon liefert — und weil nur so jedes Wettkampfformat als eigener Fall dastehen kann, ohne für
 * jedes eine Regatta aufbauen zu müssen.
 */
class AutoRoundProgressionLogicTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    /**
     * Eine Mannschaft in einem Lauf. Die Vorgabewerte beschreiben den Normalfall: gemeldet,
     * angetreten, gewertet.
     */
    private fun team(
        place: Int? = null,
        failed: Boolean = false,
        out: Boolean = false,
        deregistered: Boolean = false,
        startNumber: Int = 1,
    ) = CompetitionMatchTeamWithRegistration(
        id = UUID.randomUUID(),
        competitionMatch = UUID.randomUUID(),
        startNumber = startNumber,
        place = place,
        timeString = null,
        placesCalculated = false,
        competitionRegistration = UUID.randomUUID(),
        clubId = UUID.randomUUID(),
        clubName = "Testverein",
        registrationName = null,
        teamNumber = 1,
        participants = emptyList(),
        deregistered = deregistered,
        deregistrationReason = null,
        out = out,
        failed = failed,
        failedReason = null,
        penaltySeconds = null,
        penaltyNote = null,
        ratingCategory = null,
        mixedTeamTerm = null,
    )

    private fun match(
        finishedAt: LocalDateTime? = now,
        skipped: Boolean = false,
        teams: List<CompetitionMatchTeamWithRegistration>,
    ) = CompetitionMatchWithTeams(
        competitionSetupMatch = UUID.randomUUID(),
        startTime = now,
        activatedAt = null,
        startedAt = null,
        finishedAt = finishedAt,
        skipped = skipped,
        raceClockerPolledAt = null,
        raceClockerPollError = null,
        raceClockerAutoPausedAt = null,
        pairingsRecalculatedAt = null,
        teams = teams,
    )

    private fun round(
        required: Boolean = true,
        isQualification: Boolean = false,
        matches: List<CompetitionMatchWithTeams>,
    ) = CompetitionSetupRoundWithMatches(
        setupRoundId = UUID.randomUUID(),
        competitionSetup = UUID.randomUUID(),
        nextRound = UUID.randomUUID(),
        setupRoundName = "Runde",
        required = required,
        isQualification = isQualification,
        placesOption = CompetitionSetupPlacesOption.EQUAL.name,
        materializedAt = null,
        places = emptyList(),
        setupMatches = emptyList(),
        matches = matches,
        substitutions = emptyList(),
    )

    // --- Vererbung ---

    @Test
    fun theCompetitionInheritsWhenItSaysNothing() {
        assertTrue(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = true, competitionOverride = null))
        assertFalse(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = false, competitionOverride = null))
    }

    @Test
    fun anExplicitCompetitionSettingBeatsTheEvent() {
        assertTrue(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = false, competitionOverride = true))
        assertFalse(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = true, competitionOverride = false))
    }

    // --- Abschluss ---

    @Test
    fun aRoundWithoutMatchesIsNotComplete() {
        assertFalse(AutoRoundProgressionLogic.roundIsComplete(round(matches = emptyList())))
    }

    /**
     * Der Alltagsfall am Renntag: Ein Lauf mit DNF, einer Disqualifikation, einem Nichtantritt und
     * einer Abmeldung ist vollständig gewertet. Für all diese Boote kommt kein Ergebnis mehr, und
     * die Platzfolge zählt sie nicht mit — sonst erreichte kein Lauf mit Ausfall je "durch".
     */
    @Test
    fun specialStatusesCountAsScored() {
        val complete = round(
            matches = listOf(
                match(
                    teams = listOf(
                        team(place = 1, startNumber = 1),
                        team(place = 2, startNumber = 2),
                        team(failed = true, startNumber = 3),
                        team(deregistered = true, startNumber = 4),
                        team(out = true, startNumber = 5),
                    )
                )
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(complete))
    }

    /** Ein Freilos wird nie gefahren und nie beendet — es darf die Runde trotzdem nicht aufhalten. */
    @Test
    fun aByeDoesNotHoldTheRoundBack() {
        val withBye = round(
            required = false,
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(withBye))
    }

    /** Ein abgesagter Lauf ist erledigt, auch ohne Ergebnis. */
    @Test
    fun aSkippedMatchIsDone() {
        val withSkipped = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, skipped = true, teams = listOf(team(startNumber = 1), team(startNumber = 2))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(withSkipped))
    }

    /**
     * Vollständige Ergebnisse ohne Beenden-Klick reichen nicht. Bis zum Klick kann noch eine
     * Zeitstrafe kommen — das ist die Entscheidung C1 aus dem Bestand, und die Automatik darf sie
     * nicht unterlaufen.
     */
    @Test
    fun scoredButNotFinishedIsNotComplete() {
        val awaitingFinish = round(
            matches = listOf(
                match(
                    finishedAt = null,
                    teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2)),
                )
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(awaitingFinish))
    }

    @Test
    fun aMissingPlaceKeepsTheRoundOpen() {
        val missingPlace = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = null, startNumber = 2)))
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(missingPlace))
    }

    /**
     * Die Platzfolge muss lückenlos sein. Zwei Boote mit den Plätzen 1 und 3 sind kein
     * abgeschlossener Lauf, sondern ein Eingabefehler.
     */
    @Test
    fun placesMustBeContinuous() {
        val gap = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 3, startNumber = 2)))
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(gap))
    }

    // --- Formate ---

    /** K.-o.-Runde: vier Läufe zu zwei Booten, jeder beendet und gewertet. */
    @Test
    fun aKnockoutRoundIsComplete() {
        val quarterFinals = round(
            matches = (1..4).map {
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2)))
            }
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(quarterFinals))
    }

    /** Vorrunde: zwei Läufe zu sechs Booten. */
    @Test
    fun aHeatRoundIsComplete() {
        val heats = round(
            matches = (1..2).map {
                match(teams = (1..6).map { rank -> team(place = rank, startNumber = rank) })
            }
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(heats))
    }

    /** Zwischenrunde: derselbe Aufbau, ein Lauf noch offen — die Runde ist nicht durch. */
    @Test
    fun anUnfinishedSemiFinalHoldsTheRound() {
        val semiFinals = round(
            matches = listOf(
                match(teams = (1..6).map { rank -> team(place = rank, startNumber = rank) }),
                match(finishedAt = null, teams = (1..6).map { rank -> team(startNumber = rank) }),
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(semiFinals))
    }

    /** Qualifikation mit einem Freilos und einem gefahrenen Lauf. */
    @Test
    fun aQualificationWithAByeIsComplete() {
        val qualification = round(
            required = false,
            isQualification = true,
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(qualification))
    }

    /**
     * Ein einzelner Lauf mit nur einem Boot in einer ERFORDERLICHEN Runde ist kein Freilos: Dort
     * wird gefahren, und ohne Beenden ist die Runde nicht durch.
     */
    @Test
    fun aSingleTeamInARequiredRoundIsNoBye() {
        val timeTrial = round(
            required = true,
            matches = listOf(match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))))
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(timeTrial))
    }
}
