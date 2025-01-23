package de.lambda9.ready2race.backend.validation

import de.lambda9.ready2race.backend.validation.validators.Validator
import de.lambda9.ready2race.backend.validation.validators.Validators
import de.lambda9.ready2race.backend.validation.validators.Validators.Companion.selfValidator
import kotlin.reflect.KProperty0

fun Collection<StructuredValidationResult>.allOf() = StructuredValidationResult.allOf(*this.toTypedArray())
fun Collection<StructuredValidationResult>.oneOf() = StructuredValidationResult.oneOf(*this.toTypedArray())

fun <V> KProperty0<V>.validate(validator: Validators<V>.() -> Validator<V?>) = when (val result = validator(Validators())(this)) {
    StructuredValidationResult.Valid -> StructuredValidationResult.Valid
    is StructuredValidationResult.Invalid -> StructuredValidationResult.Invalid.Field(name, result)
}

fun <V: Validatable?> KProperty0<V>.validate() = validate { selfValidator() }

fun <V> KProperty0<Collection<V>?>.validateAllElements(validator: Validators<V>.() -> Validator<V?>) =
    Validators<V>().let { validators ->
        get()?.mapIndexed { index, element -> index to validator(validators)(element) }
            ?.filterIsInstance<Pair<Int, StructuredValidationResult.Invalid>>()
            ?.takeIf { it.isNotEmpty() }
            ?.let { StructuredValidationResult.Invalid.BadCollectionElements(it.toMap()) }
            ?: StructuredValidationResult.Valid
    }

fun <V: Validatable?> KProperty0<Collection<V>>.validateAllElements() = validateAllElements { selfValidator() }
