package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameService
import de.lambda9.ready2race.backend.app.club.entity.ClubShortNameRequest
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [ClubShortNameService] gegen ein echtes Postgres.
 *
 * Die eine Sache, die diese Ebene prüft und keine reine Funktion prüfen kann: dass die Liste beide
 * Quellen der Vereinsnamen zusammenwirft. Ein Verein steht mal als Datensatz in `club`, mal als
 * Freitext an einer Person - im Produktivstand der CRF 2026 stehen 29 der 46 vorkommenden
 * Schreibweisen ausschließlich an Personen. Liest die Abfrage nur eine der beiden Quellen, ist die
 * Pflegeseite nicht falsch, sondern unvollständig, und das fällt erst am Regattatag auf.
 */
class ClubShortNameServiceTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    private val rostock = "Rostocker Ruderclub"
    private val rostockLong = "Rostocker Ruder-Club von 1885 e.V."

    private fun TestComprehensionScope<JEnv>.adminId(): UUID =
        assertNotNull(
            !Jooq.query { select(APP_USER.ID).from(APP_USER).limit(1).fetchOne(APP_USER.ID) },
            "Ohne angelegten Benutzer lässt sich keine Kurzform pflegen",
        )

    private fun TestComprehensionScope<JEnv>.club(name: String): UUID {
        val id = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = id, name = name, createdAt = now, updatedAt = now))
        return id
    }

    private fun TestComprehensionScope<JEnv>.participant(
        clubId: UUID,
        lastname: String,
        externalClubName: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = id,
                club = clubId,
                firstname = "Test",
                lastname = lastname,
                year = 1990,
                gender = Gender.F,
                external = externalClubName != null,
                externalClubName = externalClubName,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    /**
     * Eine Meldung, so knapp wie `participant_for_event` es zulässt. [registeringClub] ist bewusst
     * getrennt von den Vereinen der Personen: der meldende Verein ist für die Anzeige
     * bedeutungslos und darf deshalb auch nicht auf der Pflegeseite auftauchen.
     */
    private fun TestComprehensionScope<JEnv>.registration(
        registeringClub: UUID,
        participants: List<UUID>,
    ): UUID {
        val eventId = UUID.randomUUID()
        val eventRegistrationId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val competitionRegistrationId = UUID.randomUUID()
        val namedParticipantId = UUID.randomUUID()

        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = eventRegistrationId,
                event = eventId,
                club = registeringClub,
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION.insert(CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now))
        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = competitionRegistrationId,
                eventRegistration = eventRegistrationId,
                competition = competitionId,
                club = registeringClub,
                createdAt = now,
                updatedAt = now,
            )
        )
        !NAMED_PARTICIPANT.insert(
            NamedParticipantRecord(id = namedParticipantId, name = "Ruderin", createdAt = now, updatedAt = now)
        )
        participants.forEach { participantId ->
            !COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(
                CompetitionRegistrationNamedParticipantRecord(
                    competitionRegistration = competitionRegistrationId,
                    namedParticipant = namedParticipantId,
                    participant = participantId,
                )
            )
        }

        return eventId
    }

    /**
     * Der Vereins-Datensatz und der Freitext an der Gastruderin sind derselbe Verein, verschieden
     * geschrieben. Sie ergeben eine Zeile - und beide Schreibweisen stehen darin, damit auf der
     * Seite sichtbar ist, was zusammengefasst wurde.
     */
    @Test
    fun aClubRecordAndAGuestsFreeTextMeetInOneRow() = testComprehension {
        val clubId = club(rostock)
        participant(clubId, "Gast", externalClubName = rostockLong)

        val rows = (!ClubShortNameService.list(null)).data

        val row = rows.singleOrNull()
        assertNotNull(row, "Beide Schreibweisen gehören in eine Zeile, bekommen habe ich: $rows")
        assertEquals(ClubNameKey.of(rostock), row.nameKey)
        // Kürzeste Schreibweise vorn - sie führt die Zeile an, die andere steht als "auch:".
        assertEquals(listOf(rostock, rostockLong), row.names)
        assertEquals("Rostocker RC", row.shortName)
        assertEquals(false, row.maintained, "Ohne gepflegten Eintrag ist die Kurzform automatisch")
    }

    /** Der Zweck der Tabelle: was gepflegt ist, schlägt die Heuristik. */
    @Test
    fun aMaintainedShortNameBeatsTheHeuristic() = testComprehension {
        val clubId = club(rostock)
        participant(clubId, "Gast", externalClubName = rostockLong)

        !ClubShortNameService.set(
            nameKey = ClubNameKey.of(rostock),
            request = ClubShortNameRequest(shortName = "RRC 1885", sampleName = rostockLong),
            userId = adminId(),
        )

        val row = (!ClubShortNameService.list(null)).data.single()
        assertEquals("RRC 1885", row.shortName)
        assertTrue(row.maintained)
        // Die Zeile bleibt eine: gepflegt wird der Schlüssel, nicht die einzelne Schreibweise.
        assertEquals(listOf(rostock, rostockLong), row.names)
    }

    /** Leeren heißt "zurück zur Heuristik", nicht "keine Kurzform" - und nicht "Zeile weg". */
    @Test
    fun deletingLeavesTheHeuristicInCharge() = testComprehension {
        val clubId = club(rostock)
        participant(clubId, "Gast", externalClubName = rostockLong)

        !ClubShortNameService.set(
            nameKey = ClubNameKey.of(rostock),
            request = ClubShortNameRequest(shortName = "RRC 1885", sampleName = rostockLong),
            userId = adminId(),
        )
        !ClubShortNameService.remove(ClubNameKey.of(rostock))

        val row = (!ClubShortNameService.list(null)).data.single()
        assertEquals("Rostocker RC", row.shortName)
        assertEquals(false, row.maintained)
    }

    /**
     * Der Filter zeigt die Vereine, die die Athleten *tragen* - nicht den, der gemeldet hat, und
     * nicht den Bestand mehrerer Jahre.
     */
    @Test
    fun theEventFilterKeepsTheClubsTheAthletesWear() = testComprehension {
        val rostockId = club(rostock)
        val registeringClubId = club("Pirnaer Ruderverein")
        club("Kölner Ruderverein von 1877")

        val eventId = registration(
            registeringClub = registeringClubId,
            participants = listOf(
                participant(rostockId, "Eigen"),
                participant(rostockId, "Gast", externalClubName = "Marburger Ruderverein"),
            ),
        )

        val all = (!ClubShortNameService.list(null)).data.map { it.names.first() }
        assertEquals(
            listOf("Kölner Ruderverein von 1877", "Marburger Ruderverein", "Pirnaer Ruderverein", rostock),
            all,
        )

        val atTheStart = (!ClubShortNameService.list(eventId)).data.map { it.names.first() }
        assertEquals(listOf("Marburger Ruderverein", rostock), atTheStart)
    }
}
