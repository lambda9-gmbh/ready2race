package de.lambda9.ready2race.backend.app.competitionSetupTemplate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.CompetitionSetupTemplateRequest
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.CompetitionSetupTemplateSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionSetupTemplate() {
    route("/competitionSetupTemplate") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(CompetitionSetupTemplateRequest.example)
                CompetitionSetupTemplateService.add(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<CompetitionSetupTemplateSort>()
                CompetitionSetupTemplateService.page(params)
            }
        }

        route("/{competitionSetupTemplateId}") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("competitionSetupTemplateId", uuid)
                    CompetitionSetupTemplateService.getById(id)
                }
            }

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("competitionSetupTemplateId", uuid)

                    val body = !receiveKIO(CompetitionSetupTemplateRequest.example)
                    CompetitionSetupTemplateService.update(body, user.id!!, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("competitionSetupTemplateId", uuid)
                    CompetitionSetupTemplateService.delete(id)
                }
            }
        }
    }
}