package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.findOneBy
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
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH_NAMING
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Materialisierung der Freilos-Namen (Anforderung vom 12.08.2026, wörtlich): "Ich möchte
 * einfach nur, dass ein Freilos zu einem 'Freilos 1' Lauf wird anstatt ein Pseudo VF1 zu werden.
 * Das VF1 ist in unserem Beispiel das ehemalige VF2 und das muss auch so bleiben."
 *
 * Zwei Festlegungen stecken darin:
 * 1. Die Benennungs-Anwendung je Teilnehmerzahl bleibt VOLLSTÄNDIG das Original (nur die Läufe,
 *    deren weighting im Satz für das aktuelle n vorkommt, werden auf der Setup-Vorlage
 *    überschrieben - inklusive der Eigenheit, dass ein einmal angewandter Satz-Name auf der
 *    Vorlage stehen bleibt).
 * 2. Freilos-Läufe (genau ein fahrendes Boot, Runde nicht verpflichtend) bekommen ihren Namen
 *    "Freilos <Setzungszahl>" an der LAUF-INSTANZ (competition_match.bye_name, V202608121300);
 *    gelesen wird überall coalesce(bye_name, Setup-Name). Weil die Instanz beim Löschen der
 *    Runde stirbt, heilt Neu-Erzeugen - der Arbeitsfluss bei jeder (auch zurückgenommenen)
 *    An- oder Abmeldung - den Namen strukturell, ganz ohne Reset-Mechanik auf der Vorlage.
 */
class MatchByeNamingTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 12, 10, 0)

    private data class Seeded(
        val eventId: UUID,
        val competitionId: UUID,
        val roundId: UUID,
        /** Meldungs-Ids in teamNumber-Reihenfolge 1..8. */
        val registrationIds: List<UUID>,
    )

    /**
     * Der Aufbau des Nutzer-Beispiels: eine einzige, NICHT verpflichtende (Viertelfinal-)Runde
     * mit vier Setup-Läufen à zwei Plätze (Kapazität 8), Ausgangsnamen "VF1".."VF4", acht
     * Meldungen. Der Benennungs-Satz existiert nur für n=5 und benennt allein weighting 4 um -
     * das ist beim Schlangen-Seeding ({1,8} {2,7} {3,6} {4,5}) der einzige Lauf, der mit fünf
     * Booten noch stattfindet. Volle Kapazität hat bewusst keinen Satz, genau wie die
     * Konfigurationsoberfläche es anbietet (CompetitionSetupRoundNaming).
     */
    private fun TestComprehensionScope<JEnv>.seed(): Seeded {
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
                identifier = "12",
                name = "Coastal Einer",
                shortName = "JM4x",
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
                required = false,
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
                    name = "VF$weighting",
                    executionOrder = weighting,
                )
            )
        }

        // Der Satz für n=5: das ehemalige VF4 wird zum neuen VF1 (beim Nutzer war es das VF2 -
        // dieselbe Rolle, anderes Seeding-Layout).
        !COMPETITION_SETUP_MATCH_NAMING.insert(
            CompetitionSetupMatchNamingRecord(
                competitionSetupRound = roundId,
                participantCount = 5,
                matchWeighting = 4,
                name = "VF1",
                executionOrder = null,
            )
        )

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

    /** Die Setup-Läufe der Runde, nach weighting sortiert. */
    private fun TestComprehensionScope<JEnv>.setupMatches(roundId: UUID): List<CompetitionSetupMatchRecord> =
        (!CompetitionSetupMatchRepo.get(listOf(roundId))).sortedBy { it.weighting }

    /** Der materialisierte Freilos-Name der Lauf-Instanz - null, wenn keine Instanz existiert. */
    private fun TestComprehensionScope<JEnv>.byeName(setupMatchId: UUID): String? =
        (!COMPETITION_MATCH.findOneBy { COMPETITION_SETUP_MATCH.eq(setupMatchId) })?.byeName

    /** Der Anzeigename: dieselbe Koaleszenz wie alle Lesepfade. */
    private fun TestComprehensionScope<JEnv>.effectiveNames(roundId: UUID): List<String?> =
        setupMatches(roundId).map { byeName(it.id!!) ?: it.name }

    @Test
    fun byesMaterializeTheirNameWhileTheNamingLogicStaysOriginal() = testComprehension {
        val seeded = seed()

        // Volles Feld: kein Satz für n=8, keine Freilose - alles bleibt beim Ausgangszustand.
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(listOf<String?>("VF1", "VF2", "VF3", "VF4"), effectiveNames(seeded.roundId))

        // Der Nutzer-Fall: Runde löschen, drei Abmeldungen (Setzungen 6-8), mit n=5 neu erzeugen.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[5], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        // Die Setup-Vorlage zeigt EXAKT die originale Benennungs-Anwendung: nur weighting 4 (der
        // einzige stattfindende Lauf) trägt den Satz-Namen "VF1", die übrigen bleiben unberührt -
        // niemand setzt etwas zurück, nichts wird erfunden.
        val setup = setupMatches(seeded.roundId)
        assertEquals(listOf<String?>("VF1", "VF2", "VF3", "VF1"), setup.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), setup.map { it.executionOrder })

        // Die drei Freilose tragen ihren Namen an der Instanz - die Setzungszahl des fahrenden
        // Boots -, der echte Lauf keinen.
        assertEquals(
            listOf("Freilos 1", "Freilos 2", "Freilos 3", null),
            setup.map { byeName(it.id!!) },
        )

        // Der Anzeigename überall: das ehemalige VF4 ist das neue VF1, die Freilose sind als
        // solche lesbar, und das Pseudo-"VF1" aus der Vorlage (weighting 1) ist nirgends zu sehen.
        val effective = effectiveNames(seeded.roundId)
        assertEquals(listOf<String?>("Freilos 1", "Freilos 2", "Freilos 3", "VF1"), effective)
        assertEquals(effective.distinct().size, effective.size)
    }

    /**
     * Der Rückweg - und der Grund, warum die Materialisierung an der Instanz statt der Vorlage
     * hängt: Werden die Abmeldungen zurückgenommen und die Runde mit vollem Feld neu erzeugt,
     * sterben die alten Instanzen samt Freilos-Namen. Die Freilos-Läufe von eben zeigen wieder
     * ihre unberührten Setup-Namen - strukturell, ohne jede Reset-Mechanik.
     */
    @Test
    fun recreatingWithFullFieldShedsTheByeNames() = testComprehension {
        val seeded = seed()

        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        deregister(seeded.registrationIds[5], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)
        assertEquals(
            listOf<String?>("Freilos 1", "Freilos 2", "Freilos 3", "VF1"),
            effectiveNames(seeded.roundId),
        )

        // Abmeldungen zurückgenommen, Runde mit vollem Feld neu erzeugt.
        !CompetitionExecutionService.deleteCurrentRound(seeded.competitionId, seeded.eventId)
        !COMPETITION_DEREGISTRATION.delete { COMPETITION_SETUP_ROUND.eq(seeded.roundId) }
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        // Keine Instanz trägt mehr einen Freilos-Namen.
        assertEquals(listOf(null, null, null, null), setupMatches(seeded.roundId).map { byeName(it.id!!) })

        // Die ehemaligen Freilose (weightings 1-3) zeigen wieder ihre nie angetasteten
        // Setup-Namen. Weighting 4 behält das "VF1" der n=5-Anwendung: Für volle Kapazität gibt
        // es keinen Satz, und die originale Benennungs-Logik setzt nichts zurück - genau diese
        // Eigenheit gehört zum unverändert gewünschten Original (der Nutzer pflegt seine Sätze
        // je n und erstellt Runden neu).
        assertEquals(listOf<String?>("VF1", "VF2", "VF3", "VF1"), effectiveNames(seeded.roundId))
    }

    /**
     * Ein Lesepfad je Gattung, am Zustand des Nutzer-Falls: Startlisten-View (PDF/CSV samt
     * Dateiname), Wellenname des Zeitnahme-Systems und die Zeitplan-Abfrage der nicht verplanten
     * Läufe (Boards/Zeitplan lesen dasselbe "match_name"-Muster).
     */
    @Test
    fun readPathsCarryTheMaterializedByeName() = testComprehension {
        val seeded = seed()
        deregister(seeded.registrationIds[5], seeded.roundId)
        deregister(seeded.registrationIds[6], seeded.roundId)
        deregister(seeded.registrationIds[7], seeded.roundId)
        !CompetitionExecutionService.createNewRound(seeded.eventId, seeded.competitionId, SYSTEM_USER)

        val byeMatchId = setupMatches(seeded.roundId).first { it.weighting == 1 }.id!!

        // Startlisten-View: speist buildCsv, PDF und den Dateinamen des Exports.
        val startlist = !CompetitionMatchRepo.getForStartList(byeMatchId)
        assertEquals("Freilos 1", assertNotNull(startlist).name)

        // Wellenname: dieselbe Koaleszenz wie der Export, sonst fände der Abgleich die Welle nicht.
        val target = !CompetitionMatchRepo.getForRaceClockerPull(byeMatchId)
        assertEquals("12 JM4x | Freilos 1", assertNotNull(target).waveName)

        // Zeitplan/Boards: die "match_name"-Spalte der Slot-/Unverplant-Abfragen.
        val unplanned = !EventScheduleRepo.getUnplannedSetupMatches(seeded.eventId)
        val names = unplanned.map { it.get("match_name", String::class.java) }
        assertTrue("Freilos 1" in names && "VF1" in names, "erwartet Freilos 1 und VF1 in $names")
        assertTrue(names.count { it == "VF1" } == 1, "das Pseudo-VF1 der Vorlage darf nicht erscheinen: $names")
    }
}
