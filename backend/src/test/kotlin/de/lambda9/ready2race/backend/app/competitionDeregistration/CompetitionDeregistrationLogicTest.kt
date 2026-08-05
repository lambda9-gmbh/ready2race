package de.lambda9.ready2race.backend.app.competitionDeregistration

import de.lambda9.ready2race.backend.app.competitionDeregistration.boundary.CompetitionDeregistrationLogic
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionDeregistrationLogicTest {

    // --- teamIsScored ---

    @Test
    fun freshTeamIsNotScored() {
        assertFalse(CompetitionDeregistrationLogic.teamIsScored(place = null, failed = false))
    }

    @Test
    fun teamWithPlaceIsScored() {
        assertTrue(CompetitionDeregistrationLogic.teamIsScored(place = 1, failed = false))
    }

    @Test
    fun failedTeamIsScored() {
        assertTrue(CompetitionDeregistrationLogic.teamIsScored(place = null, failed = true))
    }

    // --- scoringHasStarted ---

    /** Der Fall aus dem Bug: Runde frisch gesetzt, kein Ergebnis erfasst - Abmeldung muss gehen. */
    @Test
    fun freshMatchHasNoScoring() {
        val teams = listOf(
            team(place = null, failed = false),
            team(place = null, failed = false),
            team(place = null, failed = false),
            team(place = null, failed = false),
        )

        assertFalse(CompetitionDeregistrationLogic.scoringHasStarted(teams))
    }

    @Test
    fun oneTeamWithPlaceStartsTheScoring() {
        val teams = listOf(
            team(place = 1, failed = false),
            team(place = null, failed = false),
        )

        assertTrue(CompetitionDeregistrationLogic.scoringHasStarted(teams))
    }

    @Test
    fun oneFailedTeamStartsTheScoring() {
        val teams = listOf(
            team(place = null, failed = true),
            team(place = null, failed = false),
        )

        assertTrue(CompetitionDeregistrationLogic.scoringHasStarted(teams))
    }

    @Test
    fun fullyScoredMatchStartsTheScoring() {
        val teams = listOf(
            team(place = 1, failed = false),
            team(place = 2, failed = false),
            team(place = null, failed = true),
        )

        assertTrue(CompetitionDeregistrationLogic.scoringHasStarted(teams))
    }

    /** Kein Lauf (erste Runde noch nicht gesetzt) - der Service reicht dann eine leere Liste durch. */
    @Test
    fun noMatchHasNoScoring() {
        assertFalse(CompetitionDeregistrationLogic.scoringHasStarted(emptyList()))
    }

    private fun team(place: Int?, failed: Boolean): Boolean =
        CompetitionDeregistrationLogic.teamIsScored(place, failed)
}
