package de.lambda9.ready2race.backend.app.appUserWithQrCode

import de.lambda9.ready2race.testing.testApplicationComprehension
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Schliesst die letzte Lücke im Beleg für den QR-404: [DeleteQrCodeTest] zeigt, dass der Service
 * fehlschlägt, sagt aber nichts darüber, was auf der Leitung ankommt. Zwischen beidem liegen
 * `respondComprehension` und `QrCodeError.respond()` - erst hier wird aus dem Fehlerwert ein
 * Statuscode.
 *
 * Laeuft als `IT` nicht in der normalen Suite mit (kein Failsafe im POM, Surefire nimmt nur
 * `*Test`), weil `testApplicationComprehension` die Datenbank zurücksetzt und entsprechend
 * teuer ist. Gezielt starten:
 *
 * ```
 * ./mvnw -o test -Dtest=DeleteQrCodeHttpIT -DfailIfNoSpecifiedTests=false
 * ```
 */
class DeleteQrCodeHttpIT {

    @Test
    fun removingAnUnknownBandAnswersNotFound() = testApplicationComprehension {

        // admin/admin ist kein echtes Konto, sondern das Fixture aus testing.kt, das
        // initializeDatabase in der Wegwerf-Datenbank des Testcontainers anlegt.
        val login = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status, "Anmeldung als Fixture-Admin")

        // Die Sitzung reist im Header, nicht im Cookie (Sessions.kt: header<UserSession>).
        val session = login.headers["X-Api-Session"]
        assertNotNull(session, "Login muss eine Sitzung ausgeben")

        // Die eventId prüft dieser Endpoint nicht, sie muss nur eine UUID sein - der Lösch-Aufruf
        // geht direkt gegen die qr_code-Tabelle.
        val response = client.delete(
            "/api/event/${UUID.randomUUID()}/appUserWithQrCode/qrCode/kein-bekanntes-baendchen"
        ) {
            header("X-Api-Session", session)
        }

        // Vor dem Fix stand hier 204: der Fehlerzweig war ein No-Op, darunter gewann ein
        // unbedingtes ok().
        assertEquals(HttpStatusCode.NotFound, response.status, response.bodyAsText())
    }
}
