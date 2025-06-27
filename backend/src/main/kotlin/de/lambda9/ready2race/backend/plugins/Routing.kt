package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.appuser.boundary.user
import de.lambda9.ready2race.backend.app.auth.boundary.auth
import de.lambda9.ready2race.backend.app.bankAccount.boundary.bankAccount
import de.lambda9.ready2race.backend.app.captcha.boundary.captcha
import de.lambda9.ready2race.backend.app.club.boundary.club
import de.lambda9.ready2race.backend.app.competitionCategory.boundary.competitionCategory
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.boundary.competitionSetupTemplate
import de.lambda9.ready2race.backend.app.competitionTemplate.boundary.competitionTemplate
import de.lambda9.ready2race.backend.app.contactInformation.boundary.contactInformation
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.documentTemplate
import de.lambda9.ready2race.backend.app.event.boundary.event
import de.lambda9.ready2race.backend.app.eventDocumentType.boundary.eventDocumentType
import de.lambda9.ready2race.backend.app.fee.boundary.fee
import de.lambda9.ready2race.backend.app.namedParticipant.boundary.namedParticipant
import de.lambda9.ready2race.backend.app.participant.boundary.participant
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.participantRequirement
import de.lambda9.ready2race.backend.app.role.boundary.role
import de.lambda9.ready2race.backend.app.workType.boundary.workType
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api") {
            swaggerUI(path = "documentation")
            auth()
            user()
            role()
            event()
            club()
            namedParticipant()
            fee()
            participantRequirement()
            competitionCategory()
            competitionTemplate()
            competitionSetupTemplate()
            captcha()
            eventDocumentType()
            documentTemplate()
            bankAccount()
            contactInformation()
            workType()
        }
    }
}
