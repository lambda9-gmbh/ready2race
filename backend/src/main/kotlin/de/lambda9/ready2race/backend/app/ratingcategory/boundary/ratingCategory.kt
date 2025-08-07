package de.lambda9.ready2race.backend.app.ratingcategory.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRequest
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategorySort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.ratingCategory() {

    route("/ratingCategory") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<RatingCategorySort>()
                RatingCategoryService.page(params)
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val body = !receiveKIO(RatingCategoryRequest.example)
                RatingCategoryService.addCategory(body, user.id!!)
            }
        }

        route("/{ratingCategoryId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("ratingCategoryId", uuid)
                    val body = !receiveKIO(RatingCategoryRequest.example)
                    RatingCategoryService.updateCategory(id, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("ratingCategoryId", uuid)
                    RatingCategoryService.deleteCategory(id)
                }
            }

        }

    }

}