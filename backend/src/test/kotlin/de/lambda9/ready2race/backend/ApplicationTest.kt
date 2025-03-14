package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.plugins.configureHTTP
import de.lambda9.ready2race.backend.plugins.configureRequests
import de.lambda9.ready2race.backend.plugins.configureRouting
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun apiDocumentationTest() = testApplication {

        application {
            configureHTTP(Config.Mode.TEST)
            configureRequests()
            configureRouting()
        }

        val swagger = client.get("/openapi")
        assertEquals(HttpStatusCode.OK, swagger.status)

        val openapi = client.get("/openapi/documentation.yaml")
        assertEquals(HttpStatusCode.OK, openapi.status)
    }
}