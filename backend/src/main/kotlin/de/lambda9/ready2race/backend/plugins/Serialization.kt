package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.serialization.registerJavaTime
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureSerialization() {

    install(ContentNegotiation) {
        jackson {
            registerJavaTime()
        }
    }
}