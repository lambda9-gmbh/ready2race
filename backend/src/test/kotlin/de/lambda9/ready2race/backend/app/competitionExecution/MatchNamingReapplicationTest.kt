package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchNamingRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH_NAMING
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Lauf-Benennung bei der Rundenerzeugung, in beiden Teilen:
 *
 * 1. **Wieder-Anwendung der Benennungs-Sätze** (verpflichtende Runde, dort gibt es keine
 *    Freilose): Läufe, deren weighting im Satz für das AKTUELLE n nicht vorkommt, fallen auf
 *    ihren Ausgangszustand zurück, statt den Namen einer FRÜHEREN Anwendung zu behalten
 *    (zweimal "VF1", kein "VF2" - Nutzer-Screenshots vom 12.08.2026). Der Ausgangszustand wird
 *    seit V202608121200 beim ersten Überschreiben gesichert und GENAU wiederhergestellt - kein
 *    erfundener Name (siehe CompetitionSetupMatchRepo.applyNaming).
 *
 * 2. **Freilos-Benennung** (nicht verpflichtende Runde - der eigentliche Nutzer-Fall vom
 *    12.08.2026): Ein Lauf, in dem nur ein Boot fährt, heißt "Freilos <Setzungszahl>" statt
 *    eines Pseudo-Namens aus Satz oder Setup. Der echte Lauf folgt weiter dem Benennungs-Satz
 *    (das ehemalige VF-x wird zum neuen VF1) - diese Logik bleibt unberührt. Fährt der Lauf
 *    später wieder (Abmeldung zurückgenommen, Runde neu erzeugt), sorgt derselbe
 *    Ausgangszustand aus Teil 1 dafür, dass er nicht "Freilos" heißen bleibt.
 */
class MatchNamingReapplicationTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 12, 10, 0)

    private data class Seeded(
        val eventId: UUID,
        val competitionId: UUID,
        val roundId: UUID,
        /** Meldungs-Ids in teamNumber-Reihenfolge 1..8. */
        val registrationIds: List<UUID>,
    )

    /**
     * Ein Wettkampf mit einer einzigen (Viertelfinal-)Runde: vier Setup-Läufe à zwei Plätze
     * (Kapazität 8), acht Meldungen und frei wählbaren Benennungs-Sätzen
     * ([namingSets]: participantCount -> (weighting -> Name)).
     *
     * Standard ist der Aufbau des Wieder-Anwendungs-Falls: verpflichtende Runde, Ausgangsnamen
     * "Lauf 1".."Lauf 4", n=8 benennt alle vier Läufe (VF1..VF4), n=7 nur die weightings 1..3
     * (VF1..VF3) - genau die Lücke, in der vorher der Alt-Name stehen blieb. Die Freilos-Fälle
     * setzen [required] = false und eigene [setupNames]/[namingSets].
     */
    private fun TestComprehensionScope<JEnv>.seed(
        required: Boolean = true,
        setupNames: List<String> = listOf("Lauf 1", "Lauf 2", "Lauf 3", "Lauf 4"),
        namingSets: Map<Int, Map<Int, String>> = mapOf(
            8 to mapOf(1 to "VF1", 2 to "VF2", 3 to "VF3", 4 to "VF4"),
            7 to mapOf(1 to "VF1", 2 to "VF2", 3 to "VF3"),
        ),
    ): Seeded {
        val eventId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()

        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        !COMPETITION.insert(
            CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = "1",
                name = "Coastal Einer",
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(competitionProperties = propertiesId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Viertelfinale",
                required = required,
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )
        (1..4).forEach { weighting ->
            !COMPETITION_SETUP_MATCH.insert(
                CompetitionSetupMatchRecord(
                    id = UUID.randomUUID(),
                    competitionSetupRound = roundId,
                    weighting = weighting,
                    teams = 2,
                    name = setupNames[weighting - 1],
                    executionOrder = weighting,
                )
            )
        }

        // Benennungs-Sätze: Abweichungen vom Ausgangszustand je Bracket-Größe.
        namingSets.forEach { (participantCount, names) ->
            names.forEach { (weighting, name) ->
                !COMPETITION_SETUP_MATCH_NAMING.insert(
                    CompetitionSetupMatchNamingRecord(
                        competitionSetupRound = roundId,
                        participantCount = participantCount,
                        matchWeighting = weighting,
                        name = name,
                        executionOrder = null,
                    )
                )
            }
        }

        val registrationIds = (1..8).map { teamNumber ->
            val clubId = UUID.randomUUID()
            val eventRegistrationId = UUID.randomUUID()
            val registrationId = UUID.randomUUID()
            !CLUB.insert(ClubRecord(id = clubId, name = "RV Test $clubId", createdAt = now, updatedAt = now))
            !EVENT_REGISTRATION.insert(
                EventRegistrationRecord(
                    id = eventRegistrationId,
                    event = eventId,
                    club = clubId,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            !COMPETITION_REGISTRATION.insert(
                CompetitionRegistrationRecord(
                    id = registrationId,
                    eventRegistration = eventRegistrationId,
                    competition = competitionId,
                    club = clubId,
                    teamNumber = teamNumber,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            registrationId
        }

        return Seeded(eventId, competitionId, roundId, registrationIds)
    }

    private fun TestComprehensionScope<JEnv>.deregister(registrationId: UUID, roundId: UUID) {
        !COMPETITION_DEREGISTRATION.insert(
            CompetitionDeregistrationRecord(
                competitionRegistration = registrationId,
                competitionSetupRound = roundId,
                reason = "Krankheit",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /** Name und Reihenfolge der Setup-Läufe der Runde, nach weighting sortiert. */
    private fun TestComprehensionScope<JEnv>.namesByWeighting(roundId: UUID): List<Pair<String?, Int>> =
        (!CompetitionSetupMatchRepo.get(listOf(roundId)))
            .sortedBy { it.weighting }
            // execution_order ist NOT NULL - das `!!` deckt nur die generische Nullbarkeit des
            // jOOQ-Generators ab.
            .map { it.name to it.executionOrder!! }

    @Test
    fun reapplyingASmallerNamingSetResetsUncoveredMatchesToTheirSetupNames() = testComprehension {
        // Verpflichtende Runde: auch ein Lauf mit nur einem fahrenden Boot wird gefahren, es gibt
        // keine Freilose - hier zeigt sich die reine Wieder-Anwendung der Sätze.
        val seeded = seed()

        // Erste Erzeugung: n=8, alle vier Läufe bekommen ihre VF-Namen.
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )

        // Der Nutzer-Fall: Runde löschen, eine Abmeldung, mit n=7 neu erzeugen. Der Satz für 7
        // deckt nur die weightings 1..3 - weighting 4 muss auf seinen Setup-Namen zurück, statt
        // als zweites "VF..."-Duplikat aus der n=8-Anwendung stehen zu bleiben.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        val names = namesByWeighting(seeded.roundId)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "Lauf 4" to 4),
            names,
        )
        // Keine Duplikate mehr - genau die Verwirrung aus den Screenshots.
        assertEquals(names.map { it.first }.distinct().size, names.size)
    }

    @Test
    fun aMissingNamingSetResetsTheWholeRoundToItsSetupNames() = testComprehension {
        val seeded = seed()

        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )

        // Zwei Abmeldungen: n=6 hat gar keinen Benennungs-Satz - dann müssen ALLE Läufe zu ihren
        // Setup-Namen zurück, nicht nur die vom letzten Satz nicht abgedeckten.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        assertEquals(
            listOf<Pair<String?, Int>>("Lauf 1" to 1, "Lauf 2" to 2, "Lauf 3" to 3, "Lauf 4" to 4),
            namesByWeighting(seeded.roundId),
        )
    }

    /**
     * Der eigentliche Nutzer-Fall vom 12.08.2026, wörtlich: "Ich möchte einfach nur, dass ein
     * Freilos zu einem 'Freilos 1' Lauf wird anstatt ein Pseudo VF1 zu werden. Das VF1 ist in
     * unserem Beispiel das ehemalige VF2 und das muss auch so bleiben."
     *
     * Nicht verpflichtende Runde mit den Ausgangsnamen VF1..VF4, drei Abmeldungen -> n=5. Das
     * Schlangen-Seeding ([CompetitionExecutionService.getSeedingList]) paart {1,8} {2,7} {3,6}
     * {4,5}: der einzige echte Lauf ist weighting 4 (das ehemalige VF4 - im Beispiel des Nutzers
     * war es das VF2, die Rolle ist dieselbe), und genau ihn benennt der Satz für n=5 in "VF1"
     * um. Die drei Freilose tragen die Setzungszahl ihres fahrenden Boots - und das ehemalige
     * Ausgangs-"VF1" (weighting 1) taucht nirgends mehr als Pseudo-Duplikat auf.
     */
    @Test
    fun byesAreNamedFreilosWhileTheRealMatchFollowsTheNamingScheme() = testComprehension {
        val seeded = seed(
            required = false,
            setupNames = listOf("VF1", "VF2", "VF3", "VF4"),
            // Volle Kapazität (n=8) ist der Ausgangszustand und hat bewusst keinen Satz -
            // genau wie die Konfigurationsoberfläche es anbietet (CompetitionSetupRoundNaming).
            namingSets = mapOf(5 to mapOf(4 to "VF1")),
        )

        // Erste Erzeugung mit vollem Feld: kein Satz für n=8, die Ausgangsnamen bleiben.
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )

        // Runde löschen, drei Abmeldungen (Setzungen 6, 7, 8), mit n=5 neu erzeugen.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[5], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        val names = namesByWeighting(seeded.roundId)
        assertEquals(
            listOf<Pair<String?, Int>>(
                "Freilos 1" to 1,
                "Freilos 2" to 2,
                "Freilos 3" to 3,
                "VF1" to 4,
            ),
            names,
        )
        // Nirgends doppelte Namen - genau die Verwirrung aus den Screenshots.
        assertEquals(names.map { it.first }.distinct().size, names.size)
    }

    /**
     * Die Gegenrichtung - und der Grund, warum der Ausgangszustand (V202608121200) auch für die
     * Freilos-Benennung tragend ist: Werden die Abmeldungen zurückgenommen und die Runde mit
     * vollem Feld neu erzeugt, gibt es keinen Satz für n=8 und keine Freilose mehr - alle Läufe
     * müssen zu ihren Ausgangsnamen zurück. Ohne die Sicherung hieße ein wieder gefahrener Lauf
     * für immer "Freilos 1".
     */
    @Test
    fun aByeTurnedContestedAgainReturnsToItsSetupName() = testComprehension {
        val seeded = seed(
            required = false,
            setupNames = listOf("VF1", "VF2", "VF3", "VF4"),
            namingSets = mapOf(5 to mapOf(4 to "VF1")),
        )

        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[5], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<Pair<String?, Int>>(
                "Freilos 1" to 1,
                "Freilos 2" to 2,
                "Freilos 3" to 3,
                "VF1" to 4,
            ),
            namesByWeighting(seeded.roundId),
        )

        // Abmeldungen zurückgenommen, Runde mit vollem Feld neu erzeugt: keine Freilose mehr.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        !COMPETITION_DEREGISTRATION.delete { COMPETITION_SETUP_ROUND.eq(seeded.roundId) }
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        assertEquals(
            listOf<Pair<String?, Int>>("VF1" to 1, "VF2" to 2, "VF3" to 3, "VF4" to 4),
            namesByWeighting(seeded.roundId),
        )
    }
}
