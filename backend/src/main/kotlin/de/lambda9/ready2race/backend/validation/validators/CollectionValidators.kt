package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.reflect.KProperty1

object CollectionValidators : Validators<Collection<Any?>?>() {
    val notEmpty
        get() = simple("is empty") { it.isNotEmpty() }

    private fun <T, K> noDuplicatesImpl(
        keySelector: (T & Any) -> K,
    ) = Validator<Collection<T>?> { collection ->
        collection
            ?.filterNotNull()
            ?.groupingBy { keySelector(it) }
            ?.eachCount()
            ?.filter { it.value > 1 }
            ?.takeIf { it.isNotEmpty() }
            ?.map {
                ValidationResult.Invalid.Duplicate(
                    value = it.key,
                    count = it.value
                )
            }
            ?.let { ValidationResult.Invalid.Duplicates(it) }
            ?: ValidationResult.Valid
    }

    // TODO: currently null-fields are distinct
    fun <T> noDuplicates(field: KProperty1<T, Any?>, vararg fields: KProperty1<T, Any?>) =
        noDuplicatesImpl<T, _> { item ->
                fields.toMutableList().apply {
                    add(0, field)
                }.associate { it.name to it.get(item) }
            }

    // TODO: currently nulls are not distinct
    val noDuplicates get() = noDuplicatesImpl<Any?, _> { it }

    fun <S, T> flatMap(validator: Validator<Collection<S>?>, field: KProperty1<T, Collection<S>>) = Validator<Collection<T>?> { collection ->
        collection
            ?.flatMap { field.get(it) }
            ?.let { validator(it) }
            ?: ValidationResult.Valid
    }
}