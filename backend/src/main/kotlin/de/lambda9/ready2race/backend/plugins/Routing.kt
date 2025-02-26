package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.auth.boundary.auth
import de.lambda9.ready2race.backend.app.event.boundary.event
import de.lambda9.ready2race.backend.app.namedParticipant.boundary.namedParticipant
import de.lambda9.ready2race.backend.app.competitionCategory.boundary.competitionCategory
import de.lambda9.ready2race.backend.app.appuser.boundary.user
import de.lambda9.ready2race.backend.app.captcha.boundary.captcha
import de.lambda9.ready2race.backend.app.competitionTemplate.boundary.competitionTemplate
import de.lambda9.ready2race.backend.app.role.boundary.role
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api") {
            auth()
            user()
            role()
            event()
            namedParticipant()
            competitionCategory()
            competitionTemplate()
            captcha()
        }
    }
}
