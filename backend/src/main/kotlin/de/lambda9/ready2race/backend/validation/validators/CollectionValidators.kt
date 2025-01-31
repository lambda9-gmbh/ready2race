package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import kotlin.reflect.KProperty1

object CollectionValidators {
    val notEmpty
        get() = Validator<Collection<*>?> {
            if (it != null && it.isEmpty()) {
                StructuredValidationResult.Invalid.Message { "is empty" }
            } else {
                StructuredValidationResult.Valid
            }
        }

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
                StructuredValidationResult.Invalid.Duplicate(
                    value = it.key,
                    count = it.value
                )
            }
            ?.let { StructuredValidationResult.Invalid.Duplicates(it) }
            ?: StructuredValidationResult.Valid
    }

    fun <T> noDuplicates(field: KProperty1<T, Any?>, vararg fields: KProperty1<T, Any?>) =
        noDuplicatesImpl<T, _> { item ->
                fields.toMutableList().apply {
                    add(0, field)
                }.associate { it.name to it.get(item) }
            }

    val noDuplicates get() = noDuplicatesImpl<Any?, _> { it }
}