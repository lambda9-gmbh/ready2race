package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.app.liveDashboard.control.MatchTeamNoteRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardError
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardTeamDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.MatchTeamNoteRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.MatchTeamNoteRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Schiedsrichter-Notizen je Boot, am echten Postgres: Anlegen, Löschen und - der eigentliche
 * Zweck - die Einbettung in den Dashboard-Poll. Die Notizen hängen an der Boots-Zeile
 * (`competition_match_team.id`), der Poll führt seine Boote aber unter (Lauf, Meldung) - genau
 * diese Übersetzung in [MatchTeamNoteRepo] ist das, was nur ein Datenbanktest belegen kann.
 *
 * Die Rechte an den Endpunkten prüft [LiveDashboardTeamNoteHttpIT]; hier geht es um die
 * Service-Schicht darunter.
 */
class LiveDashboardTeamNoteTest {

    @Test
    fun aNoteAppearsInThePollWithItsAuthorAndDisappearsAfterDeletion() = testComprehension {
        val seeded = seedClubChain()
        val userId = seedAuthor("Anna", "Ausrichter")

        !LiveDashboardService.createTeamNote(
            seeded.eventId,
            seeded.matchId,
            seeded.registrationId,
            userId,
            MatchTeamNoteRequest(note = "Boje berührt"),
        )

        val team = boardTeam(seeded)
        assertEquals(1, team.notes.size)
        val note = team.notes.single()
        assertEquals("Boje berührt", note.note)
        assertEquals("Anna Ausrichter", note.author)
        assertNotNull(note.createdAt)

        !LiveDashboardService.deleteTeamNote(
            seeded.eventId,
            seeded.matchId,
            seeded.registrationId,
            note.id,
        )

        assertEquals(emptyList(), boardTeam(seeded).notes)
    }

    /**
     * Append-only heißt: zwei Autoren gleichzeitig ergeben zwei Einträge, und die Liste steht in
     * Schreibreihenfolge (älteste zuerst) - der Dialog liest sie wie einen Gesprächsverlauf.
     */
    @Test
    fun twoAuthorsProduceTwoEntriesOldestFirst() = testComprehension {
        val seeded = seedClubChain()
        val anna = seedAuthor("Anna", "Ausrichter")
        val bernd = seedAuthor("Bernd", "Beobachter")

        !LiveDashboardService.createTeamNote(
            seeded.eventId, seeded.matchId, seeded.registrationId, anna,
            MatchTeamNoteRequest(note = "Boje berührt"),
        )
        !LiveDashboardService.createTeamNote(
            seeded.eventId, seeded.matchId, seeded.registrationId, bernd,
            MatchTeamNoteRequest(note = "aus meiner Sicht nicht"),
        )

        val notes = boardTeam(seeded).notes
        assertEquals(listOf("Boje berührt", "aus meiner Sicht nicht"), notes.map { it.note })
        assertEquals(listOf("Anna Ausrichter", "Bernd Beobachter"), notes.map { it.author })
        assertTrue(notes[0].createdAt <= notes[1].createdAt)
    }

    /**
     * `created_by` steht auf `on delete set null`: eine Notiz überlebt das Konto ihrer Autorin,
     * nur der Name fehlt dann. Der Repo-Join darf daraus keinen halben Namen bauen.
     */
    @Test
    fun aNoteWithoutAnAccountKeepsItsTextAndLosesOnlyTheAuthor() = testComprehension {
        val seeded = seedClubChain()
        val teamRowId = assertNotNull(
            !MatchTeamNoteRepo.findTeamRowId(seeded.eventId, seeded.matchId, seeded.registrationId)
        )

        !MatchTeamNoteRepo.create(
            MatchTeamNoteRecord(
                id = UUID.randomUUID(),
                competitionMatchTeam = teamRowId,
                note = "Boje berührt",
                createdAt = CHAIN_SEED_TIME,
                createdBy = null,
            )
        )

        val note = boardTeam(seeded).notes.single()
        assertEquals("Boje berührt", note.note)
        assertNull(note.author)
    }

    @Test
    fun writingToAForeignTeamOrDeletingAForeignNoteIsRefused() = testComprehension {
        val seeded = seedClubChain()
        val userId = seedAuthor("Anna", "Ausrichter")
        val foreignTeam = UUID.randomUUID()

        // Eine Meldung, die es in diesem Lauf nicht gibt, bekommt keine Notiz ...
        assertKIOFails(LiveDashboardError.TeamNotFound(foreignTeam)) {
            LiveDashboardService.createTeamNote(
                seeded.eventId, seeded.matchId, foreignTeam, userId,
                MatchTeamNoteRequest(note = "Boje berührt"),
            )
        }

        // ... und eine erratene Notiz-Kennung löscht nichts.
        val unknownNote = UUID.randomUUID()
        assertKIOFails(LiveDashboardError.NoteNotFound(unknownNote)) {
            LiveDashboardService.deleteTeamNote(
                seeded.eventId, seeded.matchId, seeded.registrationId, unknownNote,
            )
        }
    }

    /**
     * Der Team-Pfad prüft bis zur Veranstaltung hoch: unter einer fremden Event-Kennung existiert
     * das Boot nicht - eine Notiz lässt sich nicht über die falsche Veranstaltung anlegen.
     */
    @Test
    fun theEventInThePathMustMatchTheMatch() = testComprehension {
        val seeded = seedClubChain()
        val userId = seedAuthor("Anna", "Ausrichter")

        // Eine zweite, leere Veranstaltung - der Lauf aus [seeded] gehört ihr nicht.
        val otherEventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = otherEventId,
                name = "Fremde Regatta",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        assertKIOFails(LiveDashboardError.TeamNotFound(seeded.registrationId)) {
            LiveDashboardService.createTeamNote(
                otherEventId, seeded.matchId, seeded.registrationId, userId,
                MatchTeamNoteRequest(note = "Boje berührt"),
            )
        }
    }

    private fun TestComprehensionScope<JEnv>.boardTeam(seeded: SeededClubChain): LiveDashboardTeamDto {
        val dashboard = (!LiveDashboardService.getLiveDashboard(seeded.eventId, LiveDashboardScope.ALL)).dto
        return dashboard.matches.single().teams.single()
    }

    private fun TestComprehensionScope<JEnv>.seedAuthor(firstname: String, lastname: String): UUID {
        val id = UUID.randomUUID()
        !APP_USER.insert(
            AppUserRecord(
                id = id,
                email = "$firstname.$lastname-$id@example.org".lowercase(),
                password = "x",
                firstname = firstname,
                lastname = lastname,
                language = "de",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return id
    }
}
