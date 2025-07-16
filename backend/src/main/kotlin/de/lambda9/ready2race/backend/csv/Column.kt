package de.lambda9.ready2race.backend.csv

data class Column<A>(
    val header: String,
    val f: A.(index: Int) -> String,
)
