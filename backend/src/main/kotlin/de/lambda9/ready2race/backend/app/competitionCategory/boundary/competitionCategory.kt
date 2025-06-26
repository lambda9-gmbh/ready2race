package de.lambda9.ready2race.backend.app.competitionCategory.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryRequest
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionCategory() {
    route("/competitionCategory") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(CompetitionCategoryRequest.example)
                CompetitionCategoryService.addCompetitionCategory(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<CompetitionCategorySort>()
                CompetitionCategoryService.page(params)
            }
        }

        route("/{competitionCategoryId}") {
            put{
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val competitionCategoryId = !pathParam("competitionCategoryId", uuid)

                    val body = !receiveKIO(CompetitionCategoryRequest.example)
                    CompetitionCategoryService.updateCompetitionCategory(competitionCategoryId, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val competitionCategoryId = !pathParam("competitionCategoryId",uuid)
                    CompetitionCategoryService.deleteCompetitionCategory(competitionCategoryId)
                }
            }
        }
    }
}