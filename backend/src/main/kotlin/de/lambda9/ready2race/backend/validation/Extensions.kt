package de.lambda9.ready2race.backend.validation

import de.lambda9.ready2race.backend.validation.validators.Validator
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.selfValidator
import kotlin.reflect.KProperty0

fun Collection<ValidationResult>.allOf() = ValidationResult.allOf(*this.toTypedArray())
fun Collection<ValidationResult>.anyOf() = ValidationResult.anyOf(*this.toTypedArray())
fun Collection<ValidationResult>.oneOf() = ValidationResult.oneOf(*this.toTypedArray())

infix fun <V> KProperty0<V>.validate(validator: Validator<V?>) = when (val result = validator(this)) {
    ValidationResult.Valid -> ValidationResult.Valid
    is ValidationResult.Invalid -> ValidationResult.Invalid.Field(name, result)
}

fun <V : Validatable?> KProperty0<V>.validate() = this validate selfValidator
