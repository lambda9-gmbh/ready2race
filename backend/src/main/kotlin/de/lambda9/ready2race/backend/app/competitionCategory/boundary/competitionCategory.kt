package de.lambda9.ready2race.backend.app.competitionCategory.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryRequest
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

// todo: Specific rights?
fun Route.competitionCategory() {
    route("/competitionCategory") {
        post {
            val payload = call.receiveV(CompetitionCategoryRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    CompetitionCategoryService.addCompetitionCategory(!payload, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<CompetitionCategorySort>()
                    CompetitionCategoryService.page(params)
                }
            }
        }

        route("/{competitionCategoryId}") {
            put{
                val payload = call.receiveV(CompetitionCategoryRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val competitionCategoryId = !pathParam("competitionCategoryId") { UUID.fromString(it) }
                        CompetitionCategoryService.updateCompetitionCategory(competitionCategoryId, !payload, user.id!!)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val competitionCategoryId = !pathParam("competitionCategoryId") { UUID.fromString(it) }
                        CompetitionCategoryService.deleteCompetitionCategory(competitionCategoryId)
                    }
                }
            }
        }
    }
}