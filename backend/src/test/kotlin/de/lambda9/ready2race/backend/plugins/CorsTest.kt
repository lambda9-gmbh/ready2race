package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.config.parseCorsOrigin
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CorsTest {
    @Test
    fun `production permits the configured embedding origin and session headers`() = testApplication {
        application {
            configureHTTP(Config.Mode.PROD, listOf("https://club.example.org"))
            routing { get("/api/test") { call.respondText("ok") } }
        }
        val response = client.options("/api/test") {
            header(HttpHeaders.Origin, "https://club.example.org")
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-api-session")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("https://club.example.org", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("true", response.headers[HttpHeaders.AccessControlAllowCredentials])

        val rejected = client.get("/api/test") { header(HttpHeaders.Origin, "https://other.example.org") }
        assertEquals(HttpStatusCode.Forbidden, rejected.status)
        assertNull(rejected.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `production does not enable cross origin access by default`() = testApplication {
        application {
            configureHTTP(Config.Mode.PROD)
            routing { get("/api/test") { call.respondText("ok") } }
        }
        val response = client.get("/api/test") { header(HttpHeaders.Origin, "https://club.example.org") }
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `origins reject paths credentials wildcards and non HTTP schemes`() {
        listOf("https://club.example.org/", "https://user@club.example.org", "https://*.example.org",
            "ftp://club.example.org", "https://club.example.org?query=1").forEach {
            assertFailsWith<IllegalArgumentException> { parseCorsOrigin(it) }
        }
        assertEquals("club.example.org:8443", parseCorsOrigin("https://club.example.org:8443").rawAuthority)
    }
}
