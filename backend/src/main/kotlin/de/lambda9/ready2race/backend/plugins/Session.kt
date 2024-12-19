package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.http.UserSession
import io.ktor.server.application.*
import io.ktor.server.sessions.*

fun Application.configureSession() {
    install(Sessions) {
        cookie<UserSession>("user-session") {

        }
    }
}