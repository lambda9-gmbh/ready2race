package de.lambda9.ready2race.backend.app.event

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeDto
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeSeverity
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.insert
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
import kotlin.test.assertNull

/**
 * Die HTTP-Leitung zu PUT /event/{eventId}/notice: Einhängepunkt, Berechtigung und die
 * 422-Antworten der Validierung. Was der Service dahinter rechnet, steht in
 * [EventNoticeServiceTest]; die Einbettung in die gepollten Antworten in
 * `EventNoticeInPublicViewsTest`.
 *
 * Läuft als `IT` nicht in der normalen Suite mit (Surefire nimmt nur `*Test`), weil
 * `testApplicationComprehension` die Datenbank zurücksetzt. Gezielt starten:
 *
 * ```
 * ./mvnw test -Dtest=EventNoticeHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class EventNoticeHttpIT {

    private val seedTime: LocalDateTime = LocalDateTime.of(2026, 8, 11, 17, 0)

    @Test
    fun settingTheNoticeNeedsASession() = testApplicationComprehension {
        val eventId = seedEvent()

        val anonymous = client.put("/api/event/$eventId/notice") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Sturmwarnung","severity":"WARNING"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status, anonymous.bodyAsText())

        val response = client.put("/api/event/$eventId/notice") {
            header("X-Api-Session", login())
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Sturmwarnung","severity":"WARNING"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())

        assertEquals(
            EventNoticeDto("Sturmwarnung", EventNoticeSeverity.WARNING),
            !EventRepo.getNotice(eventId),
        )
    }

    @Test
    fun clearingWithBothNullRemovesTheNotice() = testApplicationComprehension {
        val eventId = seedEvent()
        val session = login()

        client.put("/api/event/$eventId/notice") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Sturmwarnung","severity":"WARNING"}""")
        }

        val response = client.put("/api/event/$eventId/notice") {
            header("X-Api-Session", session)
            contentType(ContentType.Application.Json)
            setBody("""{"text":null,"severity":null}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
        assertNull(!EventRepo.getNotice(eventId))
    }

    /** Die Stufe kommt als String und wird gegen das Enum geprüft - deshalb 422, nicht 400. */
    @Test
    fun anUnknownSeverityAnswers422() = testApplicationComprehension {
        val eventId = seedEvent()

        val response = client.put("/api/event/$eventId/notice") {
            header("X-Api-Session", login())
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Sturmwarnung","severity":"BANANE"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, response.bodyAsText())
    }

    @Test
    fun aBlankTextAnswers422() = testApplicationComprehension {
        val eventId = seedEvent()

        val response = client.put("/api/event/$eventId/notice") {
            header("X-Api-Session", login())
            contentType(ContentType.Application.Json)
            setBody("""{"text":"   ","severity":"INFO"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, response.bodyAsText())
    }

    /** admin/admin ist das Fixture aus testing.kt, nicht ein echtes Konto. */
    private suspend fun TestApplicationComprehensionScope<JEnv>.login(): String {
        val login = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status, "Anmeldung als Fixture-Admin")

        val session = login.headers["X-Api-Session"]
        assertNotNull(session, "Login muss eine Sitzung ausgeben")
        return session
    }

    private fun TestApplicationComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        return eventId
    }
}
