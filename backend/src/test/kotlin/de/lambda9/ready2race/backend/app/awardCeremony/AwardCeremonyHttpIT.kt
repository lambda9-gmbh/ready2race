package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.TestApplicationComprehensionScope
import de.lambda9.ready2race.testing.testApplicationComprehension
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Leitung zum Siegerehrungsbogen. [AwardCeremonyServiceTest] belegt, was der Service rechnet -
 * dass ihn überhaupt jemand erreicht, und nur der Richtige, steht dort nirgends. Dazwischen liegen
 * der Einhängepunkt in `event.kt`, `authenticate(Privilege.ReadEventGlobal)` und der Pfad selbst;
 * keiner dieser drei bricht die Übersetzung, wenn er falsch ist.
 *
 * Der 401 ohne Sitzung trägt hier zwei Fehlerklassen auf einmal: ein vergessenes `!` an
 * `authenticate` machte den Endpoint für jeden offen (dann 200), ein nicht eingehängter
 * `awardCeremony()`-Aufruf oder ein Pfad-Tippfehler ergäbe 404.
 *
 * Läuft als `IT` nicht in der normalen Suite mit (kein Failsafe im POM, Surefire nimmt nur
 * `*Test`), weil `testApplicationComprehension` die Datenbank zurücksetzt und entsprechend
 * teuer ist. Gezielt starten:
 *
 * ```
 * ./mvnw test -Dtest=AwardCeremonyHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class AwardCeremonyHttpIT {

    private val seedTime: LocalDateTime = LocalDateTime.of(2026, 8, 15, 9, 0)

    @Test
    fun listingCeremoniesNeedsASession() = testApplicationComprehension {

        // Die Veranstaltung muss es geben: sonst antwortete schon der Service mit 404, und der
        // angemeldete Aufruf könnte einen nicht eingehängten Endpoint nicht mehr von einer
        // unbekannten Veranstaltung unterscheiden.
        val eventId = seedEvent()

        val anonymous = client.get("/api/event/$eventId/awardCeremony")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status, anonymous.bodyAsText())

        val response = client.get("/api/event/$eventId/awardCeremony") {
            header("X-Api-Session", login())
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        // Der Rumpf ist ApiResponse.ListDto, also ein nacktes Array. Dass es leer ist, ist in
        // Ordnung - die Veranstaltung hat keine Wettkämpfe, und der Inhalt steht in
        // AwardCeremonyServiceTest. Geprüft wird die Form, damit ein Endpoint, der plötzlich ein
        // Objekt liefert, hier auffällt.
        assertTrue(
            response.bodyAsText().trim().startsWith("["),
            "Erwartet wurde ein JSON-Array: ${response.bodyAsText()}",
        )
    }

    @Test
    fun downloadingTheSheetNeedsASessionToo() = testApplicationComprehension {

        val eventId = seedEvent()
        val body = """{"selection":null}"""

        val anonymous = client.post("/api/event/$eventId/awardCeremony/pdf") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status, anonymous.bodyAsText())

        val response = client.post("/api/event/$eventId/awardCeremony/pdf") {
            header("X-Api-Session", login())
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        // Kein Wunschwert, sondern der fachlich richtige für diese Veranstaltung: sie hat keine
        // platzierten Boote, also lehnt der Service mit AwardCeremonyError.NoResults ab. Genau
        // dieser Fehlercode ist der Beleg - er entsteht erst hinter `receiveKIO`, also nur, wenn
        // die Route erreichbar war, die Berechtigung getragen und der Rumpf sich lesen ließ.
        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertContains(response.bodyAsText(), "AWARD_CEREMONY_NO_RESULTS")
    }

    /** admin/admin ist das Fixture aus testing.kt, nicht ein echtes Konto. */
    private suspend fun TestApplicationComprehensionScope<JEnv>.login(): String {
        val login = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status, "Anmeldung als Fixture-Admin")

        // Die Sitzung reist im Header, nicht im Cookie (Sessions.kt: header<UserSession>).
        val session = login.headers["X-Api-Session"]
        assertNotNull(session, "Login muss eine Sitzung ausgeben")
        return session
    }

    /** Die kleinste Veranstaltung, die der Service durchlässt: ein Tag, keine Wettkämpfe. */
    private fun TestApplicationComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                location = "Kiel",
                challengeEvent = false,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        !EVENT_DAY.insert(
            EventDayRecord(
                id = UUID.randomUUID(),
                event = eventId,
                date = seedTime.toLocalDate(),
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        return eventId
    }
}
