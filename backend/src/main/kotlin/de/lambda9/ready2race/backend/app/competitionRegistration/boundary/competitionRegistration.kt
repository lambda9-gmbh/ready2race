package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationRequestProperties
import de.lambda9.ready2race.backend.app.competitionDeregistration.boundary.competitionDeregistration
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamSort
import de.lambda9.ready2race.backend.app.eventRegistration.entity.CompetitionRegistrationTeamUpsertDto
import de.lambda9.ready2race.backend.app.invoice.entity.RegistrationInvoiceType
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionRegistration() {

    route("/competitionRegistration") {
        get {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                val competitionId = !pathParam("competitionId", uuid)
                val params = !pagination<CompetitionRegistrationSort>()

                CompetitionRegistrationService.registrationPage(params, competitionId, scope, user)
            }
        }

        post {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                val competitionId = !pathParam("competitionId", uuid)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(CompetitionRegistrationTeamUpsertDto.example)

                val requestProperties = when (scope) {
                    Privilege.Scope.OWN -> CompetitionRegistrationRequestProperties.None
                    Privilege.Scope.GLOBAL -> {
                        val registrationType = !queryParam("registrationType", enum<RegistrationInvoiceType>())
                        val ratingCategory = !optionalQueryParam("ratingCategory", uuid)
                        CompetitionRegistrationRequestProperties.Permitted(
                            registrationType = registrationType,
                            ratingCategory = ratingCategory,
                        )
                    }
                }

                CompetitionRegistrationService.create(body, eventId, competitionId, scope, user, requestProperties)
            }
        }

        route("/{competitionRegistrationId}") {

            put {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)
                    val body = !receiveKIO(CompetitionRegistrationTeamUpsertDto.example)

                    val requestProperties = when (scope) {
                        Privilege.Scope.OWN -> CompetitionRegistrationRequestProperties.None
                        Privilege.Scope.GLOBAL -> {
                            val registrationType = !queryParam("registrationType", enum<RegistrationInvoiceType>())
                            val ratingCategory = !optionalQueryParam("ratingCategory", uuid)
                            CompetitionRegistrationRequestProperties.Permitted(
                                registrationType = registrationType,
                                ratingCategory = ratingCategory,
                            )
                        }
                    }

                    CompetitionRegistrationService.update(
                        body,
                        eventId,
                        competitionId,
                        competitionRegistrationId,
                        scope,
                        user,
                        requestProperties
                    )
                }
            }

            delete {
                call.respondComprehension {

                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                    CompetitionRegistrationService.delete(
                        eventId,
                        competitionId,
                        competitionRegistrationId,
                        scope,
                        user
                    )
                }
            }

            competitionDeregistration()
        }

        route("/teams") {
            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val params = !pagination<CompetitionRegistrationTeamSort>()

                    CompetitionRegistrationService.teamPage(params, eventId, competitionId, scope, user)
                }
            }
        }

    }


}