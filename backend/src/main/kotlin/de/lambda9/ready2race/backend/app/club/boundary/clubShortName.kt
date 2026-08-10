package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.entity.ClubShortNameRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Rechte: Lesen mit `ReadClubGlobal`, Ändern mit `UpdateClubGlobal` - bewusst kein eigenes
 * Privileg. Neue Privilegien landen in diesem Projekt erfahrungsgemäß nur an der Admin-Rolle und
 * fehlen allen anderen still, bis es jemandem auffällt.
 */
fun Route.clubShortName() {
    route("/clubShortName") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadClubGlobal)
                val eventId = !optionalQueryParam("eventId", uuid)
                ClubShortNameService.list(eventId)
            }
        }

        // Für den Bearbeiten-Dialog eines Vereins: er kennt nur den Namen, nicht den Schlüssel -
        // den zu bilden ist Serverwissen ([ClubNameKey]), genau wie die automatische Kurzform.
        // Der Name steht in der Abfrage statt im Pfad, weil er Leerzeichen und Punkte enthält.
        route("/forName") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadClubGlobal)
                    val name = !queryParam("name")
                    ClubShortNameService.forName(name)
                }
            }
        }

        // Der Schlüssel steht im Pfad statt im Rumpf, weil er selbst die Kennung des Eintrags ist.
        // Er besteht nach [ClubNameKey] nur aus Buchstaben und Ziffern und braucht deshalb keine
        // Sonderbehandlung in der URL.
        route("/{nameKey}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateClubGlobal)
                    val nameKey = !pathParam("nameKey")
                    val payload = !receiveKIO(ClubShortNameRequest.example)
                    ClubShortNameService.set(nameKey, payload, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateClubGlobal)
                    val nameKey = !pathParam("nameKey")
                    ClubShortNameService.remove(nameKey)
                }
            }
        }
    }
}
