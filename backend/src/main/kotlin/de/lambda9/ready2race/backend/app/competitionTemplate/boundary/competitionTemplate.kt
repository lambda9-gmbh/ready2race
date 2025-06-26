package de.lambda9.ready2race.backend.app.competitionTemplate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.competitionSetup
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateRequest
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionTemplate() {
    route("/competitionTemplate") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(CompetitionTemplateRequest.example)
                CompetitionTemplateService.addCompetitionTemplate(body, user.id!!)
            }
        }
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<CompetitionTemplateWithPropertiesSort>()

                CompetitionTemplateService.page(params)
            }
        }

        route("/{competitionTemplateId}") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)
                    CompetitionTemplateService.getCompetitionTemplateWithProperties(competitionTemplateId)
                }
            }
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)

                    val body = !receiveKIO(CompetitionTemplateRequest.example)
                    CompetitionTemplateService.updateCompetitionTemplate(competitionTemplateId, body, user.id!!)
                }
            }
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)
                    CompetitionTemplateService.deleteCompetitionTemplate(competitionTemplateId)
                }
            }

            competitionSetup("competitionTemplateId")
        }
    }
}