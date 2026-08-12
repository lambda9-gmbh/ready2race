package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.MAINZ
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.club.seedCrewMember
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.OpenRequirementExport
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.ParticipantRequirementService
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Spalte "E-Mail Meldender" am echten Postgres: der Export zieht die Adresse aus
 * `event_registration.created_by`, nicht aus den Athletendaten - die haben meist gar keine.
 *
 * Die Mannschaft aus [seedClubChain] ist absichtlich der unbequeme Fall: gemeldet hat der Kieler
 * RC, die Crew rudert für fünf andere Vereine, zwei davon nur als Freitext
 * (`external_club_name`). Im View `participant_for_event` ist `club_id` der Verein der Meldung -
 * alle sieben müssen deshalb die Adresse des Kieler Meldenden tragen, egal was in der Spalte
 * "Verein" steht. Leer bleibt die Zelle nur, wenn der Ersteller der Meldung gelöscht ist
 * (`created_by` steht per `on delete set null` auf NULL).
 */
class OpenRequirementRegistrantEmailTest {

    private val registrantEmail = "meldung@example.org"

    /** Alle Datenzeilen der Mappe, Nachname → Zelleninhalt der Spalte "E-Mail Meldender". */
    private fun registrantCellsByLastname(bytes: ByteArray): Map<String, String> =
        XSSFWorkbook(bytes.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val header = sheet.getRow(0)
            val col = (0 until header.lastCellNum)
                .first { header.getCell(it)?.stringCellValue == OpenRequirementExport.COLUMN_REGISTRANT_EMAIL }
            (1..sheet.lastRowNum).associate { r ->
                val row = sheet.getRow(r)
                row.getCell(1).stringCellValue to (row.getCell(col)?.stringCellValue ?: "")
            }
        }

    @Test
    fun theExportNamesTheRegistrantAndLeavesDeletedRegistrantsEmpty() = testComprehension {
        val seeded = seedClubChain()

        // Der Meldende: ein App-User, der die Meldung des Kieler RC abgegeben hat.
        val userId = UUID.randomUUID()
        !APP_USER.insert(
            AppUserRecord(
                id = userId,
                email = registrantEmail,
                password = "x",
                firstname = "Melde",
                lastname = "Mensch",
                language = "de",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !Jooq.query {
            update(EVENT_REGISTRATION)
                .set(EVENT_REGISTRATION.CREATED_BY, userId)
                .where(EVENT_REGISTRATION.EVENT.eq(seeded.eventId))
                .execute()
        }

        // Eine zweite Meldung, deren Ersteller gelöscht ist: Mainz meldet einen achten Ruderer,
        // `created_by` bleibt NULL - wie nach einem `on delete set null`.
        val mainzId = !Jooq.query {
            selectFrom(CLUB).where(CLUB.NAME.eq(MAINZ)).fetchSingle().id
        }
        val mainzEventRegistrationId = UUID.randomUUID()
        val mainzRegistrationId = UUID.randomUUID()
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = mainzEventRegistrationId,
                event = seeded.eventId,
                club = mainzId,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = mainzRegistrationId,
                eventRegistration = mainzEventRegistrationId,
                competition = seeded.competitionId,
                club = mainzId,
                name = "Mainz solo",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        seedCrewMember(mainzRegistrationId, "8. Ruderer", "Petersen", clubId = mainzId)

        // Eine Bedingung ohne Rollenbezug: damit hat jede gemeldete Person sie offen und der
        // Export enthält alle acht.
        val requirementId = UUID.randomUUID()
        !PARTICIPANT_REQUIREMENT.insert(
            ParticipantRequirementRecord(
                id = requirementId,
                name = "Aktivenpass",
                optional = false,
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        !EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(
            EventHasParticipantRequirementRecord(
                event = seeded.eventId,
                participantRequirement = requirementId,
                createdAt = CHAIN_SEED_TIME,
            )
        )

        val file = !ParticipantRequirementService.exportOpenRequirements(seeded.eventId, null)
        val cells = registrantCellsByLastname(file.bytes)

        assertEquals(8, cells.size, "alle Gemeldeten haben die Bedingung offen")

        // Die komplette Kieler Crew trägt die Adresse des Meldenden - ausdrücklich auch die
        // Gaststarter (Bruns, Evers, Groth), deren Spalte "Verein" den Freitext zeigt.
        seeded.crew.forEach { member ->
            assertEquals(registrantEmail, cells[member.lastName], member.lastName)
        }

        // Gelöschter Meldender (`created_by` NULL) ⇒ leere Zelle statt fremder Adresse.
        assertEquals("", cells["Petersen"], "Mainz, created_by NULL")
    }
}
