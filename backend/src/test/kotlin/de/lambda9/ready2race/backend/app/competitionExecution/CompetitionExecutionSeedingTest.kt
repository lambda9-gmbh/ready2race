package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Deckt die Seeding-Liste ab, aus der `computeCompetitionPlaces` die Plätze einer Runde ableitet.
 *
 * Hintergrund: Die Liste beantwortet beim Rundenaufbau die Frage "wer kommt weiter" und reichte
 * deshalb nur bis zur Zahl der Plätze in der Folgerunde. Bei der Platzvergabe braucht aber jedes
 * Boot der Runde einen Eintrag, sonst lief `seedingList[matchIndex][realPlace - 1]` in eine
 * IndexOutOfBoundsException. Zwei Konstellationen waren betroffen, beide mit Massenfeld
 * (`teams IS NULL`), denn nur dort begrenzt das Maximum die Liste überhaupt:
 *
 * - die letzte Runde (keine Folgerunde, Maximum `0`) — für Coastal Rowing der Normalfall,
 * - eine Qualifikation, aus der nur ein Teil des Feldes weiterkommt.
 */
class CompetitionExecutionSeedingTest {

    private val ascending = CompetitionSetupPlacesOption.ASCENDING.name
    private val custom = CompetitionSetupPlacesOption.CUSTOM.name
    private val equal = CompetitionSetupPlacesOption.EQUAL.name

    /** Die Rundenergebnisse, die ein Lauf der Reihe nach vergibt — so liest sie die Platzvergabe. */
    private fun outcomesOfMatch(seedingList: List<List<Int>>?, matchIndex: Int, teamsInMatch: Int) =
        (1..teamsInMatch).map { realPlace ->
            CompetitionExecutionService.getRoundOutcome(seedingList!!, matchIndex, realPlace)
        }

    // --- Fall 1: letzte Runde als Massenfeld ---

    @Test
    fun massenfeldFinaleMitAscendingVergibtDiePlaetzeEinsBisN() {
        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = listOf(null),
            seedsForNextRound = 0,
            teamsInThisRound = 5,
        )

        assertEquals(listOf(listOf(1, 2, 3, 4, 5)), seedingList)
        assertEquals(listOf(1, 2, 3, 4, 5), outcomesOfMatch(seedingList, 0, 5))
    }

    @Test
    fun massenfeldFinaleFolgtDerTatsaechlichenTeilnehmerzahl() {
        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = listOf(null),
            seedsForNextRound = 0,
            teamsInThisRound = 12,
        )

        assertEquals(listOf((1..12).toList()), seedingList)
    }

    @Test
    fun massenfeldFinaleMitCustomLaeuftNichtMehrInsLeere() {
        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = custom,
            currentRoundTeams = listOf(null),
            seedsForNextRound = 0,
            teamsInThisRound = 5,
        )

        assertEquals(listOf(listOf(1, 2, 3, 4, 5)), seedingList)
    }

    @Test
    fun leeresMassenfeldFinaleBleibtLeerStattZuKnallen() {
        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = listOf(null),
            seedsForNextRound = 0,
            teamsInThisRound = 0,
        )

        assertEquals(listOf(emptyList()), seedingList)
    }

    // --- Fall 2: Qualifikation mit mehr Booten als Aufsteigern ---

    @Test
    fun zeitfahrenAlsMassenfeldReichtBisZumLetztenBoot() {
        // Beachsprint-Template: Zeitfahren (Massenfeld, 6 Boote) -> Halbfinale mit 2 Läufen à 2 Booten
        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = listOf(null),
            seedsForNextRound = 4,
            teamsInThisRound = 6,
        )

        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), seedingList)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), outcomesOfMatch(seedingList, 0, 6))
    }

    @Test
    fun dieAufsteigerBehaltenIhrSeedingTrotzLaengererListe() {
        val teams = listOf<Int?>(null)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 4,
            teamsInThisRound = 6,
        )!!

        // Der Rundenaufbau nutzt weiterhin die kurze Liste — sie muss ein Präfix der langen sein,
        // sonst würden sich die Rundenergebnisse der Weiterkommenden verschieben.
        val forRoundCreation = CompetitionExecutionService.getSeedingList(teams, 4)
        forRoundCreation.forEachIndexed { matchIdx, seedings ->
            assertEquals(seedings, seedingList[matchIdx].take(seedings.size))
        }
    }

    @Test
    fun mehrereVorlaeufeMitFesterBootszahlLassenDieNichtAufsteigerNichtFallen() {
        // Zwei Vorläufe à 3 Boote, aus denen jeweils 2 weiterkommen
        val teams = listOf<Int?>(3, 3)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 4,
            teamsInThisRound = 6,
        )

        assertEquals(listOf(listOf(1, 4, 5), listOf(2, 3, 6)), seedingList)
        // Feste Bootszahlen waren nie vom Maximum begrenzt — hier ändert sich nichts
        assertEquals(CompetitionExecutionService.getSeedingList(teams, 4), seedingList)
    }

    // --- Über die Seeding-Reichweite hinaus ---

    @Test
    fun bootJenseitsDerListeKommtHinterAlleVerteiltenErgebnisse() {
        // Zwei Massenfelder in einer Runde: die Rechnung verteilt 3 und 3, tatsächlich fahren
        // im ersten Lauf 5 Boote
        val seedingList = listOf(listOf(1, 4, 5), listOf(2, 3, 6))

        assertEquals(5, CompetitionExecutionService.getRoundOutcome(seedingList, 0, 3))
        assertEquals(7, CompetitionExecutionService.getRoundOutcome(seedingList, 0, 4))
        assertEquals(8, CompetitionExecutionService.getRoundOutcome(seedingList, 0, 5))
    }

    @Test
    fun laufOhneEintragInDerSeedingListeKnalltNicht() {
        val seedingList = listOf(listOf(1, 2, 3))

        assertEquals(4, CompetitionExecutionService.getRoundOutcome(seedingList, 1, 1))
        assertEquals(5, CompetitionExecutionService.getRoundOutcome(seedingList, 1, 2))
    }

    // --- Letzte Runde mit fester Bootszahl bleibt unverändert ---

    @Test
    fun finaleMitFesterBootszahlBleibtUnveraendert() {
        val teams = listOf<Int?>(5)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 0,
            teamsInThisRound = 5,
        )

        assertEquals(listOf(listOf(1, 2, 3, 4, 5)), seedingList)
        // Bisheriges Verhalten: für die letzte Runde ging maxTeamsNeeded = 0 herein
        assertEquals(CompetitionExecutionService.getSeedingList(teams, 0), seedingList)
    }

    @Test
    fun finaleAUndBBehaltenIhreSchlangenVerteilung() {
        val teams = listOf<Int?>(2, 2)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = custom,
            currentRoundTeams = teams,
            seedsForNextRound = 0,
            teamsInThisRound = 4,
        )

        assertEquals(listOf(listOf(1, 4), listOf(2, 3)), seedingList)
        assertEquals(CompetitionExecutionService.getSeedingList(teams, 0), seedingList)
    }

    @Test
    fun fuenfVorlaeufeAlsLetzteRundeBleibenUnveraendert() {
        val teams = listOf<Int?>(6, 6, 6, 6, 6)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 0,
            teamsInThisRound = 30,
        )

        assertEquals(listOf(1, 10, 11, 20, 21, 30), seedingList!![0])
        assertEquals(listOf(5, 6, 15, 16, 25, 26), seedingList[4])
        assertEquals(CompetitionExecutionService.getSeedingList(teams, 0), seedingList)
    }

    // --- EQUAL braucht keine Seeding-Liste ---

    @Test
    fun equalBekommtKeineSeedingListe() {
        assertNull(
            CompetitionExecutionService.getPlacesSeedingList(
                placesOption = equal,
                currentRoundTeams = listOf(null),
                seedsForNextRound = 0,
                teamsInThisRound = 6,
            )
        )
        assertNull(
            CompetitionExecutionService.getPlacesSeedingList(
                placesOption = equal,
                currentRoundTeams = listOf(2, 2),
                seedsForNextRound = 2,
                teamsInThisRound = 4,
            )
        )
    }

    // --- Zwischenrunden mit voller Weiterleitung bleiben wie bisher ---

    @Test
    fun halbfinaleMitFesterBootszahlSeedetWieBisher() {
        val teams = listOf<Int?>(2, 2)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 4,
            teamsInThisRound = 4,
        )

        assertEquals(listOf(listOf(1, 4), listOf(2, 3)), seedingList)
        assertEquals(CompetitionExecutionService.getSeedingList(teams, 4), seedingList)
    }

    @Test
    fun kleinesFeldWirdNichtUnterDieAufsteigerplaetzeGekuerzt() {
        // Massenfeld mit nur 3 Booten, aber 4 Plätzen in der Folgerunde
        val teams = listOf<Int?>(null)

        val seedingList = CompetitionExecutionService.getPlacesSeedingList(
            placesOption = ascending,
            currentRoundTeams = teams,
            seedsForNextRound = 4,
            teamsInThisRound = 3,
        )

        assertEquals(CompetitionExecutionService.getSeedingList(teams, 4), seedingList)
    }
}
