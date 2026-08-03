package de.lambda9.ready2race.backend.calls.responses

import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.plugins.configureHTTP
import de.lambda9.ready2race.backend.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ETaggedResponseTest {

    data class Payload(val matches: List<String>)

    /** Wird zwischen den Aufrufen verändert, um eine geänderte Antwort zu simulieren. */
    private var payload = Payload(listOf("Vorlauf 1", "Vorlauf 2"))

    private fun ApplicationTestBuilder.setup() {
        application {
            // Mit Kompression wie in der Anwendung: beide greifen am selben Endpoint an.
            configureHTTP(Config.Mode.TEST)
            configureSerialization()
            routing {
                get("/dashboard") {
                    call.respondETagged(ApiResponse.ETagged(payload))
                }
            }
        }
    }

    @Test
    fun firstRequestAnswersWithBodyAndETag() = testApplication {
        setup()

        val response = client.get("/dashboard")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers[HttpHeaders.ETag])
        assertTrue(response.bodyAsText().contains("Vorlauf 1"))
    }

    @Test
    fun unchangedDataAnswersWithoutBody() = testApplication {
        setup()

        val etag = client.get("/dashboard").headers[HttpHeaders.ETag]!!
        val response = client.get("/dashboard") {
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals(etag, response.headers[HttpHeaders.ETag])
    }

    /** Der Dashboard-Endpoint ist komprimiert; ein leerer 304 darf davon nicht berührt werden. */
    @Test
    fun unchangedDataStaysEmptyWithCompressionEnabled() = testApplication {
        setup()

        val etag = client.get("/dashboard") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.headers[HttpHeaders.ETag]!!

        val response = client.get("/dashboard") {
            header(HttpHeaders.AcceptEncoding, "gzip")
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals(0, response.readRawBytes().size)
    }

    @Test
    fun changedDataAnswersWithBodyAgain() = testApplication {
        setup()

        val etag = client.get("/dashboard").headers[HttpHeaders.ETag]!!
        payload = Payload(listOf("Vorlauf 1", "Vorlauf 2", "Finale"))

        val response = client.get("/dashboard") {
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Finale"))
        assertTrue(response.headers[HttpHeaders.ETag] != etag)
    }
}
