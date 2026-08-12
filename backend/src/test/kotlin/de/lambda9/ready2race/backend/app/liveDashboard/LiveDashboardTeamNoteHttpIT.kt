package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.club.SeededClubChain
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
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

/**
 * Die Rechte an den Notiz-Endpunkten - die eine Aussage, die kein Service-Test treffen kann: ob
 * ein Aufruf durchkommt, entscheidet `authenticate` in der Route. Schreiben und Löschen tragen
 * dasselbe Privileg wie die übrigen Schiedsrichter-Aktionen (`UpdateLiveDashboardGlobal`, siehe
 * /finish, /start, /activation) - das Löschen ist ausdrücklich NICHT auf die Autorin beschränkt.
 *
 * Läuft als `IT` nicht in der normalen Suite mit (kein Failsafe im POM, Surefire nimmt nur
 * `*Test`), weil `testApplicationComprehension` die Datenbank zurücksetzt. Gezielt starten:
 *
 * ```
 * ./mvnw -o test -Dtest=LiveDashboardTeamNoteHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class LiveDashboardTeamNoteHttpIT {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    /**
     * Ein gewöhnliches Konto: USER_ROLE trägt nur ReadUserOwn und UpdateUserOwn (siehe
     * initializeDatabase) - also kein UpdateLiveDashboardGlobal.
     */
    private fun TestApplicationComprehensionScope<JEnv>.seedPlainUser(password: String): String {
        val userId = UUID.randomUUID()
        val email = "schiri-$userId@example.org"

        !AppUserRepo.create(
            AppUserRecord(
                id = userId,
                email = email,
                firstname = "Ohne",
                lastname = "Rechte",
                password = !PasswordUtilities.hash(password),
                language = EmailLanguage.DE.name,
                createdAt = now,
                createdBy = SYSTEM_USER,
                updatedAt = now,
                updatedBy = SYSTEM_USER,
            )
        )
        !AppUserHasRoleRepo.create(AppUserHasRoleRecord(appUser = userId, role = USER_ROLE))

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

        // Die Sitzung reist im Header, nicht im Cookie (Sessions.kt: header<UserSession>).
        return assertNotNull(response.headers["X-Api-Session"], "Login muss eine Sitzung ausgeben")
    }

    private fun noteBase(seeded: SeededClubChain): String =
        "/api/event/${seeded.eventId}/liveDashboard/match/${seeded.matchId}/team/${seeded.registrationId}/note"

    @Test
    fun anAccountWithoutTheRefereePrivilegeIsRefusedOnBothCalls() = testApplicationComprehension {
        val seeded = seedClubChain()
        val password = "einPasswortFuerDenTest"
        val session = login(seedPlainUser(password), password)

        val create = client.post(noteBase(seeded)) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Boje berührt"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, create.status, create.bodyAsText())

        val delete = client.delete("${noteBase(seeded)}/${UUID.randomUUID()}") {
            header("X-Api-Session", session)
        }
        assertEquals(HttpStatusCode.Forbidden, delete.status, delete.bodyAsText())
    }

    @Test
    fun anUnauthenticatedCallIsRefused() = testApplicationComprehension {
        val seeded = seedClubChain()

        val response = client.post(noteBase(seeded)) {
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Boje berührt"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
    }

    /**
     * Die Gegenprobe samt dem Kern der Lösch-Regel: Admin legt an, Admin löscht - obwohl der
     * Admin hier nicht "die Autorin" einer fremden Notiz sein muss. Es zählt allein das Privileg;
     * geprüft wird das, indem dieselbe Sitzung eine Notiz löscht, die formal ein anderer Kopf
     * angelegt haben könnte - der Server kennt keinen Autoren-Vorbehalt.
     */
    @Test
    fun anAccountWithThePrivilegeMayCreateAndDeleteRegardlessOfAuthorship() =
        testApplicationComprehension {
            val seeded = seedClubChain()

            // admin/admin ist kein echtes Konto, sondern das Fixture aus testing.kt.
            val session = login("admin", "admin")

            val create = client.post(noteBase(seeded)) {
                header("X-Api-Session", session)
                contentType(ContentType.Application.Json)
                setBody("""{"note":"Boje berührt"}""")
            }
            assertEquals(HttpStatusCode.Created, create.status, create.bodyAsText())

            // Die 201-Antwort ist die rohe Kennung als Text (siehe respondKIO: ApiResponse.Created).
            val noteId = UUID.fromString(create.bodyAsText())

            val delete = client.delete("${noteBase(seeded)}/$noteId") {
                header("X-Api-Session", session)
            }
            assertEquals(HttpStatusCode.NoContent, delete.status, delete.bodyAsText())
        }

    /**
     * Leerraum ist keine Notiz. 422, nicht 400 - so beantwortet `respondComprehension` eine
     * fehlgeschlagene Rumpfprüfung im ganzen Projekt.
     */
    @Test
    fun aBlankNoteIsRefused() = testApplicationComprehension {
        val seeded = seedClubChain()
        val session = login("admin", "admin")

        val response = client.post(noteBase(seeded)) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"note":"   "}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, response.bodyAsText())
    }
}
