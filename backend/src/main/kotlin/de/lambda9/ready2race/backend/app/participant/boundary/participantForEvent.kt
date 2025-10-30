package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventParticipant.boundary.eventParticipant
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.app.participantTracking.boundary.ParticipantTrackingService
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.participantForEvent() {

    route("/participant") {

        get {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<ParticipantForEventSort>()
                ParticipantService.pageForEvent(params, eventId, user.club, scope)
            }
        }

        route("/{qrCode}/teams") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                    val eventId = !pathParam("eventId", uuid)
                    val qrCode = !pathParam("qrCode")

                    ParticipantTrackingService.getByParticipantQrCode(qrCode, eventId)
                }
            }
        }

        route("/{participantId}") {

            eventParticipant()

            post("/checkInOut") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateAppCompetitionCheckGlobal)
                    val participantId = !pathParam("participantId", uuid)
                    val eventId = !pathParam("eventId", uuid)
                    val checkIn = !queryParam("checkIn", boolean)

                    ParticipantTrackingService.participantCheckInOut(participantId, eventId, user.id!!, checkIn)
                }
            }

        }
    }

    // todo: Remove this on refactoring - this is just a quick solution to provide the participants to the
    //  eventRequirements check page in the app with a different Privilege
    route("/participant-app") {
        get {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAppEventRequirementGlobal)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<ParticipantForEventSort>()
                ParticipantService.pageForEvent(params, eventId, user.club, Privilege.Scope.GLOBAL)
            }
        }
    }
}