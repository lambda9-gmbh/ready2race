package de.lambda9.ready2race.backend.validation.validators

abstract class Validators<T> {

    protected fun simple(message: String, valid: (T & Any) -> Boolean) = Validator.simple<T>(message, valid)

}

