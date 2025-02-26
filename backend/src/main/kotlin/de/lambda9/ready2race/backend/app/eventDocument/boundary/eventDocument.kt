package de.lambda9.ready2race.backend.app.eventDocument.boundary

import io.ktor.server.routing.*

// sub-route to /event/{id} for event id
// query for document type
fun Route.eventDocument() {
    route("/eventDocument") {

        post {

        }

        get {

        }

        route("/{eventDocumentId}") {

            get {

            }

            put {

            }

            delete {

            }

        }
    }
}