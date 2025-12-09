package de.lambda9.ready2race.backend

fun <A: Any?> lexiNumberComp(stringSelector: (A) -> String?) = Comparator<A> { a, b ->
    val identA = a?.let(stringSelector)
    val identB = b?.let(stringSelector)

    if (identA == null && identB == null) {
        0
    } else if (identA == null) {
        1
    } else if (identB == null) {
        -1
    } else {
        val digitsA = identA.takeLastWhile { it.isDigit() }
        val digitsB = identB.takeLastWhile { it.isDigit() }

        val prefixA = identA.removeSuffix(digitsA)
        val prefixB = identB.removeSuffix(digitsB)

        val intA = digitsA.toIntOrNull() ?: 0
        val intB = digitsB.toIntOrNull() ?: 0

        // sort by lexicographical, except integer suffixes
        when {
            prefixA < prefixB -> -1
            prefixA > prefixB -> 1
            intA < intB -> -1
            intA > intB -> 1
            else -> 0
        }
    }
}

fun <T> singletonOrFallback(clubs: Set<T>, mixedTeamTerm: T): T {
    return if (clubs.size == 1) {
        clubs.first()
    } else {
        mixedTeamTerm
    }
}