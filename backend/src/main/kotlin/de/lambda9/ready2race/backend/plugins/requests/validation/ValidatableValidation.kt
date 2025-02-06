package de.lambda9.ready2race.backend.plugins.requests.validation

import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.application.*
import io.ktor.server.request.*

val ValidatableValidation : RouteScopedPlugin<Unit> = createRouteScopedPlugin("ValidatableValidation") {

    on(RequestBodyTransformed) { content ->
        if (content is Validatable) {
            val result = content.validate()
            if (result is ValidationResult.Invalid) {
                throw ValidatableValidationException(content, result)
            }
        }
    }
}



private object RequestBodyTransformed : Hook<suspend (content: Any) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (content: Any) -> Unit
    ) {
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.After) {
            handler(subject)
        }
    }
}