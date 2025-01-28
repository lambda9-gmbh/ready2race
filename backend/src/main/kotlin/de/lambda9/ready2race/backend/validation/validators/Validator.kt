package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import kotlin.reflect.KProperty0

fun interface Validator<T> {
    operator fun invoke(t: T?): StructuredValidationResult
    operator fun invoke(prop: KProperty0<T?>): StructuredValidationResult = invoke(prop())
}