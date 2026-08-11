package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.config.Config
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompressionTest {

    /**
     * Roughly the shape of a live dashboard payload: large and highly repetitive.
     */
    private val largeJson = """{"matches":[${(1..200).joinToString(",") { """{"id":$it,"name":"Lauf $it","state":"UPCOMING"}""" }}]}"""

    private fun ApplicationTestBuilder.setup() {
        application {
            configureHTTP(Config.Mode.TEST)
            routing {
                get("/large") {
                    call.respondText(largeJson, ContentType.Application.Json)
                }
                get("/small") {
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
                get("/download") {
                    call.respondBytes(largeJson.toByteArray(), ContentType.Application.Pdf)
                }
            }
        }
    }

    @Test
    fun largeJsonResponseIsCompressed() = testApplication {
        setup()

        val response = client.get("/large") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        assertTrue(
            response.readRawBytes().size < largeJson.toByteArray().size,
            "compressed body should be smaller than the raw payload"
        )
    }

    @Test
    fun smallResponseIsNotCompressed() = testApplication {
        setup()

        val response = client.get("/small") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun alreadyCompressedDownloadIsNotCompressedAgain() = testApplication {
        setup()

        val response = client.get("/download") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.ContentEncoding])
    }
}
