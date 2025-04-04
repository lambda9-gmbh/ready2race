package de.lambda9.ready2race.backend.app.captcha.boundary

import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.captcha(){
    route("/captcha") {
        post{
            call.respondComprehension {
                CaptchaService.newChallenge()
            }
        }
    }
}