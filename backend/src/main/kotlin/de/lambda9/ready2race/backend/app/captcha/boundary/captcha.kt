package de.lambda9.ready2race.backend.app.captcha.boundary

import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*

fun Route.captcha(){
    route("/captcha") {
        post{
            call.respondKIO {
                KIO.comprehension {
                    CaptchaService.newChallenge()
                }
            }
        }
    }
}