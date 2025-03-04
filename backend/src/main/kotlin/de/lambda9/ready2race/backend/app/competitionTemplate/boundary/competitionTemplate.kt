package de.lambda9.ready2race.backend.app.competitionTemplate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateRequest
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.competitionTemplate() {
    route("/competitionTemplate") {
        post {
            val payload = call.receiveV(CompetitionTemplateRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                    val body = !payload
                    CompetitionTemplateService.addCompetitionTemplate(body, user.id!!)
                }
            }
        }
        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<CompetitionTemplateWithPropertiesSort>()

                    CompetitionTemplateService.page(params)
                }
            }
        }

        route("/{competitionTemplateId}") {
            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val competitionTemplateId = !pathParam("competitionTemplateId") { UUID.fromString(it) }
                        CompetitionTemplateService.getCompetitionTemplateWithProperties(competitionTemplateId)
                    }
                }
            }
            put {
                val payload = call.receiveV(CompetitionTemplateRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val competitionTemplateId = !pathParam("competitionTemplateId") { UUID.fromString(it) }

                        val body = !payload
                        CompetitionTemplateService.updateCompetitionTemplate(competitionTemplateId, body, user.id!!)
                    }
                }
            }
            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val competitionTemplateId = !pathParam("competitionTemplateId") { UUID.fromString(it) }
                        CompetitionTemplateService.deleteCompetitionTemplate(competitionTemplateId)
                    }
                }
            }
        }
    }
}