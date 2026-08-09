package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventSchedule.entity.AdvanceScheduleRequest
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ShiftScheduleRequest
import de.lambda9.ready2race.backend.app.eventSchedule.entity.UpsertScheduleSlotRequest
import de.lambda9.ready2race.backend.app.liveDashboard.entity.OpenResultHandling
import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.authenticateAny
import de.lambda9.ready2race.backend.calls.requests.hasPrivilege
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.xls.checkValidXls
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.eventSchedule() {
    route("/event/{eventId}/schedule") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                EventScheduleService.getSchedule(eventId)
            }
        }
        post("/slot") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(UpsertScheduleSlotRequest.example)

                EventScheduleService.createSlot(eventId, body, user.id!!)
            }
        }
        put("/slot/{slotId}") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)
                val body = !receiveKIO(UpsertScheduleSlotRequest.example)

                EventScheduleService.updateSlot(eventId, slotId, body, user.id!!)
            }
        }
        delete("/slot/{slotId}") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.deleteSlot(eventId, slotId)
            }
        }
        put("/slot/{slotId}/skip") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.setSlotSkipped(
                    eventId, slotId, skipped = true, userId = user.id!!,
                    // Programmpunkte absagen bleibt der Orga vorbehalten - Schiedsrichter
                    // (nur UPDATE LIVE_DASHBOARD) sagen ausschließlich Läufe ab.
                    maySkipFreeSlots = user.hasPrivilege(Privilege.UpdateEventGlobal),
                )
            }
        }
        put("/slot/{slotId}/unskip") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.setSlotSkipped(
                    eventId, slotId, skipped = false, userId = user.id!!,
                    maySkipFreeSlots = user.hasPrivilege(Privilege.UpdateEventGlobal),
                )
            }
        }
        // Regattabüro beendet/aktiviert einen Lauf direkt vom Zeitplan aus (C1) - in JEDEM
        // chainProgressionMode, anders als das Schiedsrichter-Dashboard (dort in REGATTABUERO
        // gesperrt). Der Slot muss LINKED sein.
        put("/slot/{slotId}/finish") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)
                val openResults = !optionalQueryParam("openResults", enum<OpenResultHandling>())

                EventScheduleService.finishSlot(eventId, slotId, user.id!!, openResults)
            }
        }
        put("/slot/{slotId}/activate") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.activateSlot(eventId, slotId, user.id!!)
            }
        }
        put("/round/{setupRoundId}/skip") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val setupRoundId = !pathParam("setupRoundId", uuid)

                EventScheduleService.setRoundSkipped(eventId, setupRoundId, userId = user.id!!)
            }
        }
        // Vorziehen nach einer Absage. Bewusst UPDATE EVENT allein, nicht wie /skip zusätzlich
        // UPDATE LIVE_DASHBOARD: Der Schiedsrichter sagt einen Lauf ab, den Zeitplan baut das
        // Regattabüro um - dieselbe Grenze wie bei /shift.
        post("/slot/{slotId}/advance") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)
                val body = !receiveKIO(AdvanceScheduleRequest.example)

                EventScheduleService.advanceAfterSkippedSlot(eventId, slotId, body, user.id!!)
            }
        }
        post("/shift") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(ShiftScheduleRequest.example)

                EventScheduleService.shiftSchedule(eventId, body, user.id!!)
            }
        }
        get("/import/template") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                EventScheduleService.scheduleImportTemplate(eventId)
            }
        }
        post("/import") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val multiPartData = receiveMultipart()

                var upload: File? = null
                var dryRun: Boolean? = null

                var done = false
                while (!done) {
                    val part = multiPartData.readPart()
                    if (part == null) {
                        done = true
                    } else {
                        when (part) {
                            is PartData.FileItem -> {
                                if (upload == null) {
                                    upload = File(
                                        part.originalFileName!!,
                                        part.provider().toByteArray(),
                                    )
                                } else {
                                    !KIO.fail(RequestError.File.Multiple)
                                }
                            }

                            is PartData.FormItem -> {
                                if (part.name == "dryRun") {
                                    dryRun = part.value.toBooleanStrictOrNull()
                                }
                            }

                            else -> {}
                        }
                        part.dispose()
                    }
                }

                val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                // Fehlt/unparsbar: sicherer Default ist die Vorschau, kein versehentliches Schreiben.
                val dry = dryRun ?: true

                !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                EventScheduleService.importSchedule(
                    eventId = eventId,
                    fileBytes = file.bytes,
                    dryRun = dry,
                    userId = user.id!!,
                )
            }
        }
    }
}
