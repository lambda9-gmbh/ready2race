package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleOrderRequest
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Rechte wie bei den Kurzformen: Lesen mit `ReadClubGlobal`, Ändern mit `UpdateClubGlobal`. Kein
 * eigenes Privileg - neue Privilegien landen in diesem Projekt erfahrungsgemäß nur an der
 * Admin-Rolle und fehlen allen anderen still.
 */
fun Route.clubNameRule() {
    route("/clubNameRule") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadClubGlobal)
                ClubNameRuleService.all()
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateClubGlobal)
                val payload = !receiveKIO(ClubNameRuleRequest.example)
                ClubNameRuleService.add(payload, user.id!!)
            }
        }

        // Vor "/{ruleId}", damit die Reihenfolge nicht als Kennung gelesen wird.
        route("/order") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateClubGlobal)
                    val payload = !receiveKIO(ClubNameRuleOrderRequest.example)
                    ClubNameRuleService.reorder(payload, user.id!!)
                }
            }
        }

        route("/{ruleId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateClubGlobal)
                    val ruleId = !pathParam("ruleId", uuid)
                    val payload = !receiveKIO(ClubNameRuleRequest.example)
                    ClubNameRuleService.update(ruleId, payload, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateClubGlobal)
                    val ruleId = !pathParam("ruleId", uuid)
                    ClubNameRuleService.remove(ruleId)
                }
            }
        }
    }
}
