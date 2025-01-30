package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.auth.boundary.auth
import de.lambda9.ready2race.backend.app.event.boundary.event
import de.lambda9.ready2race.backend.app.namedParticipant.boundary.namedParticipant
import de.lambda9.ready2race.backend.app.raceCategory.boundary.raceCategory
import de.lambda9.ready2race.backend.app.raceTemplate.boundary.raceTemplate
import de.lambda9.ready2race.backend.app.user.boundary.user
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api") {
            auth()
            user()
            event()
            namedParticipant()
            raceCategory()
            raceTemplate()
        }
    }
}
