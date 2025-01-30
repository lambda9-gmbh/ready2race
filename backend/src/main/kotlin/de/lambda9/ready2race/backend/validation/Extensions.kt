package de.lambda9.ready2race.backend.validation

import de.lambda9.ready2race.backend.validation.validators.Validator
import de.lambda9.ready2race.backend.validation.validators.Validators.selfValidator
import kotlin.reflect.KProperty0

fun Collection<StructuredValidationResult>.allOf() = StructuredValidationResult.allOf(*this.toTypedArray())
fun Collection<StructuredValidationResult>.anyOf() = StructuredValidationResult.anyOf(*this.toTypedArray())

infix fun <V> KProperty0<V>.validate(validator: Validator<V?>) = when (val result = validator(this)) {
    StructuredValidationResult.Valid -> StructuredValidationResult.Valid
    is StructuredValidationResult.Invalid -> StructuredValidationResult.Invalid.Field(name, result)
}

fun <V : Validatable?> KProperty0<V>.validate() = this validate selfValidator()
