package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
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

                CompetitionRegistrationService.getByCompetition(params, competitionId, scope, user)
            }
        }

        post {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                val competitionId = !pathParam("competitionId", uuid)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(CompetitionRegistrationTeamUpsertDto.example)

                val differentRegistrationType = when (scope) {
                    Privilege.Scope.OWN -> null
                    Privilege.Scope.GLOBAL -> {
                        !queryParam("registrationType", enum<RegistrationInvoiceType>())
                    }
                }

                CompetitionRegistrationService.create(body, eventId, competitionId, scope, user, differentRegistrationType)
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

                    val differentRegistrationType = when (scope) {
                        Privilege.Scope.OWN -> null
                        Privilege.Scope.GLOBAL -> {
                            !queryParam("registrationType", enum<RegistrationInvoiceType>())
                        }
                    }

                    CompetitionRegistrationService.update(body, eventId, competitionId, competitionRegistrationId, scope, user, differentRegistrationType)
                }
            }

            delete {
                call.respondComprehension {

                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                    CompetitionRegistrationService.delete(eventId, competitionId, competitionRegistrationId, scope, user)
                }
            }

        }

    }


}