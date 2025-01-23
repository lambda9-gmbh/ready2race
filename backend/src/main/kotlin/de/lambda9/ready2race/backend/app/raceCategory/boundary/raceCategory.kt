package de.lambda9.ready2race.backend.app.raceCategory.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.raceCategory() {
    route("/raceCategory") {
        post {
            val params = call.receive<String>()
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)// todo: Other rights?
                    RaceCategoryService.addRaceCategory(params)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT) // todo: Other rights?
                    RaceCategoryService.getRaceCategoryList()
                }
            }
        }

        route("/{raceCategoryId}") {
            put{
                val params = call.receive<String>()
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT) // todo: Other rights?
                        val prevName = !pathParam("raceCategoryId") { it }
                        RaceCategoryService.updateRaceCategory(prevName, params)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT) // todo: Other rights?
                        val prevName = !pathParam("name") { it }
                        RaceCategoryService.deleteRaceCategory(prevName)
                    }
                }
            }
        }
    }
}