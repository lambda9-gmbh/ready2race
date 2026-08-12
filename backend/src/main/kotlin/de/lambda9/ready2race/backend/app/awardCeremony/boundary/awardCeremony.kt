package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListOptions
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListSize
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.awardCeremony() {

    route("/awardCeremony") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                // Von der Platzierungsseite eines Wettkampfs aus interessiert nur dieser eine.
                // Ohne die Einschränkung berechnete ein Klick dort die Plätze aller Rennen.
                val competitionId = !optionalQueryParam("competitionId", uuid)

                AwardCeremonyService.listCeremonies(eventId, competitionId)
            }
        }

        // POST, obwohl es ein Download ist: die Auswahl umfasst bei einer Regatta mit 40 Rennen
        // leicht hundert Schlüssel und passt nicht mehr sinnvoll in einen Query-String.
        post("/pdf") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(AwardCeremonySelectionRequest.example)

                AwardCeremonyService.download(eventId, body)
            }
        }
    }

    route("/resultList") {
        // GET, anders als der Bogen: hier gibt es keine Auswahl einzelner Ehrungen, nur eine
        // Handvoll Schalter - die passen in den Query-String. Jeder fehlende Schalter fällt auf
        // den Aushang-Vorgabewert zurück, das Preset „Siegerehrung" setzt der Dialog über die
        // einzelnen Parameter.
        get("/pdf") {
            call.respondComprehension {
                // Dieselben Rechte wie der Siegerehrungsbogen: beides sind interne Ergebnisdrucke.
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                // Von der Platzierungsseite eines Wettkampfs aus interessiert nur dieser eine -
                // dieselbe Kostenbegründung wie bei der Ehrungs-Auswahl oben.
                val competitionId = !optionalQueryParam("competitionId", uuid)

                val options = ResultListOptions(
                    heading = "ERGEBNISLISTE",
                    includeCrew = !optionalQueryParam("crew", boolean) ?: true,
                    includeTimes = !optionalQueryParam("times", boolean) ?: true,
                    podiumOnly = !optionalQueryParam("podiumOnly", boolean) ?: false,
                    byRatingCategory = !optionalQueryParam("byRatingCategory", boolean) ?: true,
                    size = !optionalQueryParam("size", enum<ResultListSize>()) ?: ResultListSize.POSTING,
                    // Die Fußzeile mit dem Stand setzt der Service - er kennt die Veranstaltung.
                    footerLine = null,
                )

                AwardCeremonyService.resultList(eventId, competitionId, options)
            }
        }
    }
}
