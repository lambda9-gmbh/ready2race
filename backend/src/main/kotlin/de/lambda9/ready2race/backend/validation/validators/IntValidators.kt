package de.lambda9.ready2race.backend.validation.validators

object IntValidators : Validators<Int?>() {
    val notNegative
        get() = simple("is negative") { it >= 0 }

    fun min(min: Int) = simple("is less than $min") { it >= min }
}
