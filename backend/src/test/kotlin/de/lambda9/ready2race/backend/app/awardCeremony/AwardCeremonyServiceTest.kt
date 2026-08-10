package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyService
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyError
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyKeyRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.REGISTERING_CLUB
import de.lambda9.ready2race.backend.app.club.seedClub
import de.lambda9.ready2race.backend.app.club.seedCrewMember
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
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
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimecodeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_RATING_CATEGORY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.RATING_CATEGORY
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Der Siegerehrungsbogen am echten Postgres. Was hier zählt und sich in den reinen Tests von
 * [AwardCeremonyLogicTest] nicht prüfen lässt, ist die Datenbeschaffung: dass die Ehrungen aus der
 * Platzberechnung entstehen, in der Reihenfolge der Rennnummern, dass Lauf, Zeit, Strafe und
 * Mannschaft am richtigen Boot landen, und dass eine Auswahl genau die Blätter ergibt, die auf dem
 * Pult liegen sollen - nicht eines mehr und keines weniger.
 *
 * Die Veranstaltung ist deshalb so gebaut, dass jede dieser Fragen eine eigene Antwort hat: ein
 * Wettkampf über zwei Runden mit zwei Wertungen, einer ohne Wertung mit drei ausgeschlossenen
 * Booten, einer ganz ohne Platzierungen.
 *
 * Geprüft wird über den ausgelesenen Seitentext, nicht über die Seitenzahl: griffe die Auswahl die
 * falsche Ehrung oder das falsche Boot, käme weiterhin genau eine Seite heraus.
 */
class AwardCeremonyServiceTest {

    /** Der Vorlauf - nur mit geplantem Start erfasst; auf dem Blatt steht er ohnehin nicht. */
    private val heatTime: LocalDateTime = CHAIN_SEED_TIME

    /** Der geplante Start des Finales; er darf dem tatsächlichen nicht vorgehen. */
    private val finalPlanned: LocalDateTime = CHAIN_SEED_TIME.withHour(14).withMinute(20)

    /** Der tatsächliche Start des Finales - das ist die Uhrzeit, die aufs Blatt gehört. */
    private val finalStarted: LocalDateTime = CHAIN_SEED_TIME.withHour(14).withMinute(35)

    private data class SeededCeremonies(
        val eventId: UUID,
        /** Rennnummer „10" - zwei Runden, zwei Wertungen; „Masters A" trägt vier platzierte Boote. */
        val quadId: UUID,
        /** Rennnummer „2" - ohne Wertung, zwei geehrte Boote hinter drei ausgeschlossenen. */
        val sprintId: UUID,
        /** Rennnummer „3" - gemeldet, aber nie gefahren. */
        val unracedId: UUID,
    )

    @Test
    fun ceremoniesAreListedPerCompetitionAndCategory() = testComprehension {
        val seeded = seedCeremonies()

        val ceremonies = (!AwardCeremonyService.listCeremonies(seeded.eventId)).data

        // Rennnummer „2" vor „10": lexikografisch stünde die 10 vorn, die Sprecherin liest aber
        // nach Rennnummer. Innerhalb des Wettkampfs steht „Masters B" vor „Masters A", weil die
        // Veranstaltung es so sortiert hat - siehe seedCeremonies.
        assertEquals(
            listOf(
                Triple("2", null, 2),
                Triple("10", "Masters B", 2),
                Triple("10", "Masters A", 3),
            ),
            ceremonies.map { Triple(it.competitionIdentifier, it.ratingCategoryName, it.awardedTeams) },
        )

        val quad = ceremonies.first { it.competitionIdentifier == "10" }
        assertEquals(seeded.quadId, quad.competitionId)
        assertEquals("Coastal Quad", quad.competitionName)
        assertEquals("CQ", quad.competitionShortName)
    }

    /**
     * Die Reihenfolge der Wertungen kommt aus der gepflegten `sortOrder` der Veranstaltung, nicht
     * mehr aus dem Alphabet - und der Dienst darf sie danach nicht umsortieren. Die Vorrichtung
     * setzt „Masters B" bewusst vor „Masters A": mit alphabetisch sortierten Namen bewiese diese
     * Zusicherung nichts.
     */
    @Test
    fun theCategoriesFollowTheConfiguredSortOrderNotTheAlphabet() = testComprehension {
        val seeded = seedCeremonies()

        val ceremonies = (!AwardCeremonyService.listCeremonies(seeded.eventId, seeded.quadId)).data

        assertEquals(listOf("Masters B", "Masters A"), ceremonies.map { it.ratingCategoryName })

        // Noch einmal am Blatt gemessen: die Auswahlliste könnte richtig sortiert sein und der
        // Druck trotzdem in der alten Reihenfolge herauskommen.
        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(
                    AwardCeremonyKeyRequest(seeded.quadId, "Masters A"),
                    AwardCeremonyKeyRequest(seeded.quadId, "Masters B"),
                )
            ),
        )

        assertEquals(2, pagesOf(file.bytes))
        assertContains(textOfPage(file.bytes, 1), "Wertung: Masters B")
        assertContains(textOfPage(file.bytes, 2), "Wertung: Masters A")
    }

    /**
     * Von der Platzierungsseite eines Wettkampfs aus zählt nur dieses eine Rennen, und die
     * Einschränkung gehört auf den Server: jede Ehrung kostet eine Platzberechnung, die teuerste
     * Rechnung des Wettkampfbereichs. Ein Dienst, der weiter alles sammelt und erst die Antwort
     * beschneidet, käme mit der ersten Zusicherung noch durch - die zweite fängt ihn: eine ID aus
     * einer fremden Veranstaltung fiele beim nachträglichen Filtern still unter den Tisch.
     */
    @Test
    fun theCeremonyListCanBeLimitedToOneCompetition() = testComprehension {
        val seeded = seedCeremonies()

        val ceremonies = (!AwardCeremonyService.listCeremonies(seeded.eventId, seeded.quadId)).data

        assertEquals(
            listOf("Masters B", "Masters A"),
            ceremonies.map { it.ratingCategoryName },
        )
        assertTrue(
            ceremonies.all { it.competitionId == seeded.quadId },
            "Nur der gewählte Wettkampf gehört in die Antwort: $ceremonies",
        )
    }

    @Test
    fun theCeremonyListRejectsACompetitionOfAnotherEvent() = testComprehension {
        val seeded = seedCeremonies()
        val otherEventId = seedEvent("Fremdregatta")
        val (foreignCompetitionId, _) = seedCompetition(otherEventId, identifier = "1", name = "Fremdlauf")

        assertKIOFails(AwardCeremonyError.CompetitionNotInEvent) {
            AwardCeremonyService.listCeremonies(seeded.eventId, foreignCompetitionId)
        }
    }

    /**
     * Die Zahl neben der Ehrung ist das Versprechen an die Auswahl. „Masters A" hat vier platzierte
     * Boote, Medaillen gibt es drei - genannt und gedruckt werden muss dieselbe Zahl.
     */
    @Test
    fun theChoiceCountsTheHonouredBoatsNotEveryPlacedOne() = testComprehension {
        val seeded = seedCeremonies()

        val mastersA = (!AwardCeremonyService.listCeremonies(seeded.eventId)).data
            .single { it.competitionIdentifier == "10" && it.ratingCategoryName == "Masters A" }
        assertEquals(3, mastersA.awardedTeams, "Vier platzierte Boote, aber nur drei Ränge")

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(listOf(AwardCeremonyKeyRequest(seeded.quadId, "Masters A"))),
        )

        // Dieselbe Zahl noch einmal am Blatt gemessen: das vierte Boot der Wertung (Startnummer 6)
        // steht nicht darauf, also darf die Auswahl es auch nicht mitzählen.
        assertEquals(
            listOf(1, 3, 5),
            startNumbersOf(file.bytes, page = 1),
            "Gedruckt wird bis Rang drei - die Auswahl muss dieselben Boote meinen",
        )
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

    /**
     * Die erwartete Seitenzahl steht fest und stammt aus der Vorrichtung, nicht aus dem Prüfling:
     * gegen `listCeremonies().size` verglichen wanderten beide Zahlen gemeinsam, wenn die
     * Sammelfunktion eine Ehrung verlöre.
     */
    @Test
    fun aMissingSelectionRendersEveryCeremony() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(seeded.eventId, AwardCeremonySelectionRequest(selection = null))

        assertEquals("siegerehrung_Testregatta.pdf", file.name)
        assertEveryCeremonyInOrder(file.bytes)
    }

    /** „Leer oder gar nicht ausgewählt" ist derselbe Fall - beide Wege müssen dasselbe drucken. */
    @Test
    fun anEmptySelectionRendersEveryCeremonyToo() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(selection = emptyList()),
        )

        assertEveryCeremonyInOrder(file.bytes)
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

        // Eine Seite käme auch bei vertauschter Wertung heraus - erst der Text sagt, ob es die
        // bestellte Ehrung ist.
        val text = textOfPage(file.bytes, 1)
        assertContains(text, "Coastal Quad")
        assertContains(text, "Wertung: Masters A")
        assertContains(linesOfPage(file.bytes, 1), "10 · CQ")
        assertFalse(text.contains("Masters B"), "Die nicht gewählte Wertung darf nicht auf dem Blatt stehen")
        assertFalse(text.contains("Beachsprint"), "Der nicht gewählte Wettkampf darf nicht auf dem Blatt stehen")
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

        val text = textOfPage(file.bytes, 1)
        assertContains(text, "Beachsprint")
        assertContains(linesOfPage(file.bytes, 1), "2")
        assertFalse(text.contains("Coastal Quad"), "Getroffen wurde der falsche Wettkampf")
        assertFalse(text.contains("Wertung"), "Ohne Kategorie darf keine Wertungszeile stehen")
    }

    /**
     * Der Platz eines Bootes entsteht in der letzten Runde, in der es vorkommt - auf dem Blatt
     * steht deshalb das Finale, nicht der Vorlauf. Mit nur einer Runde je Wettkampf wäre das nicht
     * zu belegen: eine Vorwärtssuche käme über einer einelementigen Liste zum selben Ergebnis.
     */
    @Test
    fun theRaceOnTheSheetIsTheLastOneTheBoatRacedIn() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(AwardCeremonyKeyRequest(seeded.quadId, "Masters A"))
            ),
        )

        val text = textOfPage(file.bytes, 1)
        assertContains(text, "Finale A")
        assertContains(text, "14:35")
        assertFalse(text.contains("Vorlauf"), "Der Platz entsteht im Finale - der Vorlauf gehört nicht aufs Blatt")
        assertFalse(text.contains("14:20"), "Gedruckt wird, wann gefahren wurde, nicht wann geplant war")
    }

    /**
     * Abgemeldete, ausgeschiedene und disqualifizierte Boote bekommen von der Platzberechnung einen
     * Platz, geehrt werden sie nicht. Sie stehen in der Vorrichtung bewusst auf den Plätzen eins bis
     * drei: hinter den geehrten Booten fielen sie ohnehin aus den Rängen, und ein fehlender Filter
     * bliebe unbemerkt.
     */
    @Test
    fun deregisteredRetiredAndDisqualifiedBoatsAreNotHonoured() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(AwardCeremonyKeyRequest(seeded.sprintId, ratingCategoryName = null))
            ),
        )

        // Die Reihenfolge zählt mit: ohne Filter trügen die drei ausgeschlossenen Boote die Ränge
        // eins bis drei, und diese beiden hier stünden gar nicht auf dem Blatt.
        assertEquals(
            listOf(7, 8),
            startNumbersOf(file.bytes, page = 1),
            "Nur die gewerteten Boote werden geehrt, und sie rücken auf die vorderen Ränge",
        )
    }

    /** Startnummer, Zeit, Strafe und Mannschaft - jedes Feld am Boot, zu dem es gehört. */
    @Test
    fun startNumberTimePenaltyAndCrewReachTheSheet() = testComprehension {
        val seeded = seedCeremonies()

        val file = !AwardCeremonyService.download(
            seeded.eventId,
            AwardCeremonySelectionRequest(
                selection = listOf(AwardCeremonyKeyRequest(seeded.quadId, "Masters A"))
            ),
        )

        val lines = linesOfPage(file.bytes, 1)

        // Rang, Verein und Zeit stehen auf einer Höhe: die Zeit gehört zum Sieger, nicht
        // irgendwohin auf das Blatt.
        assertContains(lines, "1. $REGISTERING_CLUB 4:12.7")
        assertContains(lines, "Boot „Boot 1“ · Startnummer 1")
        assertContains(lines, "Test Boot1Bug (1. Ruderer)")
        assertContains(lines, "Test Boot1Schlag (2. Ruderer)")

        // Die Strafe trägt das zweitplatzierte Boot (Startnummer 3) - stünde sie am Sieger, wäre
        // die Zuordnung vertauscht. Der Grund bricht in der schmalen Spalte um; gedruckt wird er.
        assertContains(lines, "2. $REGISTERING_CLUB Zeitstrafe +10 s")
        assertContains(lines, "(Frühstart)")
        assertContains(lines, "Boot „Boot 3“ · Startnummer 3")
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

    // --- Blattlesung ---------------------------------------------------------------------------

    private fun pagesOf(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    private fun textOfPage(bytes: ByteArray, page: Int): String =
        Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().apply {
                startPage = page
                endPage = page
            }.getText(doc)
        }

    private fun linesOfPage(bytes: ByteArray, page: Int): List<String> =
        textOfPage(bytes, page).lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Die Startnummern des Blatts in Rangfolge - so steht jedes Boot für seinen Platz. */
    private fun startNumbersOf(bytes: ByteArray, page: Int): List<Int> =
        Regex("Startnummer (\\d+)").findAll(textOfPage(bytes, page))
            .map { it.groupValues[1].toInt() }
            .toList()

    /**
     * Drei Blätter in der Reihenfolge der Rennnummern - die Zahl steht fest und stammt aus der
     * Vorrichtung: Beachsprint ohne Wertung, dann die beiden Wertungen des Coastal Quad in ihrer
     * gepflegten Reihenfolge.
     */
    private fun assertEveryCeremonyInOrder(bytes: ByteArray) {
        assertEquals(3, pagesOf(bytes))
        assertContains(textOfPage(bytes, 1), "Beachsprint")
        assertContains(textOfPage(bytes, 2), "Wertung: Masters B")
        assertContains(textOfPage(bytes, 3), "Wertung: Masters A")
    }

    // --- Vorrichtung ---------------------------------------------------------------------------

    /** Eine Runde mit genau einem Lauf; beide Kennungen werden gebraucht. */
    private data class SeededRound(val roundId: UUID, val setupMatchId: UUID)

    /** Eine Meldung; die Startnummer bleibt an ihr hängen, weil sie in jedem Lauf dieselbe ist. */
    private data class SeededBoat(val registrationId: UUID, val startNumber: Int)

    private fun TestComprehensionScope<JEnv>.seedCeremonies(): SeededCeremonies {
        val eventId = seedEvent("Testregatta")

        // Die gepflegte Reihenfolge widerspricht bewusst dem Alphabet: „Masters B" trägt die
        // vordere Stelle. Nur so belegt die Reihenfolge der Ehrungen, dass sie aus der sortOrder
        // stammt und nicht aus einer eigenen alphabetischen Sortierung des Bogens.
        val mastersA = seedRatingCategory(eventId, "Masters A", sortOrder = 1)
        val mastersB = seedRatingCategory(eventId, "Masters B", sortOrder = 0)
        val club = seedClub(REGISTERING_CLUB)
        // Ein Verein meldet einmal zur Veranstaltung an; daran hängen alle seine Boote.
        val registration = seedEventRegistration(eventId, club)

        val (quadId, quadProperties) = seedCompetition(eventId, "10", "Coastal Quad", shortName = "CQ")
        // Zwei Runden, damit die Suche nach dem Lauf eine Wahl hat: gefahren wird beides, der Platz
        // entsteht im Finale.
        val quadFinal = seedRound(
            quadProperties,
            roundName = "Finale",
            matchName = "Finale A",
            startTime = finalPlanned,
            startedAt = finalStarted,
        )
        val quadHeat = seedRound(
            quadProperties,
            roundName = "Vorlauf",
            matchName = "Vorlauf 1",
            startTime = heatTime,
            nextRound = quadFinal.roundId,
        )

        // Die Plätze wechseln sich zwischen den Wertungen ab: so ist belegt, dass die Ränge je
        // Wertung neu gezählt werden und nicht der Platz im Gesamtfeld auf dem Blatt landet.
        // „Masters A" bekommt vier Boote, damit sich die Zahl der geehrten von der der platzierten
        // unterscheidet.
        listOf(
            Triple(1, 1, mastersA),
            Triple(2, 2, mastersB),
            Triple(3, 3, mastersA),
            Triple(4, 4, mastersB),
            Triple(5, 5, mastersA),
            Triple(6, 6, mastersA),
        ).forEach { (startNumber, place, category) ->
            val boat = seedTeam(registration, quadId, club, startNumber, ratingCategory = category)
            seedMatchTeam(boat, quadHeat.setupMatchId, place = place)
            seedMatchTeam(
                boat,
                quadFinal.setupMatchId,
                place = place,
                // Zeit und Strafe hängen an verschiedenen Booten: eine vertauschte Zuordnung fiele
                // sonst nicht auf.
                timecode = if (startNumber == 1) seedTimecode(4, 12, 700) else null,
                penaltySeconds = if (startNumber == 3) 10 else null,
                penaltyNote = if (startNumber == 3) "Frühstart" else null,
            )
        }

        val (sprintId, sprintProperties) = seedCompetition(eventId, "2", "Beachsprint")
        val sprintFinal = seedRound(sprintProperties, roundName = "Finale", matchName = "Finale A", startTime = heatTime)
        seedMatchTeam(seedTeam(registration, sprintId, club, 7), sprintFinal.setupMatchId, place = 4)
        seedMatchTeam(seedTeam(registration, sprintId, club, 8), sprintFinal.setupMatchId, place = 5)

        // Die drei Ausschlussgründe stehen bewusst auf den vorderen Plätzen: hinter den gewerteten
        // Booten fielen sie ohnehin aus den Rängen, und ein fehlender Filter bliebe unsichtbar.
        val deregistered = seedTeam(registration, sprintId, club, 9)
        seedMatchTeam(deregistered, sprintFinal.setupMatchId, place = 1)
        seedDeregistration(deregistered, sprintFinal.roundId)

        seedMatchTeam(seedTeam(registration, sprintId, club, 10), sprintFinal.setupMatchId, place = 2, out = true)
        seedMatchTeam(seedTeam(registration, sprintId, club, 11), sprintFinal.setupMatchId, place = 3, failed = true)

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

    /**
     * Eine Wertungskategorie samt ihrer Stelle in der Abschnittsreihenfolge dieser Veranstaltung.
     * Die Zuordnung in `event_rating_category` ist kein Beiwerk: ohne sie trüge die Kategorie
     * [de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef.UNCONFIGURED_SORT_ORDER]
     * und sortierte sich wieder nach Namen.
     */
    private fun TestComprehensionScope<JEnv>.seedRatingCategory(
        eventId: UUID,
        name: String,
        sortOrder: Int,
    ): UUID {
        val id = UUID.randomUUID()
        !RATING_CATEGORY.insert(
            RatingCategoryRecord(
                id = id,
                name = name,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !EVENT_RATING_CATEGORY.insert(
            EventRatingCategoryRecord(
                event = eventId,
                ratingCategory = id,
                sortOrder = sortOrder,
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
     * Eine Runde als Massenfeld (`teams = null`) mit aufsteigenden Plätzen - so fährt Coastal
     * Rowing, und nur so vergibt die Platzberechnung 1, 2, 3, 4 statt viermal Platz 1.
     *
     * [nextRound] verkettet die Runden: die Runde ohne Nachfolger ist die letzte, und nur in ihr
     * entstehen die Plätze der Boote, die bis dorthin gekommen sind.
     */
    private fun TestComprehensionScope<JEnv>.seedRound(
        propertiesId: UUID,
        roundName: String,
        matchName: String,
        startTime: LocalDateTime,
        startedAt: LocalDateTime? = null,
        nextRound: UUID? = null,
    ): SeededRound {
        val roundId = UUID.randomUUID()
        val setupMatchId = UUID.randomUUID()

        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                nextRound = nextRound,
                name = roundName,
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
                name = matchName,
                executionOrder = 1,
                teams = null,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = setupMatchId,
                startTime = startTime,
                startedAt = startedAt,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
                activatedAt = CHAIN_SEED_TIME,
            )
        )

        return SeededRound(roundId, setupMatchId)
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

    /** Die Meldung mitsamt Mannschaft; in welchen Läufen sie fährt, sagt [seedMatchTeam]. */
    private fun TestComprehensionScope<JEnv>.seedTeam(
        eventRegistrationId: UUID,
        competitionId: UUID,
        clubId: UUID,
        startNumber: Int,
        ratingCategory: UUID? = null,
    ): SeededBoat {
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

        return SeededBoat(registrationId, startNumber)
    }

    /** Ein Boot in einem Lauf. Ohne Zeit, Strafe und Ausschlussgrund ist es ein gewertetes Boot. */
    private fun TestComprehensionScope<JEnv>.seedMatchTeam(
        boat: SeededBoat,
        setupMatchId: UUID,
        place: Int,
        timecode: UUID? = null,
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
        out: Boolean = false,
        failed: Boolean = false,
    ) {
        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = setupMatchId,
                competitionRegistration = boat.registrationId,
                startNumber = boat.startNumber,
                place = place,
                placesCalculated = true,
                timecode = timecode,
                penaltySeconds = penaltySeconds,
                penaltyNote = penaltyNote,
                out = out,
                failed = failed,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
    }

    /** Eine gefahrene Zeit, wie sie das Ziel meldet: `4:12.7` bei [minutes] 4, [seconds] 12. */
    private fun TestComprehensionScope<JEnv>.seedTimecode(minutes: Int, seconds: Int, millis: Int): UUID {
        val id = UUID.randomUUID()
        !TIMECODE.insert(
            TimecodeRecord(
                id = id,
                time = (minutes * 60_000L) + (seconds * 1_000L) + millis,
                baseUnit = Timecode.BaseUnit.MINUTES.name,
                millisecondPrecision = Timecode.MillisecondPrecision.ONE.name,
            )
        )
        return id
    }

    /** Die Abmeldung gilt je Runde - ohne die Runde sähe der Lauf das Boot weiter als gemeldet. */
    private fun TestComprehensionScope<JEnv>.seedDeregistration(boat: SeededBoat, roundId: UUID) {
        !COMPETITION_DEREGISTRATION.insert(
            CompetitionDeregistrationRecord(
                competitionRegistration = boat.registrationId,
                competitionSetupRound = roundId,
                reason = "Krankheit",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
    }
}
