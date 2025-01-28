package de.lambda9.ready2race.backend.schedule

import kotlin.time.Duration

sealed interface Policy<A> {

    data class Fixed(val interval: Duration): Policy<Unit>

    data class Dynamic<A>(val block: (A?) -> Duration): Policy<A>

}