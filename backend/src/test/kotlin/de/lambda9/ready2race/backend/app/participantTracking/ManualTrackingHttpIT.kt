package de.lambda9.ready2race.backend.app.participantTracking

import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.testing.TestApplicationComprehensionScope
import de.lambda9.ready2race.testing.testApplicationComprehension
import de.lambda9.ready2race.backend.app.JEnv
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Die Rechte am manuellen Check-in - die eine Aussage, die kein Service-Test treffen kann: ob ein
 * Aufruf durchkommt, entscheidet `authenticateAny` in der Route, und zwischen Fehlerwert und
 * Statuscode liegt noch `respondComprehension`.
 *
 * Geprüft wird ausdrücklich auch der **lesende** Aufruf. In der Änderungsspur stehen Begründungen
 * im Klartext; ein GET, das jeder Angemeldete abrufen kann, wäre genau das Leck, das die
 * Anforderung ausschliesst.
 *
 * Laeuft als `IT` nicht in der normalen Suite mit (kein Failsafe im POM, Surefire nimmt nur
 * `*Test`), weil `testApplicationComprehension` die Datenbank zurücksetzt. Gezielt starten:
 *
 * ```
 * ./mvnw -o test -Dtest=ManualTrackingHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class ManualTrackingHttpIT {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 9, 0)

    /**
     * Nicht `body` nennen: innerhalb von `client.post { ... }` ist der Empfaenger ein
     * HttpRequestBuilder, der selbst ein `body` hat - `setBody(validBody)` haette dessen leeren
     * Standardwert genommen und den Aufruf ohne Rumpf abgeschickt.
     */
    private val validBody = """{"scanType":"ENTRY","scannedAt":"2026-08-14T09:30:00","reason":"Boot ohne Scan abgelegt"}"""

    /** Verein, Person, Veranstaltung - mehr berührt der manuelle Eintrag nicht. */
    private fun TestApplicationComprehensionScope<JEnv>.seedParticipant(): Pair<UUID, UUID> {
        val clubId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        !CLUB.insert(
            ClubRecord(id = clubId, name = "Testverein $clubId", createdAt = now, updatedAt = now)
        )
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = participantId,
                club = clubId,
                firstname = "Test",
                lastname = "Ruderin",
                year = 1990,
                gender = Gender.F,
                createdAt = now,
                updatedAt = now,
            )
        )
        !EVENT.insert(
            EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now)
        )

        return participantId to eventId
    }

    /**
     * Ein gewöhnliches Konto: USER_ROLE trägt nur ReadUserOwn und UpdateUserOwn (siehe
     * initializeDatabase) - also weder UpdateLiveDashboardGlobal noch UpdateEventGlobal.
     */
    private fun TestApplicationComprehensionScope<JEnv>.seedPlainUser(password: String): String {
        val userId = UUID.randomUUID()
        val email = "helfer-$userId@example.org"

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

    @Test
    fun anAccountWithoutTheTwoPrivilegesIsRefusedOnAllThreeCalls() = testApplicationComprehension {
        val (participantId, eventId) = seedParticipant()
        val password = "einPasswortFuerDenTest"
        val email = seedPlainUser(password)
        val session = login(email, password)

        val base = "/api/event/$eventId/participant/$participantId/tracking"

        val read = client.get(base) { header("X-Api-Session", session) }
        assertEquals(HttpStatusCode.Forbidden, read.status, read.bodyAsText())

        val create = client.post(base) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.Forbidden, create.status, create.bodyAsText())

        val correct = client.put("$base/${UUID.randomUUID()}") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.Forbidden, correct.status, correct.bodyAsText())
    }

    /** Ohne Anmeldung gibt es die Spur ebenfalls nicht zu sehen. */
    @Test
    fun anUnauthenticatedReadIsRefused() = testApplicationComprehension {
        val (participantId, eventId) = seedParticipant()

        val response = client.get("/api/event/$eventId/participant/$participantId/tracking")

        assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
    }

    /**
     * Die Gegenprobe: derselbe Aufruf mit den nötigen Rechten kommt durch. Ohne sie bewiese der
     * 403 oben nur, dass der Pfad überhaupt nicht erreichbar ist.
     */
    @Test
    fun anAdminMayReadAndWrite() = testApplicationComprehension {
        val (participantId, eventId) = seedParticipant()

        // admin/admin ist kein echtes Konto, sondern das Fixture aus testing.kt.
        val session = login("admin", "admin")
        val base = "/api/event/$eventId/participant/$participantId/tracking"

        val read = client.get(base) { header("X-Api-Session", session) }
        assertEquals(HttpStatusCode.OK, read.status, read.bodyAsText())

        val create = client.post(base) {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.Created, create.status, create.bodyAsText())
    }

    /**
     * Die Pflichtbegründung reicht bis auf die Leitung: ein leerer Grund kommt nicht durch. 422,
     * nicht 400 - so beantwortet `respondComprehension` eine fehlgeschlagene Rumpfprüfung im
     * ganzen Projekt.
     */
    @Test
    fun anEmptyReasonIsRefused() = testApplicationComprehension {
        val (participantId, eventId) = seedParticipant()
        val session = login("admin", "admin")

        val response = client.post("/api/event/$eventId/participant/$participantId/tracking") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"scanType":"ENTRY","scannedAt":"2026-08-14T09:30:00","reason":"   "}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, response.bodyAsText())
    }
}
