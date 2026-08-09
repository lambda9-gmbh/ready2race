package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.EXPECTED_CLUBS
import de.lambda9.ready2race.backend.app.club.EXPECTED_FULL
import de.lambda9.ready2race.backend.app.club.FLENSBURG
import de.lambda9.ready2race.backend.app.club.MAINZ
import de.lambda9.ready2race.backend.app.club.MARBURG
import de.lambda9.ready2race.backend.app.club.NUERTINGEN
import de.lambda9.ready2race.backend.app.club.REGISTERING_CLUB
import de.lambda9.ready2race.backend.app.club.ROSTOCK
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.club.seedClub
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.club.seedCrewMember
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardTeamDto
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Vereinskette auf dem Schiedsrichter-Board, am echten Postgres.
 *
 * Die Ableitung selbst steht ohne Datenbank in
 * [de.lambda9.ready2race.backend.app.club.ClubCompositionTest], derselbe Fall für die
 * Athleten-Anzeige in [de.lambda9.ready2race.backend.app.club.ClubChainInDisplaysTest]. Was sich
 * nur gegen echte Daten prüfen lässt, sind die beiden Abfragen dahinter, und in beiden sieht ein
 * Fehler im Review vollkommen richtig aus:
 *
 * 1. Der Verein einer Person hängt an einem *zweiten*, aliasierten CLUB-Join
 *    ([de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo] `PARTICIPANT_CLUB`),
 *    während der bisherige CLUB-Join weiterhin den meldenden Verein liefert. Verwechselt man die
 *    beiden, steht auf jeder Karte der Verein, der das Boot angemeldet hat.
 * 2. Für eine Ersatzperson, die für diese Veranstaltung gar nicht gemeldet ist, schlägt
 *    `LiveDashboardRepo.getParticipantClubs` gesondert nach. Fällt diese Abfrage weg, verliert
 *    genau die Person ihren Verein - und zwar erst im Betrieb, nach dem Bootswechsel.
 *
 * Alle Kurzformen kommen hier aus gepflegten Einträgen (`club_short_name`), nicht aus der
 * Heuristik: die Heuristik ist Gegenstand eigener Tests und darf sich ändern, ohne diese Zusagen
 * zu bewegen.
 */
class LiveDashboardClubChainTest {

    private val berlin = "Berliner Ruder-Club von 1880 e.V."

    /** Kurzformen, die die Heuristik so nie erzeugen würde - jede Zusage hängt am Eintrag. */
    private val maintainedShortNames = mapOf(
        MAINZ to "Mainz",
        MARBURG to "Marburg",
        FLENSBURG to "Flensburg",
        NUERTINGEN to "Nürtingen",
        ROSTOCK to "Rostock",
        // Auch der meldende Verein hat eine Kurzform - fiele die Kette auf ihn zurück, stünde sie
        // in clubsShort und die Zusage unten wäre gebrochen.
        REGISTERING_CLUB to "Kiel EK",
    )

    @Test
    fun theRefereeBoardShowsTheClubsTheCrewWearsAndNotTheRegisteringClub() = testComprehension {
        val seeded = seedClubChain()
        maintainShortNames()

        val team = boardTeam(seeded.eventId)

        // Die Kette steht in Bootsreihenfolge: Mainz einmal (zwei Personen, ein Verein), der
        // "N.N."-Platz gar nicht - und das alles in der Reihenfolge der Rollen im Boot.
        assertEquals(EXPECTED_FULL, team.clubsFull)
        assertEquals("Mainz / Marburg / Flensburg / Nürtingen / Rostock", team.clubsShort)

        // Der Kern des Ganzen: der meldende Verein steht in keiner der beiden Ketten - obwohl er
        // im Datensatz weiterhin mitkommt, weil die Verwaltung ihn braucht.
        assertEquals(REGISTERING_CLUB, team.clubName)
        assertFalse(team.clubsFull.contains("Kieler"), "meldender Verein in der Kette: ${team.clubsFull}")
        assertFalse(team.clubsShort.contains("Kiel EK"), "meldender Verein in der Kurzkette: ${team.clubsShort}")
        assertFalse(team.clubsShort.contains("Renngemeinschaft"))
    }

    /**
     * Eine Ummeldung holt ein Vereinsmitglied ins Boot, das für diese Veranstaltung nirgends
     * gemeldet ist - der Fall, für den `wornClubsByParticipant` seine zweite Abfrage hat. Was der
     * Schiedsrichter nach einem Bootswechsel sieht, hängt allein an ihr.
     *
     * Die Ersatzperson steht am Ende der Kette, nicht auf dem Platz der ersetzten: die Crew nach
     * Ummeldungen entsteht in
     * [de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getActuallyParticipatingParticipants]
     * als "wer noch drin ist" plus "wer dazugekommen ist".
     */
    @Test
    fun aSubstitutionFromAnotherClubChangesTheChain() = testComprehension {
        val seeded = seedClubChain()
        maintainShortNames()
        !ClubShortNameRepo.upsert(shortNameOf(berlin, "Berlin"))

        substituteIntoBoat(seeded, replacing = "Cordes", withClub = berlin, lastName = "Hansen")

        val team = boardTeam(seeded.eventId)

        // Als Menge verglichen, nicht als Kette - siehe [chainLinks]: nach einer Ummeldung ist die
        // Reihenfolge auch mit dem orderBy nicht mehr die Bootsreihenfolge.
        assertEquals(setOf(MAINZ, MARBURG, NUERTINGEN, ROSTOCK, berlin), chainLinks(team.clubsFull).toSet())
        assertEquals(EXPECTED_CLUBS.size, chainLinks(team.clubsFull).size, "Kette mit doppeltem Glied: ${team.clubsFull}")
        assertEquals(
            setOf("Mainz", "Marburg", "Nürtingen", "Rostock", "Berlin"),
            chainLinks(team.clubsShort).toSet(),
        )

        // Der Verein der ausgebooteten Person ist weg, der der Ersatzperson da - genau das, was
        // ohne die zweite Abfrage still verschwände.
        assertFalse(team.clubsFull.contains("Flensburg"), "ersetzte Person noch in der Kette: ${team.clubsFull}")
        assertTrue(team.substituted, "die Karte müsste die Ummeldung ausweisen")
    }

    /**
     * Die dritte Anzeigestufe der Karte (`crew=true`): jede Person mit dem Verein, den SIE trägt.
     * Der "N.N."-Platzhalter bleibt hier stehen, während die Kette ihn still fallen lässt - in der
     * Crew-Zeile ist er die Aussage "Platz noch offen".
     */
    @Test
    fun theCrewLineCarriesEveryonesOwnClubShortName() = testComprehension {
        val seeded = seedClubChain()
        maintainShortNames()

        val team = boardTeam(seeded.eventId, crew = true)
        val crew = assertNotNull(team.crew, "crew=true müsste die Crew liefern")

        // In Bootsreihenfolge, wie die Karte sie zeigt.
        assertEquals(
            listOf(
                "Albers" to "Mainz",
                "Bruns" to "Marburg",
                "Cordes" to "Flensburg",
                "Dohm" to "Mainz",
                "Evers" to "N.N.",
                "Fischer" to "Nürtingen",
                "Groth" to "Rostock",
            ),
            crew.map { it.lastName to it.clubShort },
        )
        crew.forEach { assertNotNull(it.role, "Rolle fehlt bei ${it.lastName}") }

        // Ohne den Schalter bleibt die Crew aus der Nutzlast - der Endpunkt wird im Sekundentakt
        // gepollt.
        assertEquals(null, boardTeam(seeded.eventId).crew)
    }

    /**
     * Die Kette in ihre Glieder zerlegt - nur für den Ummelde-Fall, der als einziger die Menge der
     * Vereine statt der Kette prüft.
     *
     * Der Grund liegt nicht in der Abfrage:
     * [de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getActuallyParticipatingParticipants]
     * baut die startende Crew als "wer noch drin ist" plus "wer dazugekommen ist" - die Ersatzperson
     * hängt also hinten an, statt den Platz der ersetzten Person einzunehmen. Nach einer Ummeldung
     * ist die Reihenfolge damit auch mit dem `orderBy` in
     * [de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo.getTeams] nicht
     * mehr die Reihenfolge im Boot; festzuschreiben wäre hier nur die Reihenfolge, in der der Code
     * zusammensetzt, und die sagt über die Anzeige nichts aus.
     *
     * Alle Fälle ohne Ummeldung prüfen die Kette dagegen vollständig.
     */
    private fun chainLinks(chain: String): List<String> = chain.split(ClubComposition.SEPARATOR)

    private fun TestComprehensionScope<JEnv>.boardTeam(
        eventId: UUID,
        crew: Boolean = false,
    ): LiveDashboardTeamDto {
        val dashboard = (!LiveDashboardService.getLiveDashboard(eventId, LiveDashboardScope.LIVE, crew)).dto
        return dashboard.matches.single().teams.single()
    }

    private fun TestComprehensionScope<JEnv>.maintainShortNames() {
        maintainedShortNames.forEach { (name, short) -> !ClubShortNameRepo.upsert(shortNameOf(name, short)) }
    }

    private fun shortNameOf(name: String, short: String) = ClubShortNameRecord(
        nameKey = ClubNameKey.of(name),
        sampleName = name,
        shortName = short,
        createdAt = CHAIN_SEED_TIME,
        updatedAt = CHAIN_SEED_TIME,
    )

    /**
     * Bootswechsel in der Runde des Laufs: [replacing] geht raus, eine Person aus [withClub] kommt
     * herein. Die Ersatzperson ist bewusst NICHT gemeldet - nur so greift die Nachfrage über
     * `participant_in`.
     */
    private fun TestComprehensionScope<JEnv>.substituteIntoBoat(
        seeded: SeededClubChain,
        replacing: String,
        withClub: String,
        lastName: String,
    ) {
        val out = seeded.member(replacing)
        val substitute = seedCrewMember(
            registrationId = null,
            role = out.role,
            lastName = lastName,
            clubId = seedClub(withClub),
        )

        !SubstitutionRepo.insert(
            listOf(
                SubstitutionRecord(
                    id = UUID.randomUUID(),
                    competitionRegistration = seeded.registrationId,
                    competitionSetupRound = seeded.roundId,
                    participantOut = out.participantId,
                    participantIn = substitute.participantId,
                    reason = "krank",
                    orderForRound = 1,
                    namedParticipant = out.namedParticipantId,
                    createdAt = CHAIN_SEED_TIME,
                    updatedAt = CHAIN_SEED_TIME,
                )
            )
        )
    }
}
