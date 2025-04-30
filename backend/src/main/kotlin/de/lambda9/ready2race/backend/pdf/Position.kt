package de.lambda9.ready2race.backend.pdf

data class Position(
    var x: Float,
    var y: Float,
) {
    companion object {
        val zero
            get() = Position(0f, 0f)
    }
}
