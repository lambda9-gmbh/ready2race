package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.boundary.ClubService
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameService
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubNameRuleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB_NAME_RULE
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Das Feld "Kurzform" im Bearbeiten-Dialog eines Vereins.
 *
 * Es schreibt in dieselbe Ablage wie die Pflegeseite - `club_short_name` unter dem Schlüssel des
 * Vereins*namens* - und nicht in eine Spalte an `club`. Was diese Ebene prüft und keine reine
 * Funktion prüfen kann: dass beide Orte denselben Eintrag treffen, und was beim Umbenennen mit ihm
 * geschieht. Der Schlüssel wandert beim Umbenennen mit, und eine verwaiste Zeile zurückzulassen
 * wäre die schlechteste Variante.
 */
class ClubShortNameOnClubTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    private val rostock = "Rostocker Ruderclub"
    private val rostockLong = "Rostocker Ruder-Club von 1885 e.V."

    private fun TestComprehensionScope<JEnv>.adminId(): UUID =
        assertNotNull(
            !Jooq.query { select(APP_USER.ID).from(APP_USER).limit(1).fetchOne(APP_USER.ID) },
            "Ohne angelegten Benutzer lässt sich keine Kurzform pflegen",
        )

    private fun TestComprehensionScope<JEnv>.abbreviation(term: String, replacement: String) {
        !CLUB_NAME_RULE.insert(
            ClubNameRuleRecord(
                id = UUID.randomUUID(),
                kind = ClubNameRuleKind.ABBREVIATION.name,
                term = term,
                replacement = replacement,
                sortOrder = 100,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun TestComprehensionScope<JEnv>.club(name: String): UUID {
        val id = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = id, name = name, createdAt = now, updatedAt = now))
        return id
    }

    private fun TestComprehensionScope<JEnv>.guest(clubId: UUID, externalClubName: String) {
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = UUID.randomUUID(),
                club = clubId,
                firstname = "Test",
                lastname = "Gast",
                year = 1990,
                gender = Gender.F,
                external = true,
                externalClubName = externalClubName,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun TestComprehensionScope<JEnv>.stored(): Map<String, String> =
        (!ClubShortNameRepo.all()).associate { it.nameKey to it.shortName }

    /** Die Vorbelegung des Feldes: dieselbe automatische Kurzform, die auch in der Liste steht. */
    @Test
    fun theDialogIsPrefilledWithTheAutomaticShortName() = testComprehension {
        abbreviation("Ruderclub", "RC")
        club(rostock)

        val resolved = (!ClubShortNameService.forName(rostock)).dto
        assertEquals(ClubNameKey.of(rostock), resolved.nameKey)
        assertEquals("Rostocker RC", resolved.shortName)
        assertEquals(false, resolved.maintained)
    }

    /** Beide Orte, ein Eintrag: was im Dialog steht, steht danach in der Liste. */
    @Test
    fun theDialogFieldWritesIntoTheSameStoreAsTheList() = testComprehension {
        val clubId = club(rostock)
        // Dieselbe Mannschaft, an einer Gastruderin anders geschrieben - sie fällt auf denselben
        // Schlüssel und muss die Kurzform aus dem Dialog mitbekommen.
        guest(clubId, rostockLong)

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)

        val row = (!ClubShortNameService.list(null)).data.single()
        assertEquals("RRC 1885", row.shortName)
        assertTrue(row.maintained)
        assertEquals(listOf(rostock, rostockLong), row.names)
    }

    /** Leeren heißt "zurück zur Automatik" - auch aus dem Dialog heraus. */
    @Test
    fun clearingTheDialogFieldReturnsToTheAutomatic() = testComprehension {
        abbreviation("Ruderclub", "RC")
        val clubId = club(rostock)

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)
        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = ""), adminId(), clubId)

        val row = (!ClubShortNameService.list(null)).data.single()
        assertEquals("Rostocker RC", row.shortName)
        assertEquals(false, row.maintained)
    }

    /**
     * Ein unangetastetes Feld schickt gar nichts mit. Ohne diese Unterscheidung würde jedes
     * Speichern eines Vereins die Automatik als gepflegt festschreiben - und eine spätere
     * Verbesserung der Regeln käme nirgends mehr an.
     */
    @Test
    fun anUntouchedFieldChangesNothing() = testComprehension {
        val clubId = club(rostock)

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)
        !ClubService.updateClub(ClubUpsertDto(name = rostock), adminId(), clubId)

        assertEquals(mapOf(ClubNameKey.of(rostock) to "RRC 1885"), stored())
    }

    /**
     * Umbenennen ändert den Schlüssel. Entscheidung: die gepflegte Kurzform wandert mit, statt als
     * verwaiste Zeile unter dem alten Schlüssel liegenzubleiben.
     */
    @Test
    fun renamingTakesTheMaintainedShortNameAlong() = testComprehension {
        val clubId = club(rostock)

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)
        !ClubService.updateClub(ClubUpsertDto(name = "Warnemünder Ruderclub"), adminId(), clubId)

        assertEquals(mapOf(ClubNameKey.of("Warnemünder Ruderclub") to "RRC 1885"), stored())
        assertNull(stored()[ClubNameKey.of(rostock)])
    }

    /**
     * Die alte Zeile fällt nur, wenn die alte Schreibweise nirgends mehr vorkommt. Gastruderer
     * tragen ihren Verein als Freitext; der wird beim Umbenennen eines Vereins-Datensatzes nicht
     * mitgezogen und braucht seine Kurzform weiterhin.
     */
    @Test
    fun renamingKeepsTheOldEntryWhileAGuestStillWearsTheOldSpelling() = testComprehension {
        val clubId = club(rostock)
        guest(clubId, rostockLong)

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)
        !ClubService.updateClub(ClubUpsertDto(name = "Warnemünder Ruderclub"), adminId(), clubId)

        assertEquals(
            mapOf(
                ClubNameKey.of(rostock) to "RRC 1885",
                ClubNameKey.of("Warnemünder Ruderclub") to "RRC 1885",
            ),
            stored(),
        )
    }

    /**
     * Steht am neuen Namen schon eine gepflegte Kurzform, bleibt sie stehen - sie ist die jüngere
     * Aussage über diesen Namen als die des umbenannten Vereins.
     */
    @Test
    fun renamingDoesNotOverwriteAnAlreadyMaintainedTarget() = testComprehension {
        val clubId = club(rostock)
        val other = club("Warnemünder Ruderclub")

        !ClubService.updateClub(ClubUpsertDto(name = rostock, shortName = "RRC 1885"), adminId(), clubId)
        !ClubService.updateClub(
            ClubUpsertDto(name = "Warnemünder Ruderclub", shortName = "WRC"),
            adminId(),
            other,
        )
        // Anderer Name (Vereinsnamen sind eindeutig), derselbe Schlüssel wie der des anderen
        // Vereins - genau der Fall, in dem die wandernde Kurzform auf eine gepflegte trifft.
        !ClubService.updateClub(ClubUpsertDto(name = "Warnemünder Ruder-Club e.V."), adminId(), clubId)

        assertEquals("WRC", stored()[ClubNameKey.of("Warnemünder Ruderclub")])
    }
}
