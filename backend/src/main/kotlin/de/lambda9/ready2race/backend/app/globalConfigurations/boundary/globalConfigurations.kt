package de.lambda9.ready2race.backend.app.globalConfigurations.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateGlobalConfigurationsRequest
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateThemeRequest
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantImportRequest
import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.globalConfigurations(env: JEnv) {

    route("/globalConfigurations") {
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                val request = !receiveKIO(UpdateGlobalConfigurationsRequest.example)

                GlobalConfigurationsService.updateConfigurations(request, user.id!!)
            }
        }
        get("/createClubOnRegistration") {
            call.respondComprehension {
                GlobalConfigurationsService.getCreateClubOnRegistration()
            }
        }

        route("/theme") {
            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)

                    val multipartData = receiveMultipart()

                    var request: UpdateThemeRequest? = null
                    var fontFile: File? = null
                    var logoFile: File? = null

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                when (part.name) {
                                    "fontFile" -> {
                                        if (fontFile == null) {
                                            fontFile = File(
                                                part.originalFileName!!,
                                                part.provider().toByteArray(),
                                            )
                                        } else {
                                            KIO.fail(RequestError.File.Multiple)
                                        }
                                    }
                                    "logoFile" -> {
                                        if (logoFile == null) {
                                            logoFile = File(
                                                part.originalFileName!!,
                                                part.provider().toByteArray(),
                                            )
                                        } else {
                                            KIO.fail(RequestError.File.Multiple)
                                        }
                                    }
                                }
                            }

                            is PartData.FormItem -> {
                                if (part.name == "request") {
                                    request = jsonMapper.readValue<UpdateThemeRequest>(part.value)
                                }
                            }

                            else -> {}
                        }
                        part.dispose()
                    }

                    // Validate not null and Validatable
                    val validatedRequest =
                        !KIO.failOnNull(request) { RequestError.BodyMissing(ParticipantImportRequest.example) }
                            .andThen { req ->
                                val validationResult = req.validate()
                                if (validationResult is ValidationResult.Invalid) {
                                    KIO.fail(RequestError.BodyValidationFailed(validationResult))
                                } else {
                                    KIO.ok(req)
                                }
                            }

                    ThemeService.updateTheme(validatedRequest, fontFile, logoFile)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    ThemeService.resetTheme()
                }
            }
        }
    }
}