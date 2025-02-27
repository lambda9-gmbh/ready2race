package de.lambda9.ready2race.backend.app.eventDocument.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

private val logger = KotlinLogging.logger { }

fun Route.eventDocument() {
    route("/eventDocument") {

        post {
            // upload
            val multiPartData = call.receiveMultipart() // todo: default limit 50MB, need custom value?

            multiPartData.readPart()
            multiPartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        logger.error { part.originalFileName }
                    }
                    is PartData.FormItem -> {
                        logger.error { part.name + ": " + part.value }
                    }
                    else -> {}
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val params = !pagination<EventDocumentViewSort>()
                    EventDocumentService.page(params)
                }
            }
        }

        route("/{eventDocumentId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.ReadEventGlobal)
                        val id = !pathParam("eventDocumentId") { UUID.fromString(it) }
                        EventDocumentService.downloadDocument(id)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.UpdateEventGlobal)
                        val id = !pathParam("eventDocumentId") { UUID.fromString(it) }
                        EventDocumentService.deleteDocument(id)
                    }
                }
            }

        }
    }
}