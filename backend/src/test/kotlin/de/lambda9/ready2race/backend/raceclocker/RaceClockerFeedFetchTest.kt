package de.lambda9.ready2race.backend.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Die Netzwerk-Fehlerpfade von RaceClockerFeed.fetch, mit einem MockEngine statt eines echten
 * Zugriffs auf raceclocker.com. Der injizierbare [HttpClient]-Parameter dient ausschliesslich diesem
 * Test - der Vorgabewert baut denselben CIO-Client wie zuvor, das Produktionsverhalten ist
 * unveraendert.
 */
class RaceClockerFeedFetchTest {

    private val url = Url("https://raceclocker.com/7c854955?json=1")

    private fun clientRespondingWith(status: HttpStatusCode, body: String = ""): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) { expectSuccess = false }
    }

    /** Holt den Fehlerwert aus dem Exit, statt nur "kein Erfolg" zu prüfen. */
    private fun failureOf(client: HttpClient): RaceClockerError =
        runBlocking { RaceClockerFeed.fetch(url, client) }.unsafeRunSync().fold(
            onSuccess = { fail("Erwartete einen Fehler, bekam Ergebnis: $it") },
            onError = { it },
            onDefect = { fail("Erwartete einen Fehler, bekam einen Defekt: $it") },
        )

    @Test
    fun httpErrorStatusIsReportedAsUnreachable() {
        val client = clientRespondingWith(HttpStatusCode.InternalServerError)

        val error = failureOf(client)

        val unreachable = assertIs<RaceClockerError.Unreachable>(error)
        assertEquals(url.toString(), unreachable.url)
        assertEquals("HTTP 500", unreachable.reason)
    }

    @Test
    fun oversizedBodyIsRejectedWithoutBeingParsed() {
        // MAX_BYTES ist mit 8 MiB bewusst grosszuegig bemessen; ein Feed, der darueber liegt, ist
        // entweder kaputt oder ein Uebertragungsfehler - beides soll nicht erst geparst werden.
        val oversized = "a".repeat(8 * 1024 * 1024 + 1)
        val client = clientRespondingWith(HttpStatusCode.OK, oversized)

        val error = failureOf(client)

        val unreachable = assertIs<RaceClockerError.Unreachable>(error)
        assertEquals("response too large", unreachable.reason)
    }

    @Test
    fun bodyExactlyAtTheLimitIsAccepted() {
        // Grenzfall zur Absicherung von oversizedBodyIsRejectedWithoutBeingParsed: exakt MAX_BYTES
        // darf noch geparst werden, erst darueber wird abgelehnt. Die Fuellzeichen stecken in einem
        // JSON-String-Feld, damit der Body dabei gueltig bleibt.
        val maxBytes = 8 * 1024 * 1024
        val prefix = """[{"Name":""""
        val suffix = """","Bib number":"1","Wave":"None","Result":"00:01:00.0"}]"""
        val padding = "a".repeat(maxBytes - prefix.length - suffix.length)
        val body = prefix + padding + suffix
        assertEquals(maxBytes, body.length, "Testaufbau: Body muss exakt MAX_BYTES lang sein")

        val rows = runBlocking { RaceClockerFeed.fetch(url, clientRespondingWith(HttpStatusCode.OK, body)) }
            .unsafeRunSync().getOrNull()

        assertEquals(padding, rows?.single()?.name)
    }

    @Test
    fun successfulResponseIsParsedNormally() {
        val body = """[{"Name":"Test","Bib number":"1","Wave":"None","Result":"00:01:00.0"}]"""
        val client = clientRespondingWith(HttpStatusCode.OK, body)

        val rows = runBlocking { RaceClockerFeed.fetch(url, client) }.unsafeRunSync().getOrNull()
        assertEquals(1, rows?.size)
        assertEquals("Test", rows?.single()?.name)
    }
}
