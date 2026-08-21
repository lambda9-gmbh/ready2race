package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionExecution.entity.TeamPlacement
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wertung je Partie: Finale A, B und C haben jeweils einen Ersten.
 *
 * Der Platz ist damit der Platz IN DER PARTIE und für sich genommen mehrdeutig - zwei Boote
 * tragen eine „1". Für Reihenfolge und Kategoriewertung zählt deshalb die Partie zuerst
 * (Setup-Reihenfolge über `weighting`) und der Platz danach; ohne das stünden in der
 * Ergebnisliste abwechselnd Erste und Zweite verschiedener Partien untereinander.
 */
class PerMatchPlacesTest {

    private fun team(startNumber: Int, category: RatingCategoryRef? = null) =
        CompetitionMatchTeamWithRegistration(
            id = UUID.randomUUID(),
            competitionMatch = UUID.randomUUID(),
            startNumber = startNumber,
            place = null,
            timeString = null,
            placesCalculated = true,
            competitionRegistration = UUID.randomUUID(),
            clubId = UUID.randomUUID(),
            clubName = "Verein $startNumber",
            registrationName = null,
            teamNumber = null,
            participants = emptyList(),
            deregistered = false,
            deregistrationReason = null,
            out = false,
            failed = false,
            failedReason = null,
            penaltySeconds = null,
            penaltyNote = null,
            ratingCategory = category,
            mixedTeamTerm = null,
        )

    /** Finale A (weighting 1) und Finale B (weighting 2) mit je zwei Booten. */
    private fun zweiFinals() = listOf(
        TeamPlacement(team(1), place = 1, matchName = "Finale A", matchWeighting = 1),
        TeamPlacement(team(2), place = 2, matchName = "Finale A", matchWeighting = 1),
        TeamPlacement(team(3), place = 1, matchName = "Finale B", matchWeighting = 2),
        TeamPlacement(team(4), place = 2, matchName = "Finale B", matchWeighting = 2),
    )

    @Test
    fun wertungJePartieBrauchtKeineSeedingListe() {
        assertNull(
            CompetitionExecutionService.getPlacesSeedingList(
                placesOption = CompetitionSetupPlacesOption.PER_MATCH.name,
                currentRoundTeams = listOf(3, 3),
                seedsForNextRound = 0,
                teamsInThisRound = 6,
            )
        )
    }

    @Test
    fun partienStehenNacheinanderUndNichtVerschraenkt() {
        val entries = CompetitionExecutionService.placesByRatingCategory(zweiFinals())
            .flatMap { it.entries }

        assertEquals(
            listOf("Finale A" to 1, "Finale A" to 2, "Finale B" to 1, "Finale B" to 2),
            entries.map { it.item.matchName to it.item.place },
        )
        assertEquals(listOf(1, 2, 3, 4), entries.map { it.categoryPlace })
    }

    @Test
    fun jedeWertungskategorieZaehltFuerSichAbEins() {
        val leicht = RatingCategoryRef(UUID.randomUUID(), "Leichtgewicht", 1)
        val offen = RatingCategoryRef(UUID.randomUUID(), "Offen", 2)

        val sections = CompetitionExecutionService.placesByRatingCategory(
            listOf(
                TeamPlacement(team(1, leicht), place = 1, matchName = "Finale A", matchWeighting = 1),
                TeamPlacement(team(2, offen), place = 2, matchName = "Finale A", matchWeighting = 1),
                TeamPlacement(team(3, leicht), place = 1, matchName = "Finale B", matchWeighting = 2),
                TeamPlacement(team(4, offen), place = 2, matchName = "Finale B", matchWeighting = 2),
            )
        )

        assertEquals(listOf("Leichtgewicht", "Offen"), sections.map { it.category?.name })
        sections.forEach { section ->
            assertEquals(listOf(1, 2), section.entries.map { it.categoryPlace })
        }
    }

    @Test
    fun ohneWertungJePartieEntscheidetWeiterhinAlleinDerPlatz() {
        // Gleichstand aus EQUAL: gleicher Platz, gleicher Kategorieplatz - unverändert zur
        // bisherigen Zählung, obwohl die Reihenfolge jetzt über einen Rang läuft.
        val entries = CompetitionExecutionService.placesByRatingCategory(
            listOf(
                TeamPlacement(team(1), place = 3),
                TeamPlacement(team(2), place = 3),
                TeamPlacement(team(3), place = 1),
            )
        ).flatMap { it.entries }

        assertEquals(listOf(1, 2, 2), entries.map { it.categoryPlace })
        assertEquals(listOf(1, 3, 3), entries.map { it.item.place })
    }
}
