package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.calls.requests.authenticateAny
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*

fun Route.eventInfo() {
    // Alle Endpoints in diesem Block sind öffentlich (kein authenticate). Das Rate-Limit
    // ist eine grob dimensionierte Notbremse gegen Hämmern, siehe Requests.kt.
    route("/event/{eventId}/info") {
        rateLimit(RateLimitName("publicInfo")) {

            // Get upcoming competition matches
            get("/upcoming-matches") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                    EventInfoService.getUpcomingCompetitionMatches(eventId, limit)
                }
            }

            // Get latest match results. matchId grenzt auf das Feld eines einzelnen Laufs
            // ein ("Mein Event") - die Sichtbarkeitsregel bleibt dieselbe wie ohne Filter.
            get("/latest-match-results") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })
                    val competitionId = !optionalQueryParam("competitionId", uuid)
                    val matchId = !optionalQueryParam("matchId", uuid)

                    EventInfoService.getLatestMatchResults(eventId, limit, competitionId, matchId)
                }
            }

            // Get currently running matches
            get("/running-matches") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                    EventInfoService.getRunningMatches(eventId, limit)
                }
            }

            // Der Tab "Live" der öffentlichen Ergebnisanzeige: aktivierte UND anstehende Läufe,
            // jeder mit seinem Zustand. Öffentlich wie die Endpoints darüber.
            get("/live-matches") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val limit = !queryParam("limit", { it.toIntOrNull() ?: 10 })

                    EventInfoService.getLiveMatches(eventId, limit)
                }
            }

            // Der Tab "Zeitplan" der öffentlichen Ergebnisanzeige: das Tagesprogramm aus dem
            // Zeitplan, Slots mit Zustand — ohne Aufstellungen und ohne Ergebnisse.
            get("/program") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)

                    BoardService.getProgram(eventId)
                }
            }

            // Boards sind öffentlich abrufbar wie die Anzeigen darüber: montierte
            // Bildschirme und Athleten-Handys laden ihre URL ohne Anmeldung. Die
            // Kurzliste trägt die Umleitung der alten Athleten-Board-URL.
            get("/boards") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)

                    BoardService.getBoardNames(eventId)
                }
            }

            // Alles, was die Anzeige eines Boards braucht, in einer Antwort.
            get("/board/{boardId}") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val boardId = !pathParam("boardId", uuid)

                    BoardService.getBoardView(eventId, boardId)
                }
            }

            // Persönliches Dashboard, erreichbar über den QR-Code am Teilnehmerband.
            // Öffentlich wie die Anzeigen darüber; welche Felder ein anonymer Aufruf sieht,
            // entscheidet ausschließlich MyEventService.
            get("/my-event/{qrCode}") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val qrCode = !pathParam("qrCode")

                    MyEventService.getMyEvent(eventId, qrCode)
                }
            }
        }
    }

    // Die Board-Verwaltung trägt ein eigenes Rechtepaar (READ/UPDATE BOARD), damit eine
    // Sprecher- oder Streamer-Rolle die Anzeigen pflegen kann, ohne das breite UPDATE EVENT
    // (Wettkämpfe, Gebühren, Urkunden, Zeitnahme) zu bekommen. Die Event-Rechte bleiben
    // gleichwertig zugelassen - bestehende Rollen verlieren dadurch nichts.
    route("/event/{eventId}/boards") {
        // Alle Boards einer Veranstaltung, mit voller Konfiguration (Verwaltungsmaske).
        get {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.ReadBoardGlobal, Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                BoardService.getBoards(eventId)
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateBoardGlobal, Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val request = !receiveKIO(BoardRequest.example)

                BoardService.createBoard(eventId, request)
            }
        }

        put("/{boardId}") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateBoardGlobal, Privilege.UpdateEventGlobal)
                val boardId = !pathParam("boardId", uuid)
                val request = !receiveKIO(BoardRequest.example)

                BoardService.updateBoard(boardId, request)
            }
        }

        delete("/{boardId}") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateBoardGlobal, Privilege.UpdateEventGlobal)
                val boardId = !pathParam("boardId", uuid)

                BoardService.deleteBoard(boardId)
            }
        }
    }
}