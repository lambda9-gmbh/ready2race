package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.reflect.KProperty0

fun interface Validator<T> {
    operator fun invoke(t: T): ValidationResult
    operator fun invoke(prop: KProperty0<T>): ValidationResult = invoke(prop())
}