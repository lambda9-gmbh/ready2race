package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.config.Config
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP(mode: Config.Mode) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    if (mode == Config.Mode.DEV) {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            exposeHeader(HttpHeaders.ContentDisposition)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowCredentials = true
        }
    }
}
