package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyDensity
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AwardCeremonyLogicTest {

    private fun rower(
        firstName: String = "Anna",
        lastName: String = "Meier",
        role: String = "Ruderin",
        external: Boolean? = false,
        externalClubName: String? = null,
        ownClubName: String? = "Ruderclub Nürtingen",
    ) = AwardCeremonyCandidateParticipant(
        firstName = firstName,
        lastName = lastName,
        role = role,
        external = external,
        externalClubName = externalClubName,
        ownClubName = ownClubName,
    )

    private fun candidate(
        place: Int,
        startNumber: Int = place,
        ratingCategoryName: String? = null,
        registeringClubName: String = "Ruderclub Nürtingen",
        teamName: String? = "RCN I",
        time: String? = "4:12,7",
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
        roundName: String? = "Finale",
        matchName: String? = "Finale A",
        participants: List<AwardCeremonyCandidateParticipant> = listOf(rower()),
    ) = AwardCeremonyCandidate(
        competitionPlace = place,
        startNumber = startNumber,
        ratingCategoryName = ratingCategoryName,
        registeringClubName = registeringClubName,
        teamName = teamName,
        time = time,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
        roundName = roundName,
        matchName = matchName,
        matchTime = null,
        participants = participants,
    )

    @Test
    fun categoriesBecomeSeparateGroupsInAlphabeticalOrder() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(
            listOf(
                candidate(1, ratingCategoryName = "Masters B"),
                candidate(2, ratingCategoryName = "Masters A"),
                candidate(3, ratingCategoryName = "Masters B"),
            )
        )

        assertEquals(listOf("Masters A", "Masters B"), groups.map { it.first })
        assertEquals(listOf(1, 2), groups.map { it.second.size })
    }

    @Test
    fun competitionWithoutCategoriesBecomesOneGroupWithNullKey() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(listOf(candidate(1), candidate(2)))

        assertEquals(listOf(null), groups.map { it.first })
        assertEquals(2, groups.single().second.size)
    }

    @Test
    fun theGroupWithoutCategoryComesLast() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(
            listOf(
                candidate(1, ratingCategoryName = null),
                candidate(2, ratingCategoryName = "Masters A"),
            )
        )

        assertEquals(listOf("Masters A", null), groups.map { it.first })
    }

    @Test
    fun ranksRestartAtOneWithinTheCategory() {
        val ranks = AwardCeremonyLogic.rank(listOf(candidate(2), candidate(5), candidate(7), candidate(9)))

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
        assertEquals(listOf(false, false, false), ranks.map { it.shared })
    }

    @Test
    fun aTieOnSecondLeavesNoThirdRank() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1), candidate(2, startNumber = 4), candidate(2, startNumber = 9), candidate(5))
        )

        assertEquals(listOf(1, 2, 2), ranks.map { it.rank })
        assertEquals(listOf(false, true, true), ranks.map { it.shared })
        assertEquals(listOf(true, true, false), ranks.map { it.first })
    }

    @Test
    fun aTieOnFirstKeepsTheThirdRank() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1, startNumber = 2), candidate(1, startNumber = 6), candidate(3))
        )

        assertEquals(listOf(1, 1, 3), ranks.map { it.rank })
        assertEquals(listOf(true, true, false), ranks.map { it.shared })
    }

    @Test
    fun aTieAtTheCutoffPrintsEveryEntitledBoat() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1), candidate(2), candidate(4, startNumber = 3), candidate(4, startNumber = 8))
        )

        assertEquals(listOf(1, 2, 3, 3), ranks.map { it.rank })
    }

    @Test
    fun tiedBoatsAreOrderedByStartNumber() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1, startNumber = 9, teamName = "spät"), candidate(1, startNumber = 2, teamName = "früh"))
        )

        assertEquals(listOf("früh", "spät"), ranks.map { it.team.boatLine.substringAfter("Boot „").substringBefore("\"") })
    }

    @Test
    fun onlyThreeRanksReachThePage() {
        val ranks = AwardCeremonyLogic.rank((1..8).map { candidate(it) })

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
    }

    @Test
    fun oneClubForEveryoneCollapsesToASingleLine() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(firstName = "Bernd", ownClubName = "Ruderclub Nürtingen"),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertNull(team.registeringClub)
        assertEquals(listOf(null, null), team.athletes.map { it.club })
    }

    @Test
    fun aMixedCrewShowsTheClubChainAndTheRegisteringClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(firstName = "Bernd", ownClubName = "RG Hansa Kiel"),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen / RG Hansa Kiel", team.clubLine)
        assertEquals("Ruderclub Nürtingen", team.registeringClub)
        assertEquals(listOf("Ruderclub Nürtingen", "RG Hansa Kiel"), team.athletes.map { it.club })
    }

    @Test
    fun aGuestRowerCarriesTheirExternalClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(
                        firstName = "Sven",
                        external = true,
                        externalClubName = "Roskilde Roklub",
                        ownClubName = null,
                    ),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen / Roskilde Roklub", team.clubLine)
        assertEquals("Roskilde Roklub", team.athletes[1].club)
    }

    @Test
    fun aCrewRowingForAnotherClubNamesTheRegisteringClubSeparately() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderverein Meldestelle",
                participants = listOf(rower(ownClubName = "Ruderclub Nürtingen")),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertEquals("Ruderverein Meldestelle", team.registeringClub)
        assertNull(team.athletes.single().club)
    }

    @Test
    fun aTeamWithoutUsableClubDataFallsBackToTheRegisteringClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(rower(ownClubName = null, external = false)),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertNull(team.registeringClub)
    }

    @Test
    fun everyAthleteKeepsNameAndRole() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                participants = listOf(
                    rower(firstName = "Anna", lastName = "Meier", role = "Schlagfrau"),
                    rower(firstName = "Bernd", lastName = "Groß", role = "Steuermann"),
                ),
            )
        )

        assertEquals(listOf("Anna Meier", "Bernd Groß"), team.athletes.map { it.name })
        assertEquals(listOf("Schlagfrau", "Steuermann"), team.athletes.map { it.role })
    }

    @Test
    fun aBoatWithoutNameShowsOnlyTheStartNumber() {
        assertEquals("Startnummer 3", AwardCeremonyLogic.formatBoatLine(null, 3))
        assertEquals("Startnummer 3", AwardCeremonyLogic.formatBoatLine("  ", 3))
        assertEquals("Boot „RCN I\" · Startnummer 3", AwardCeremonyLogic.formatBoatLine("RCN I", 3))
    }

    @Test
    fun aPenaltyIsOnlyPrintedWhenThereIsOne() {
        assertNull(AwardCeremonyLogic.formatPenalty(null, null))
        assertNull(AwardCeremonyLogic.formatPenalty(null, "Frühstart"))
        assertEquals("Zeitstrafe +10 s", AwardCeremonyLogic.formatPenalty(10, null))
        assertEquals("Zeitstrafe +10 s (Frühstart)", AwardCeremonyLogic.formatPenalty(10, "Frühstart"))
    }

    @Test
    fun theRaceLineShrinksToWhatIsKnown() {
        val at = LocalDateTime.of(2026, 8, 15, 14, 35)

        assertEquals("Finale A · 15.08., 14:35", AwardCeremonyLogic.formatRaceLine("Finale", "Finale A", at))
        assertEquals("Finale · 15.08., 14:35", AwardCeremonyLogic.formatRaceLine("Finale", null, at))
        assertEquals("Finale A", AwardCeremonyLogic.formatRaceLine("Finale", "Finale A", null))
        assertNull(AwardCeremonyLogic.formatRaceLine(null, null, null))
    }

    @Test
    fun aCrowdedPageMovesDownOneStep() {
        assertEquals(AwardCeremonyDensity.NORMAL, AwardCeremonyLogic.densityFor(18))
        assertEquals(AwardCeremonyDensity.COMPACT, AwardCeremonyLogic.densityFor(19))
    }

    @Test
    fun theSheetCarriesHeadingsRanksAndDensity() {
        val sheet = AwardCeremonyLogic.sheet(
            eventName = "Küstenregatta Kiel",
            eventDate = "15.–16. August 2026",
            eventLocation = "Kiel",
            competitionIdentifier = "17-NC",
            competitionShortName = "CM 4x+",
            competitionName = "Mixed-Coastal-Vierer mit Steuermann",
            ratingCategoryName = "Masters A",
            candidates = listOf(candidate(1), candidate(2)),
        )

        assertEquals("Masters A", sheet.ratingCategoryName)
        assertEquals(listOf(1, 2), sheet.ranks.map { it.rank })
        assertEquals(AwardCeremonyDensity.NORMAL, sheet.density)
        assertNull(sheet.ceremonyTime)
    }
}
