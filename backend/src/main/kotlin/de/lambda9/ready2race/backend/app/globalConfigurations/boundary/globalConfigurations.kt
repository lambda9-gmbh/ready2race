package de.lambda9.ready2race.backend.app.globalConfigurations.boundary

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateGlobalConfigurationsRequest
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateThemeRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.globalConfigurations(env: JEnv) {
    val jsonMapper = ObjectMapper()

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
            get {
                call.respondComprehension {
                    ThemeService.getTheme(env)
                }
            }

            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)

                    var request: UpdateThemeRequest? = null
                    var fontFile: Pair<String, ByteArray>? = null

                    val multipartData = call.receiveMultipart()
                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fontFile = Pair(part.originalFileName!!, part.provider().readRemaining().readByteArray())
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

                    val validatedRequest = !receiveKIO(request ?: UpdateThemeRequest.example)
                    ThemeService.updateTheme(env, validatedRequest, fontFile)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                    ThemeService.resetTheme(env)
                }
            }
        }
    }
}