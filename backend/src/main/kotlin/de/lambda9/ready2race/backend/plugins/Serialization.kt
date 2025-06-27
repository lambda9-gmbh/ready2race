package de.lambda9.ready2race.backend.plugins

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.lambda9.ready2race.backend.calls.serialization.registerJavaTime
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureSerialization() {

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerJavaTime()
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
        }
    }
}