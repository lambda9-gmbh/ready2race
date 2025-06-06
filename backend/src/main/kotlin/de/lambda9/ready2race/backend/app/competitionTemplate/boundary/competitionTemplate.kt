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
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                val body = !receiveKIO(CompetitionTemplateRequest.example)
                CompetitionTemplateService.addCompetitionTemplate(body, user.id!!)
            }
        }
        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<CompetitionTemplateWithPropertiesSort>()

                CompetitionTemplateService.page(params)
            }
        }

        route("/{competitionTemplateId}") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)
                    CompetitionTemplateService.getCompetitionTemplateWithProperties(competitionTemplateId)
                }
            }
            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)

                    val body = !receiveKIO(CompetitionTemplateRequest.example)
                    CompetitionTemplateService.updateCompetitionTemplate(competitionTemplateId, body, user.id!!)
                }
            }
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val competitionTemplateId = !pathParam("competitionTemplateId", uuid)
                    CompetitionTemplateService.deleteCompetitionTemplate(competitionTemplateId)
                }
            }

            competitionSetup("competitionTemplateId")
        }
    }
}