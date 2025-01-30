package de.lambda9.ready2race.backend.app.raceCategory.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

// todo: Specific rights?
fun Route.raceCategory() {
    route("/raceCategory") {
        post {
            val params = call.receiveV(RaceCategoryDto.example)
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    RaceCategoryService.addRaceCategory(params.getOrThrow())
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    RaceCategoryService.getRaceCategoryList()
                }
            }
        }

        route("/{raceCategoryId}") {
            put{
                val params = call.receiveV(RaceCategoryDto.example)
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val raceCategoryId = !pathParam("raceCategoryId") { UUID.fromString(it) }
                        RaceCategoryService.updateRaceCategory(params.getOrThrow(), raceCategoryId)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val raceCategoryId = !pathParam("raceCategoryId") { UUID.fromString(it) }
                        RaceCategoryService.deleteRaceCategory(raceCategoryId)
                    }
                }
            }
        }
    }
}