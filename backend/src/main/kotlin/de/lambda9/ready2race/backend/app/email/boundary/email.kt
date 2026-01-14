package de.lambda9.ready2race.backend.app.email.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.boundary.EmailService.emailPlaceholderMapping
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateRequest
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.simple
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import de.lambda9.ready2race.backend.validation.fold

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
                    val language = !queryParam("language", enum<EmailLanguage>())
                    EmailService.getTemplates(language)
                }
            }

            delete() {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    val key = !queryParam("key", enum<EmailTemplateKey>())
                    val language = !queryParam("language", enum<EmailLanguage>())
                    EmailService.deleteTemplate(key, language)
                }
            }

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    val key = !queryParam("key", enum<EmailTemplateKey>())
                    val language = !queryParam("language", enum<EmailLanguage>())
                    val template = !receiveKIO(EmailTemplateRequest.example)
                    val foo = simple<String>("Template body must contain all required placeholders") {
                        val placeholderPattern = "##(.*?)##".toRegex()
                        val usedPlaceholders = placeholderPattern.findAll(it)
                            .map { it.groupValues[1] }
                            .toSet()
                        usedPlaceholders.containsAll(emailPlaceholderMapping[key]!!.required)
                    }(template.body)
                    !foo.fold(
                        onValid = {KIO.unit},
                        onInvalid = {
                            KIO.fail(RequestError.BodyValidationFailed(it))
                        }
                    )
                    EmailService.setTemplate(key, language, user.id!!, template)
                }
            }
        }
    }
}