package de.lambda9.ready2race.backend.app.raceclocker.entity

/**
 * Where to find a given match in RaceClocker.
 *
 * A competition needs two RaceClocker races, so there are two URLs: a qualification round is timed as
 * an individual-start race (only those have a real countdown), everything else as a wave-start race.
 * [isQualification] picks between them, so nothing has to be assigned by hand.
 */
data class RaceClockerMatchTarget(
    /** The match name, which is exported as - and read back as - the RaceClocker wave name. */
    val waveName: String?,
    val isQualification: Boolean,
    val timeTrialUrl: String?,
    val heatsUrl: String?,
) {
    val resultsUrl: String? get() = (if (isQualification) timeTrialUrl else heatsUrl)?.takeIf { it.isNotBlank() }
}
