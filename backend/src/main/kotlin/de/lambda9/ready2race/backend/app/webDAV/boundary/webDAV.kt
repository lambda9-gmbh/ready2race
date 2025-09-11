package de.lambda9.ready2race.backend.app.webDAV.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getImportOptionFolders
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getImportOptionTypes
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.initializeExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.webDAV() {
    route("/webDAV") {
        route("/export") {
            post {

                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)

                    val body = !receiveKIO(WebDAVExportRequest.example)
                    initializeExportData(body, user.id!!)
                }
            }
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)

                    WebDAVService.getExportStatus()
                }
            }
        }

        route("/import") {
            route("/options") {
                get {
                    call.respondComprehension {
                        !authenticate(Privilege.ReadEventGlobal)

                        getImportOptionFolders()
                    }
                }

                route("/{folderName}") {
                    get {
                        call.respondComprehension {
                            !authenticate(Privilege.ReadEventGlobal)
                            val folderName = !pathParam("folderName")

                            getImportOptionTypes(folderName)
                        }
                    }
                }
            }
        }
    }
}