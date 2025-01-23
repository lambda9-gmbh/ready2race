package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.JEnv
import io.ktor.server.application.*
import io.ktor.util.*

private val kioEnvKey = AttributeKey<JEnv>("kioEnv")

val ApplicationCall.kioEnv get(): JEnv =
    application.attributes[kioEnvKey]

fun Application.configureKIO(env: JEnv) {
    attributes.put(kioEnvKey, env)
}
