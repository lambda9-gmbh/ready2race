package de.lambda9.ready2race.backend.app.email.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateRequest
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.emailLanguage
import de.lambda9.ready2race.backend.parsing.Parser.Companion.emailTemplateKey
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.email() {
    route("/email") {

        route("/smtp-override") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadAdministrationConfigGlobal)
                    EmailService.getSMTPConfigOverride()
                }

            }
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    val configOverride = !receiveKIO(SmtpConfigOverrideDto.example)
                    EmailService.setSMTPConfigOverride(configOverride, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    EmailService.deleteSMTPConfigOverride()
                }
            }
        }

        route("/templates") {
            get() {
                call.respondComprehension {
                    !authenticate(Privilege.ReadAdministrationConfigGlobal)
                    val key = !queryParam("key", emailTemplateKey)
                    val language = !queryParam("language", emailLanguage)
                    EmailService.getTemplate(key, language).map { ApiResponse.Dto(it) }
                }
            }

            delete() {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    val key = !queryParam("key", emailTemplateKey)
                    val language = !queryParam("language", emailLanguage)
                    EmailService.deleteTemplate(key, language)
                }
            }

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    val key = !queryParam("key", emailTemplateKey)
                    val language = !queryParam("language", emailLanguage)
                    val template = !receiveKIO(EmailTemplateRequest.example)
                    EmailService.setTemplate(key, language, user.id!!, template)
                }
            }

            get("/list") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadAdministrationConfigGlobal)
                    EmailService.getTemplateKeysWithPlaceholders().map { ApiResponse.Dto(it) }
                }
            }
        }
    }
}