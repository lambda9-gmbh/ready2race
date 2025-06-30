package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.testing.testApplicationComprehension
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {

    @Test
    fun primitiveNullsDeserialize() = testApplicationComprehension {

        data class Primitives(
            val int: Int,
            val float: Float,
            val bool: Boolean,
        ) : Validatable {
            override fun validate(): ValidationResult = ValidationResult.Valid
        }

        val path = "/deserializeNulls"

        extendApplication {

            routing {
                post(path) {
                    call.respondComprehension {
                        receiveKIO(Primitives(1, 1f, true)).map {
                            ApiResponse.Dto(it)
                        }
                    }
                }
            }
        }

        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}