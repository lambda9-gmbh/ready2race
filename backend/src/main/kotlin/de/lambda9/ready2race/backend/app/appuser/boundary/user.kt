package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.task.boundary.TaskService
import de.lambda9.ready2race.backend.app.task.entity.TaskWithResponsibleUsersSort
import de.lambda9.ready2race.backend.app.workShift.boundary.WorkShiftService
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.parsing.Parser.Companion.datetime
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*

fun Route.user() {
    route("/user") {
        get {
            call.respondComprehension {
                val user = !authenticate(Privilege.ReadUserGlobal)
                if (user.id == SYSTEM_USER) {
                    val params = !pagination<EveryAppUserWithRolesSort>()
                    AppUserService.pageIncludingAdmins(params)
                } else {
                    val params = !pagination<AppUserWithRolesSort>()
                    AppUserService.page(params)
                }
            }
        }

        route("/{userId}") {
            get {
                call.respondComprehension {
                    val id = !pathParam("userId", uuid)
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.USER)
                    if (scope == Privilege.Scope.OWN && user.id != id) {
                        KIO.fail(AuthError.PrivilegeMissing)
                    } else if (user.id == SYSTEM_USER || user.id == id) {
                        AppUserService.getIncludingAllAdmins(id)
                    } else {
                        // TODO: This gives 404 instead of 403, when trying to get another admin as non system user admin
                        AppUserService.get(id)
                    }
                }
            }

            put {
                call.respondComprehension {
                    val id = !pathParam("userId", uuid)
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.USER)
                    val body = !receiveKIO(UpdateAppUserRequest.example)

                    AppUserService.update(body, scope, user.id!!, id)
                }
            }

            route("/task") {
                get {
                    call.respondComprehension {
                        val id = !pathParam("userId", uuid)
                        val (user, scope) = !authenticate(
                            Privilege.Action.READ,
                            Privilege.Resource.EVENT
                        )
                        val params = !pagination<TaskWithResponsibleUsersSort>()

                        if (scope == Privilege.Scope.OWN && id != user.id!!) {
                            KIO.fail(AuthError.PrivilegeMissing)
                        } else {
                            TaskService.pageOpenForUser(params, id)
                        }
                    }
                }
            }

            route("/workshift") {
                get {
                    call.respondComprehension {
                        val id = !pathParam("userId", uuid)
                        val (user, scope) = !authenticate(
                            Privilege.Action.READ,
                            Privilege.Resource.EVENT
                        )
                        val params = !pagination<WorkShiftWithAssignedUsersSort>()
                        val timeFrom = !optionalQueryParam("timeFrom", datetime)
                        val timeTo = !optionalQueryParam("timeTo", datetime)

                        if (scope == Privilege.Scope.OWN && id != user.id!!) {
                            KIO.fail(AuthError.PrivilegeMissing)
                        } else {
                            WorkShiftService.pageByUser(params, id, timeFrom, timeTo)
                        }
                    }
                }
            }

        }

        route("/registration") {
            post {
                call.respondComprehension {
                    !checkCaptcha()
                    val body = !receiveKIO(RegisterRequest.example)
                    AppUserService.register(body)
                }
            }

            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadUserGlobal)
                    val params = !pagination<AppUserRegistrationSort>()
                    AppUserService.pageRegistrations(params)
                }
            }

            post("/verify") {
                call.respondComprehension {
                    val body = !receiveKIO(VerifyRegistrationRequest.example)
                    AppUserService.verifyRegistration(body)
                }
            }
        }

        route("invitation") {
            post {
                call.respondComprehension {
                    val user = !authenticate(Privilege.CreateUserGlobal)
                    val body = !receiveKIO(InviteRequest.example)
                    AppUserService.invite(body, user)
                }
            }

            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadUserGlobal)
                    val params = !pagination<AppUserInvitationWithRolesSort>()
                    AppUserService.pageInvitations(params)
                }
            }

            post("/accept") {
                call.respondComprehension {
                    val body = !receiveKIO(AcceptInvitationRequest.example)
                    AppUserService.acceptInvitation(body)
                }
            }
        }
        route("/resetPassword") {
            rateLimit(RateLimitName("resetPassword")) {
                post {
                    call.respondComprehension {
                        !checkCaptcha()
                        val body = !receiveKIO(PasswordResetInitRequest.example)
                        AppUserService.initPasswordReset(body)
                    }
                }
            }

            put("/{passwordResetToken}") {
                call.respondComprehension {
                    val token = !pathParam("passwordResetToken")
                    val body = !receiveKIO(PasswordResetRequest.example)
                    AppUserService.resetPassword(token, body)
                }
            }
        }

    }
}