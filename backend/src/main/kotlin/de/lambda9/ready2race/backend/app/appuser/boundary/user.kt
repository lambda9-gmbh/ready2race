package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetInitRequest
import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetRequest
import de.lambda9.ready2race.backend.app.captcha.boundary.CaptchaService
import de.lambda9.ready2race.backend.requests.*
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import java.util.*

fun Route.user() {
    route("/user") {
        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.ReadUserGlobal)
                    val params = !pagination<AppUserWithRolesSort>()
                    AppUserService.page(params)
                }
            }
        }

        route("/{userId}") {
            get {
                call.respondKIO {
                    KIO.comprehension {
                        val id = !pathParam("userId") { UUID.fromString(it) }
                        val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.USER)
                        if (scope == Privilege.Scope.OWN && user.id != id) {
                            KIO.fail(AuthError.PrivilegeMissing)
                        } else {
                            AppUserService.get(id)
                        }
                    }
                }
            }

            put {

            }
        }

        route("/registration") {
            // todo: evaluate rate limiting. How?
            post {
                val payload = call.receiveV(RegisterRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val captchaId = !queryParam("challenge") { UUID.fromString(it) } // todo: put captcha in helper function
                        val captchaInput = !queryParam("input") { it.toInt() }
                        !CaptchaService.trySolution(captchaId, captchaInput)

                        val body = !payload
                        AppUserService.register(body)
                    }
                }
            }

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.ReadUserGlobal)
                        val params = !pagination<AppUserRegistrationSort>()
                        AppUserService.pageRegistrations(params)
                    }
                }
            }

            post("/verify") {
                val payload = call.receiveV(VerifyRegistrationRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val body = !payload
                        AppUserService.verifyRegistration(body)
                    }
                }
            }
        }

        route("invitation") {
            post {
                val payload = call.receiveV(InviteRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val user = !authenticate(Privilege.CreateUserGlobal)
                        AppUserService.invite(!payload, user)
                    }
                }
            }

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.ReadUserGlobal)
                        val params = !pagination<AppUserInvitationWithRolesSort>()
                        AppUserService.pageInvitations(params)
                    }
                }
            }

            post("/accept") {
                val payload = call.receiveV(AcceptInvitationRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val body = !payload
                        AppUserService.acceptInvitation(body)
                    }
                }
            }
        }
        route("/resetPassword"){
            rateLimit(RateLimitName("resetPassword")) {
                post{
                    val payload = call.receiveV(PasswordResetInitRequest.example)
                    call.respondKIO {
                        KIO.comprehension {
                            val captchaId = !queryParam("challenge") { UUID.fromString(it) }
                            val captchaInput = !queryParam("input") { it.toInt() }
                            !CaptchaService.trySolution(captchaId, captchaInput)

                            AppUserService.initPasswordReset(!payload)
                        }
                    }
                }
            }

            route("/{passwordResetToken}"){
                put{
                    val payload = call.receiveV(PasswordResetRequest.example)
                    call.respondKIO {
                        KIO.comprehension {
                            val token = !pathParam("passwordResetToken")
                            AppUserService.resetPassword(token, !payload)
                        }
                    }
                }
            }
        }
    }
}