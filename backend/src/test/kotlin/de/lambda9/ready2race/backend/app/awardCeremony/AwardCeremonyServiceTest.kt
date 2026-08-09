package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyService
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyKeyRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.seedClub
import de.lambda9.ready2race.backend.app.club.seedCrewMember
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.RATING_CATEGORY
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import org.apache.pdfbox.Loader
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Siegerehrungsbogen am echten Postgres. Was hier zählt und sich in den reinen Tests von
 * [AwardCeremonyLogicTest] nicht prüfen lässt, ist die Datenbeschaffung: dass die Ehrungen aus der
 * Platzberechnung entstehen, in der Reihenfolge der Rennnummern, und dass eine Auswahl genau die
 * Blätter ergibt, die auf dem Pult liegen sollen - nicht eines mehr und keines weniger.
 *
 * Die Veranstaltung ist deshalb so gebaut, dass jede dieser Fragen eine eigene Antwort hat: ein
 * Wettkampf mit zwei Wertungen, einer ohne Wertung, einer ganz ohne Platzierungen.
 */
class AwardCeremonyServiceTest {

    private data class SeededCeremonies(
        val eventId: UUID,
        /** Rennnummer „10" - zwei Wertungen, je zwei platzierte Boote. */
        val quadId: UUID,
        /** Rennnummer „2" - ohne Wertung, zwei platzierte Boote. */
        val sprintId: UUID,
        /** Rennnummer „3" - gemeldet, aber nie gefahren. */
        val unracedId: UUID,
    )

    @Test
    fun ceremoniesAreListedPerCompetitionAndCategory() = testComprehension {
        val seeded = seedCeremonies()

        val ceremonies = (!AwardCeremonyService.listCeremonies(seeded.eventId)).data

        // Rennnummer „2" vor „10": lexikografisch stünde die 10 vorn, die Sprecherin liest aber
        // nach Rennnummer.
        assertEquals(
            listOf(
                Triple("2", null, 2),
                Triple("10", "Masters A", 2),
                Triple("10", "Masters B", 2),
            ),
            ceremonies.map { Triple(it.competitionIdentifier, it.ratingCategoryName, it.placedTeams) },
        )

        val quad = ceremonies.first { it.competitionIdentifier == "10" }
        assertEquals(seeded.quadId, quad.competitionId)
        assertEquals("Coastal Quad", quad.competitionName)
        assertEquals("CQ", quad.competitionShortName)
    }

    @Test
    fun aCompetitionWithoutPlacedTeamsIsNotOffered() = testComprehension {
        val seeded = seedCeremonies()

        val ceremonies = (!AwardCeremonyService.listCeremonies(seeded.eventId)).data

        // Die anderen Wettkämpfe müssen dastehen, sonst bewiese eine leere Liste dasselbe.
        assertTrue(ceremonies.any { it.competitionId == seeded.quadId }, "Auswahlliste ist leer")
        assertTrue(
            ceremonies.none { it.competitionId == seeded.unracedId },
            "Ein Wettkampf ohne gesetzte Plätze gehört nicht auf die Auswahlliste: $ceremonies",
        )
    }

    @Test
    fun anEmptySelectionRendersEveryCeremony() = testComprehension {
        val seeded = seedCeremonies()

        val expected = (!AwardCeremonyService.listCeremonies(seeded.eventId)).data.size
        val file = !AwardCeremonyService.download(seeded.eventId, AwardCeremonySelectionRequest(selection = null))

        assertEquals("siegerehrung_Testregatta.pdf", file.name)
        assertEquals(expected, pagesOf(file.bytes))
    }

    @Test
    fun theSelectionLimitsThePdfToTheChosenCeremonies() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(AwardCeremonyKeyRequest(seeded.quadId, "Masters A"))
            ),
        )

        assertEquals(1, pagesOf(file.bytes))
    }

    /**
     * Die Wertung ohne Namen ist ein gültiger Wert, kein „nicht angegeben" - eine Auswahl mit
     * `null` muss deshalb dasselbe Blatt treffen wie eine mit Namen.
     */
    @Test
    fun aCeremonyWithoutARatingCategoryCanBeSelected() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(AwardCeremonyKeyRequest(seeded.sprintId, ratingCategoryName = null))
            ),
        )

        assertEquals(1, pagesOf(file.bytes))
    }

    @Test
    fun aCompetitionOfAnotherEventIsRejected() = testComprehension {
        val seeded = seedCeremonies()
        val otherEventId = seedEvent("Fremdregatta")
        val (foreignCompetitionId, _) = seedCompetition(otherEventId, identifier = "1", name = "Fremdlauf")

        assertKIOFails(AwardCeremonyError.CompetitionNotInEvent) {
            AwardCeremonyService.download(
                seeded.eventId,
                AwardCeremonySelectionRequest(
                    selection = listOf(AwardCeremonyKeyRequest(foreignCompetitionId, ratingCategoryName = null))
                ),
            )
        }
    }

    @Test
    fun anUnknownRatingCategoryIsRejected() = testComprehension {
        val seeded = seedCeremonies()

        assertKIOFails(AwardCeremonyError.UnknownRatingCategory) {
            AwardCeremonyService.download(
                seeded.eventId,
                AwardCeremonySelectionRequest(
                    selection = listOf(AwardCeremonyKeyRequest(seeded.quadId, "Masters Z"))
                ),
            )
        }
    }

    /**
     * Ein Wettkampf ohne Platzierungen ist keine Ehrung - wer ihn auswählt, bekommt kein leeres
     * Blatt, sondern eine Absage.
     */
    @Test
    fun aCompetitionWithoutPlacedTeamsCannotBeSelectedEither() = testComprehension {
        val seeded = seedCeremonies()

        assertKIOFails(AwardCeremonyError.UnknownRatingCategory) {
            AwardCeremonyService.download(
                seeded.eventId,
                AwardCeremonySelectionRequest(
                    selection = listOf(AwardCeremonyKeyRequest(seeded.unracedId, ratingCategoryName = null))
                ),
            )
        }
    }

    @Test
    fun anEventWithoutAnyPlacedTeamIsRejected() = testComprehension {
        val eventId = seedEvent("Leere Regatta")

        assertKIOFails(AwardCeremonyError.NoResults) {
            AwardCeremonyService.download(eventId, AwardCeremonySelectionRequest(selection = null))
        }
    }

    /**
     * Die Challenge-Prüfung steht vor allem anderen - deshalb wird sie hier an einer Veranstaltung
     * geprüft, die sonst gar nichts enthält: käme sie später, antwortete der Service mit
     * „keine Ergebnisse", und das Büro wartete auf Ergebnisse, die es nie geben wird.
     */
    @Test
    fun aChallengeEventIsRejectedBeforeAnythingElse() = testComprehension {
        val eventId = seedEvent("Challenge", challengeEvent = true)

        assertKIOFails(AwardCeremonyError.IsChallengeEvent) {
            AwardCeremonyService.listCeremonies(eventId)
        }
        assertKIOFails(AwardCeremonyError.IsChallengeEvent) {
            AwardCeremonyService.download(eventId, AwardCeremonySelectionRequest(selection = null))
        }
    }

    private fun pagesOf(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    // --- Vorrichtung ---------------------------------------------------------------------------

    private fun TestComprehensionScope<JEnv>.seedCeremonies(): SeededCeremonies {
        val eventId = seedEvent("Testregatta")

        val mastersA = seedRatingCategory("Masters A")
        val mastersB = seedRatingCategory("Masters B")
        val club = seedClub("Erster Kieler Ruder-Club von 1862 e.V.")
        // Ein Verein meldet einmal zur Veranstaltung an; daran hängen alle seine Boote.
        val registration = seedEventRegistration(eventId, club)

        val (quadId, quadProperties) = seedCompetition(eventId, "10", "Coastal Quad", shortName = "CQ")
        val quadMatch = seedFinal(quadProperties)
        // Die Plätze wechseln sich zwischen den Wertungen ab: so ist belegt, dass die Ränge je
        // Wertung neu gezählt werden und nicht der Platz im Gesamtfeld auf dem Blatt landet.
        seedTeam(registration, quadId, club, quadMatch, startNumber = 1, place = 1, ratingCategory = mastersA)
        seedTeam(registration, quadId, club, quadMatch, startNumber = 2, place = 2, ratingCategory = mastersB)
        seedTeam(registration, quadId, club, quadMatch, startNumber = 3, place = 3, ratingCategory = mastersA)
        seedTeam(registration, quadId, club, quadMatch, startNumber = 4, place = 4, ratingCategory = mastersB)

        val (sprintId, sprintProperties) = seedCompetition(eventId, "2", "Beachsprint")
        val sprintMatch = seedFinal(sprintProperties)
        seedTeam(registration, sprintId, club, sprintMatch, startNumber = 5, place = 1, ratingCategory = null)
        seedTeam(registration, sprintId, club, sprintMatch, startNumber = 6, place = 2, ratingCategory = null)

        // Gemeldet, aber nie gefahren: kein Lauf, keine Plätze.
        val (unracedId, _) = seedCompetition(eventId, "3", "Nicht gefahren")

        return SeededCeremonies(eventId, quadId, sprintId, unracedId)
    }

    private fun TestComprehensionScope<JEnv>.seedEvent(
        name: String,
        challengeEvent: Boolean = false,
    ): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = name,
                location = "Kiel",
                challengeEvent = challengeEvent,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !EVENT_DAY.insert(
            EventDayRecord(
                id = UUID.randomUUID(),
                event = eventId,
                date = CHAIN_SEED_TIME.toLocalDate(),
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.seedRatingCategory(name: String): UUID {
        val id = UUID.randomUUID()
        !RATING_CATEGORY.insert(
            RatingCategoryRecord(
                id = id,
                name = name,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return id
    }

    /** @return Wettkampf und seine Properties - an letzteren hängt das Setup. */
    private fun TestComprehensionScope<JEnv>.seedCompetition(
        eventId: UUID,
        identifier: String,
        name: String,
        shortName: String? = null,
    ): Pair<UUID, UUID> {
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()

        !COMPETITION.insert(
            CompetitionRecord(
                id = competitionId,
                event = eventId,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = identifier,
                name = name,
                shortName = shortName,
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(
                competitionProperties = propertiesId,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        return competitionId to propertiesId
    }

    /**
     * Eine einzige Runde als Massenfeld (`teams = null`) mit aufsteigenden Plätzen - so fährt
     * Coastal Rowing, und nur so vergibt die Platzberechnung 1, 2, 3, 4 statt viermal Platz 1.
     *
     * @return der Lauf, in den die Boote gesetzt werden.
     */
    private fun TestComprehensionScope<JEnv>.seedFinal(propertiesId: UUID): UUID {
        val roundId = UUID.randomUUID()
        val setupMatchId = UUID.randomUUID()

        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Finale",
                required = true,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.ASCENDING.name,
            )
        )
        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = setupMatchId,
                competitionSetupRound = roundId,
                weighting = 1,
                name = "Finale A",
                executionOrder = 1,
                teams = null,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = setupMatchId,
                startTime = CHAIN_SEED_TIME,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
                activatedAt = CHAIN_SEED_TIME,
            )
        )

        return setupMatchId
    }

    /** Die Anmeldung des Vereins zur Veranstaltung - es gibt sie je Verein genau einmal. */
    private fun TestComprehensionScope<JEnv>.seedEventRegistration(eventId: UUID, clubId: UUID): UUID {
        val id = UUID.randomUUID()
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = id,
                event = eventId,
                club = clubId,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return id
    }

    private fun TestComprehensionScope<JEnv>.seedTeam(
        eventRegistrationId: UUID,
        competitionId: UUID,
        clubId: UUID,
        setupMatchId: UUID,
        startNumber: Int,
        place: Int,
        ratingCategory: UUID?,
    ) {
        val registrationId = UUID.randomUUID()

        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = registrationId,
                eventRegistration = eventRegistrationId,
                competition = competitionId,
                club = clubId,
                name = "Boot $startNumber",
                ratingCategory = ratingCategory,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        seedCrewMember(registrationId, "1. Ruderer", "Boot${startNumber}Bug", clubId = clubId)
        seedCrewMember(registrationId, "2. Ruderer", "Boot${startNumber}Schlag", clubId = clubId)

        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = setupMatchId,
                competitionRegistration = registrationId,
                startNumber = startNumber,
                place = place,
                placesCalculated = true,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
    }
}
