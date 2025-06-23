package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.sessions.UserSession
import io.ktor.server.application.*
import io.ktor.server.sessions.*

fun Application.configureSessions() {
    install(Sessions) {
        header<UserSession>("X-Api-Session")
    }
}