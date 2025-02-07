package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import io.ktor.server.routing.*

fun Route.user() {
    route("/user") {
        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.USER)
                    val params = !pagination<AppUserWithRolesSort>()
                    AppUserService.page(params)
                }
            }
        }

        put("/{userId}") {

        }

        post("/register") {
            val payload = call.receiveV(RegisterRequest.example)
            call.respondKIO {
                payload.andThen {
                    AppUserService.register(it)
                }
            }
        }

        post("/verifyRegistration") {
            val payload = call.receiveV(VerifyRegistrationRequest.example)
            call.respondKIO {
                payload.andThen {
                    AppUserService.verifyRegistration(it)
                }
            }
        }

        post("/invite") {
            val payload = call.receiveV(InviteRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate(Privilege.Action.CREATE, Privilege.Resource.USER, Privilege.Scope.GLOBAL)
                    AppUserService.invite(!payload, user)
                }
            }
        }

        post("/acceptInvitation") {
            val payload = call.receiveV(AcceptInvitationRequest.example)
            call.respondKIO {
                payload.andThen {
                    AppUserService.acceptInvitation(it)
                }
            }
        }
    }
}