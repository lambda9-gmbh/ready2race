package de.lambda9.ready2race.backend.app.raceclocker.entity

/**
 * Where to find a given match in RaceClocker.
 *
 * A competition needs two RaceClocker races, so there are two URLs: a qualification round is timed as
 * an individual-start race (only those have a real countdown), everything else as a wave-start race.
 * [isQualification] picks between them, so nothing has to be assigned by hand.
 *
 * That flag is a tournament-tree setting, though, and nothing stops a time trial round from being left
 * unmarked. The pull therefore treats it as a preference rather than a fact: it starts at
 * [resultsUrl] and falls back to [alternateResultsUrl] when the match cannot be found there.
 */
data class RaceClockerMatchTarget(
    /**
     * The planned start time, the competition (number and short name) and the match name (see
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName]), exported as the
     * RaceClocker wave name. Only used for pre-match-id start lists.
     */
    val waveName: String?,
    val isQualification: Boolean,
    val timeTrialUrl: String?,
    val heatsUrl: String?,
) {
    val resultsUrl: String? get() = (if (isQualification) timeTrialUrl else heatsUrl)?.takeIf { it.isNotBlank() }

    val alternateResultsUrl: String? get() = (if (isQualification) heatsUrl else timeTrialUrl)?.takeIf { it.isNotBlank() }

    /** Primary first, so a correctly marked round costs exactly one request. */
    val candidateUrls: List<String> get() = listOfNotNull(resultsUrl, alternateResultsUrl)
}
