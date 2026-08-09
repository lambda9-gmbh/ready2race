package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRule
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind

/**
 * Die Regeln, die eine Ruder-Installation hat.
 *
 * [shipped] ist, was die Migration `V202608091300` mitbringt - das Sportartübergreifende.
 * [rowing] kommt aus `docs/seeds/seed-club-name-rules-rowing.sql`, der Datei für Installationen,
 * die die Kürzel bisher aus dem Code bekommen haben.
 *
 * Beides steht hier noch einmal, damit die Heuristik ohne Datenbank prüfbar bleibt - und damit
 * eine Abweichung zwischen Seed und Erwartung an dieser einen Stelle auffällt statt am Regattatag.
 */
object ClubNameRuleFixtures {

    private fun abbreviation(term: String, replacement: String) =
        ClubNameRule(ClubNameRuleKind.ABBREVIATION, term, replacement)

    private fun removeTerm(term: String) = ClubNameRule(ClubNameRuleKind.REMOVE_TERM, term, null)

    val shipped: List<ClubNameRule> = listOf(
        removeTerm("e.V."),
        removeTerm("e. V."),
        removeTerm("eV"),
        ClubNameRule(ClubNameRuleKind.REMOVE_BRACKETED, null, null),
        ClubNameRule(ClubNameRuleKind.REMOVE_YEARS, null, null),
    )

    val rowing: List<ClubNameRule> = shipped + listOf(
        abbreviation("Rudergesellschaft", "RG"),
        abbreviation("Ruder-Gesellschaft", "RG"),
        abbreviation("Rudervereinigung", "RVg"),
        abbreviation("Ruder-Vereinigung", "RVg"),
        abbreviation("Ruderverein", "RV"),
        abbreviation("Ruder-Verein", "RV"),
        abbreviation("Ruderclub", "RC"),
        abbreviation("Ruder-Club", "RC"),
        abbreviation("Ruderklub", "RK"),
        abbreviation("Ruder-Klub", "RK"),
        abbreviation("Segelverein", "SV"),
        abbreviation("Segel-Verein", "SV"),
        abbreviation("Segelclub", "SC"),
        abbreviation("Segel-Club", "SC"),
        abbreviation("Sportvereinigung", "SVg"),
        abbreviation("Sportverein", "SV"),
        abbreviation("Turnverein", "TV"),
        abbreviation("Akademischer", "Akad."),
    )

    fun rowingSettings(aliases: Map<String, String> = emptyMap()) =
        ClubShortNameSettings(aliases = aliases, rules = rowing)
}
