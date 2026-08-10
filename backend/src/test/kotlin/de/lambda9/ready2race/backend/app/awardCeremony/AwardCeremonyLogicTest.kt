package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RankedEntry
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AwardCeremonyLogicTest {

    private val mastersA = RatingCategoryRef(UUID.randomUUID(), "Masters A", 0)

    /**
     * Genau der Weg, den [de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyService]
     * geht: erst zählt `RatingCategoryRanking` die Wertung durch, dann schneidet der Bogen sie auf
     * die Medaillenränge. Die Ränge hier direkt aus Kandidaten zu bauen, ließe genau die Fuge
     * ungeprüft, um die es geht.
     */
    private fun ranks(candidates: List<AwardCeremonyCandidate>) =
        AwardCeremonyLogic.ranks(sectionOf(candidates))

    /** `single()` statt `flatMap`: ein Blatt trägt genau eine Wertung, und das soll auffallen. */
    private fun sectionOf(candidates: List<AwardCeremonyCandidate>): List<RankedEntry<AwardCeremonyCandidate>> =
        RatingCategoryRanking.groupAndRank(
            items = candidates,
            category = { it.ratingCategory },
            place = { it.competitionPlace },
            tieBreak = { it.startNumber },
        ).let { sections -> if (sections.isEmpty()) emptyList() else sections.single().entries }

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
        ratingCategory: RatingCategoryRef? = null,
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
        ratingCategory = ratingCategory,
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
    fun categoriesBecomeSeparateSectionsInTheConfiguredOrder() {
        // Bis zum 09.08.2026 sortierte der Bogen die Wertungen selbst und alphabetisch. Jetzt
        // stammt die Reihenfolge aus der für die Veranstaltung gepflegten sortOrder - hier bewusst
        // gegen das Alphabet gesetzt, sonst bewiese die Zusicherung nichts.
        val zuerst = RatingCategoryRef(UUID.randomUUID(), "Masters B", 0)
        val danach = RatingCategoryRef(UUID.randomUUID(), "Masters A", 1)

        val sections = RatingCategoryRanking.groupAndRank(
            items = listOf(
                candidate(1, ratingCategory = danach),
                candidate(2, ratingCategory = zuerst),
                candidate(3, ratingCategory = danach),
            ),
            category = { it.ratingCategory },
            place = { it.competitionPlace },
            tieBreak = { it.startNumber },
        )

        assertEquals(listOf("Masters B", "Masters A"), sections.map { it.category?.name })
        assertEquals(listOf(1, 2), sections.map { it.entries.size })
    }

    @Test
    fun competitionWithoutCategoriesBecomesOneSectionWithoutACategory() {
        val sections = RatingCategoryRanking.groupAndRank(
            items = listOf(candidate(1), candidate(2)),
            category = { it.ratingCategory },
            place = { it.competitionPlace },
            tieBreak = { it.startNumber },
        )

        assertNull(sections.single().category)
        assertEquals(2, sections.single().entries.size)
    }

    @Test
    fun groupingAnEmptyListYieldsNoSections() {
        assertEquals(emptyList(), sectionOf(emptyList()))
    }

    @Test
    fun theSectionWithoutCategoryComesLast() {
        val sections = RatingCategoryRanking.groupAndRank(
            items = listOf(candidate(1, ratingCategory = null), candidate(2, ratingCategory = mastersA)),
            category = { it.ratingCategory },
            place = { it.competitionPlace },
            tieBreak = { it.startNumber },
        )

        assertEquals(listOf("Masters A", null), sections.map { it.category?.name })
    }

    @Test
    fun ranksRestartAtOneWithinTheCategory() {
        val ranks = ranks(listOf(candidate(2), candidate(5), candidate(7), candidate(9)))

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
        assertEquals(listOf(false, false, false), ranks.map { it.shared })
    }

    @Test
    fun aTieOnSecondLeavesNoThirdRank() {
        val ranks = ranks(
            listOf(candidate(1), candidate(2, startNumber = 4), candidate(2, startNumber = 9), candidate(5))
        )

        assertEquals(listOf(1, 2, 2), ranks.map { it.rank })
        assertEquals(listOf(false, true, true), ranks.map { it.shared })
        assertEquals(listOf(true, true, false), ranks.map { it.first })
    }

    @Test
    fun aTieOnFirstKeepsTheThirdRank() {
        val ranks = ranks(
            listOf(candidate(1, startNumber = 2), candidate(1, startNumber = 6), candidate(3))
        )

        assertEquals(listOf(1, 1, 3), ranks.map { it.rank })
        assertEquals(listOf(true, true, false), ranks.map { it.shared })
    }

    @Test
    fun aTieAtTheCutoffPrintsEveryEntitledBoat() {
        val ranks = ranks(
            listOf(candidate(1), candidate(2), candidate(4, startNumber = 3), candidate(4, startNumber = 8))
        )

        assertEquals(listOf(1, 2, 3, 3), ranks.map { it.rank })
    }

    @Test
    fun tiedBoatsAreOrderedByStartNumber() {
        val ranks = ranks(
            listOf(candidate(1, startNumber = 9, teamName = "spät"), candidate(1, startNumber = 2, teamName = "früh"))
        )

        assertEquals(listOf("früh", "spät"), ranks.map { it.team.boatLine.substringAfter("Boot „").substringBefore("“") })
    }

    @Test
    fun onlyThreeRanksReachThePage() {
        val ranks = ranks((1..8).map { candidate(it) })

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
    }

    @Test
    fun rankingAnEmptyListYieldsNoRanks() {
        assertEquals(emptyList(), ranks(emptyList()))
    }

    @Test
    fun aBoatWithoutAPlaceDoesNotReachTheSheet() {
        // Ungewertete Boote stehen in der Ergebnisliste weiterhin am Ende ihres Abschnitts - auf
        // einem Siegerehrungsbogen haben sie nichts zu suchen. Ohne diese Zusicherung rutschte ein
        // abgemeldetes Boot auf einen freien Medaillenrang.
        val ranks = AwardCeremonyLogic.ranks(
            listOf(
                RankedEntry(candidate(1, teamName = "gewertet"), categoryPlace = 1),
                RankedEntry(candidate(2, teamName = "abgemeldet"), categoryPlace = null),
            )
        )

        assertEquals(listOf(1), ranks.map { it.rank })
        assertEquals(
            listOf("gewertet"),
            ranks.map { it.team.boatLine.substringAfter("Boot „").substringBefore("“") },
        )
    }

    @Test
    fun aTieOnFirstWithFourBoatsPrintsAllFourAtRankOne() {
        // Beginnt der Gleichstand schon auf Rang 1, kommen laut KDoc an ranks() *alle* Boote der
        // Gruppe aufs Blatt - auch wenn das mehr als drei Blöcke ergibt.
        val ranks = ranks(
            listOf(
                candidate(1, startNumber = 1),
                candidate(1, startNumber = 2),
                candidate(1, startNumber = 3),
                candidate(1, startNumber = 4),
            )
        )

        assertEquals(listOf(1, 1, 1, 1), ranks.map { it.rank })
        assertEquals(listOf(true, true, true, true), ranks.map { it.shared })
        assertEquals(listOf(true, false, false, false), ranks.map { it.first })
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
    fun twoSpellingsOfTheSameClubCollapseToASingleLine() {
        // "Rostocker Ruderclub" und "Rostocker Ruder-Club von 1885 e.V." sind nach ClubNameKey
        // derselbe Verein. ClubComposition fasst sie zu einem Kettenglied zusammen (mit der
        // zuerst gesehenen Schreibweise) - der Vereinsvergleich in team() darf das nicht per
        // rohem Stringvergleich wieder auseinanderreißen, sonst sähe ein Vereinsboot wie eine
        // Renngemeinschaft aus.
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                // Bewusst die andere Schreibweise als die der Titelzeile: nur so trägt
                // assertNull(team.registeringClub) unten wirklich - mit derselben Schreibweise
                // bestünde die Zusicherung auch bei rohem Stringvergleich.
                registeringClubName = "Rostocker Ruder-Club von 1885 e.V.",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Rostocker Ruderclub"),
                    rower(firstName = "Bernd", ownClubName = "Rostocker Ruder-Club von 1885 e.V."),
                ),
            )
        )

        assertEquals("Rostocker Ruderclub", team.clubLine)
        assertNull(team.registeringClub)
        assertEquals(listOf(null, null), team.athletes.map { it.club })
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
        assertEquals("Boot „RCN I“ · Startnummer 3", AwardCeremonyLogic.formatBoatLine("RCN I", 3))
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
    fun aBlankMatchNameFallsBackToTheRoundNameInsteadOfVanishing() {
        // Ein leerer, aber nicht-null matchName darf den vorhandenen Rundennamen nicht
        // verschlucken - dieselbe Regel wie bei formatBoatLine und formatPenalty.
        assertEquals("Finale · 15.08., 14:35", AwardCeremonyLogic.formatRaceLine("Finale", "  ", LocalDateTime.of(2026, 8, 15, 14, 35)))
        assertEquals("Finale", AwardCeremonyLogic.formatRaceLine("Finale", "", null))
    }

    @Test
    fun onlyATimeWithoutAnyNameStillPrintsTheTime() {
        val at = LocalDateTime.of(2026, 8, 15, 14, 35)

        assertEquals("15.08., 14:35", AwardCeremonyLogic.formatRaceLine(null, null, at))
        assertEquals("15.08., 14:35", AwardCeremonyLogic.formatRaceLine("  ", "  ", at))
    }

    @Test
    fun theSheetCarriesHeadingsAndRanks() {
        val sheet = AwardCeremonyLogic.sheet(
            eventName = "Küstenregatta Kiel",
            eventDate = "15.–16. August 2026",
            eventLocation = "Kiel",
            competitionIdentifier = "17-NC",
            competitionShortName = "CM 4x+",
            competitionName = "Mixed-Coastal-Vierer mit Steuermann",
            ratingCategoryName = "Masters A",
            entries = sectionOf(listOf(candidate(1), candidate(2))),
        )

        assertEquals("Masters A", sheet.ratingCategoryName)
        assertEquals(listOf(1, 2), sheet.ranks.map { it.rank })
        assertNull(sheet.ceremonyTime)
    }
}
