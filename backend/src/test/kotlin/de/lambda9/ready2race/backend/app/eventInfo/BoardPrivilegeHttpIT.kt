package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleHasPrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.testing.TestApplicationComprehensionScope
import de.lambda9.ready2race.testing.testApplicationComprehension
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Rechte an der Board-Verwaltung. Seit dem 14.08.2026 trägt sie ein eigenes Paar
 * (READ/UPDATE BOARD), damit eine Sprecher- oder Streamer-Rolle die Anzeigen pflegen kann, ohne
 * das breite UPDATE EVENT zu bekommen. Zwei Aussagen hängen daran, und beide kann nur ein Test
 * über die Route treffen - `authenticateAny` entscheidet dort, nicht der Service:
 *
 *  - Das neue Recht **reicht** für Anlegen, Ändern und Löschen.
 *  - Es reicht **nicht weiter**: dieselbe Sitzung darf die Veranstaltung nicht bearbeiten.
 *
 * Dazu die Gegenprobe für den Bestand: eine Rolle mit den alten Event-Rechten und ohne jedes
 * Board-Recht speichert weiterhin - sonst hätte die Umstellung bestehende Rollen entwertet.
 *
 * Läuft als `IT` nicht in der normalen Suite mit (Surefire nimmt nur `*Test`), weil
 * `testApplicationComprehension` die Datenbank zurücksetzt. Gezielt starten:
 *
 * ```
 * ./mvnw -o test -Dtest=BoardPrivilegeHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class BoardPrivilegeHttpIT {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    private val boardBody = """
        {
          "name": "Sprecher-Anzeige",
          "config": {
            "columns": 3,
            "tiles": [
              {"elements": [{"type": "MATCH", "offset": 0}]}
            ]
          }
        }
    """.trimIndent()

    /**
     * Ein Konto mit genau den übergebenen Privilegien - über eine eigene Rolle, so wie die
     * Rechtevergabe in der Oberfläche auch läuft. Die Privileg-Zeilen selbst legt
     * `initializeDatabase` beim Start an; hier werden sie nur der Rolle zugeordnet.
     */
    private fun TestApplicationComprehensionScope<JEnv>.seedUser(
        password: String,
        privileges: List<Privilege>,
    ): String {
        val roleId = UUID.randomUUID()
        !RoleRepo.create(
            RoleRecord(
                id = roleId,
                name = "Testrolle $roleId",
                description = null,
                static = false,
                createdAt = now,
                createdBy = SYSTEM_USER,
                updatedAt = now,
                updatedBy = SYSTEM_USER,
            )
        )

        val all = !PrivilegeRepo.all()
        val wanted = all.filter { record ->
            privileges.any {
                it.action.name == record.action &&
                    it.resource.name == record.resource &&
                    it.scope.name == record.scope
            }
        }
        assertEquals(privileges.size, wanted.size, "Alle gewünschten Privilegien müssen existieren")

        !RoleHasPrivilegeRepo.create(
            wanted.map { RoleHasPrivilegeRecord(role = roleId, privilege = it.id) }
        )

        val userId = UUID.randomUUID()
        val email = "board-$userId@example.org"
        !AppUserRepo.create(
            AppUserRecord(
                id = userId,
                email = email,
                firstname = "Sprecherin",
                lastname = "Test",
                password = !PasswordUtilities.hash(password),
                language = EmailLanguage.DE.name,
                createdAt = now,
                createdBy = SYSTEM_USER,
                updatedAt = now,
                updatedBy = SYSTEM_USER,
            )
        )
        // USER_ROLE dazu, weil ein echtes Konto sie immer trägt (Eigenprofil lesen/ändern).
        !AppUserHasRoleRepo.create(AppUserHasRoleRecord(appUser = userId, role = USER_ROLE))
        !AppUserHasRoleRepo.create(AppUserHasRoleRecord(appUser = userId, role = roleId))

        return email
    }

    private suspend fun TestApplicationComprehensionScope<JEnv>.login(
        email: String,
        password: String,
    ): String {
        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, "Anmeldung als $email")

        return assertNotNull(response.headers["X-Api-Session"], "Login muss eine Sitzung ausgeben")
    }

    private fun boardsBase(seeded: SeededClubChain): String = "/api/event/${seeded.eventId}/boards"

    /** Legt ein Board an und gibt dessen Kennung zurück - der Aufruf muss durchkommen. */
    private suspend fun TestApplicationComprehensionScope<JEnv>.createBoard(
        seeded: SeededClubChain,
        session: String,
    ): String {
        val response = client.post(boardsBase(seeded)) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(boardBody)
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        // Die Antwort ist das angelegte Board (ApiResponse.Dto), die Kennung steht im JSON.
        val body = response.bodyAsText()
        return assertNotNull(
            Regex("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").find(body)?.groupValues?.get(1),
            "Antwort muss die Kennung des Boards tragen: $body",
        )
    }

    @Test
    fun theBoardPrivilegeAloneCarriesTheWholeAdminMask() = testApplicationComprehension {
        val seeded = seedClubChain()
        val password = "einPasswortFuerDenTest"
        val session = login(
            seedUser(password, listOf(Privilege.ReadBoardGlobal, Privilege.UpdateBoardGlobal)),
            password,
        )

        val list = client.get(boardsBase(seeded)) { header("X-Api-Session", session) }
        assertEquals(HttpStatusCode.OK, list.status, list.bodyAsText())

        val boardId = createBoard(seeded, session)

        val update = client.put("${boardsBase(seeded)}/$boardId") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(boardBody.replace("Sprecher-Anzeige", "Sprecher-Anzeige 2"))
        }
        assertEquals(HttpStatusCode.NoContent, update.status, update.bodyAsText())

        val delete = client.delete("${boardsBase(seeded)}/$boardId") {
            header("X-Api-Session", session)
        }
        assertEquals(HttpStatusCode.NoContent, delete.status, delete.bodyAsText())
    }

    /**
     * Der Kern der Trennung: Wer Boards pflegen darf, darf deshalb noch lange nicht die
     * Veranstaltung ändern. Ginge das durch, wäre das neue Recht nur ein zweiter Name für
     * UPDATE EVENT und die ganze Umstellung sinnlos.
     */
    @Test
    fun theBoardPrivilegeDoesNotUnlockTheEventItself() = testApplicationComprehension {
        val seeded = seedClubChain()
        val password = "einPasswortFuerDenTest"
        val session = login(
            seedUser(password, listOf(Privilege.ReadBoardGlobal, Privilege.UpdateBoardGlobal)),
            password,
        )

        val response = client.put("/api/event/${seeded.eventId}") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Umbenannt","description":null,"location":null}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
    }

    /**
     * Die Gegenprobe für den Bestand: die alten Event-Rechte, kein Board-Recht - und die
     * Verwaltungsmaske funktioniert unverändert.
     */
    @Test
    fun theOldEventPrivilegeStillSaves() = testApplicationComprehension {
        val seeded = seedClubChain()
        val password = "einPasswortFuerDenTest"
        val session = login(
            seedUser(password, listOf(Privilege.ReadEventGlobal, Privilege.UpdateEventGlobal)),
            password,
        )

        val list = client.get(boardsBase(seeded)) { header("X-Api-Session", session) }
        assertEquals(HttpStatusCode.OK, list.status, list.bodyAsText())

        createBoard(seeded, session)
    }

    /** Ein Konto ohne beide Rechte kommt an keinen der beiden Wege heran. */
    @Test
    fun anAccountWithoutAnyOfTheTwoIsRefused() = testApplicationComprehension {
        val seeded = seedClubChain()
        val password = "einPasswortFuerDenTest"
        val session = login(seedUser(password, emptyList()), password)

        val list = client.get(boardsBase(seeded)) { header("X-Api-Session", session) }
        assertEquals(HttpStatusCode.Forbidden, list.status, list.bodyAsText())

        val create = client.post(boardsBase(seeded)) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(boardBody)
        }
        assertEquals(HttpStatusCode.Forbidden, create.status, create.bodyAsText())
    }

    /**
     * Die Anzeige selbst bleibt öffentlich - der montierte Bildschirm meldet sich nie an. Das
     * neue Recht darf daran nichts geändert haben.
     */
    @Test
    fun theBoardItselfStaysPublic() = testApplicationComprehension {
        val seeded = seedClubChain()
        val session = login("admin", "admin")
        val boardId = createBoard(seeded, session)

        // Die Anzeige hängt unter dem öffentlichen /info-Zweig, nicht unter der Verwaltung.
        val response = client.get("/api/event/${seeded.eventId}/info/board/$boardId")

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        // Die Anzeige trägt keinen Namen, sondern die Kennung des Boards und seine Konfiguration.
        assertTrue(response.bodyAsText().contains(boardId), response.bodyAsText())
    }
}
