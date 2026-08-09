package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.club.entity.ClubNameRule
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind

/**
 * Die Kurzform eines Vereinsnamens.
 *
 * Zwei Quellen, in dieser Reihenfolge: die gepflegte Kurzform aus `club_short_name`, sonst die
 * Heuristik. Die Heuristik lag bis zum 09.08.2026 als `shortClubName` im Frontend
 * (`liveDashboard/common.ts`); sie ist hierher gezogen, weil Schiedsrichter-Board,
 * Athleten-Anzeige und Pflegeseite jetzt dieselbe Regel brauchen - und die Pflegeseite ihr
 * Eingabefeld mit genau dem Wert vorbelegt, den die Heuristik erzeugt.
 *
 * Ihre Regeln stehen seit dem Nachtrag vom 09.08.2026 in `club_name_rule` statt im Code:
 * `Ruderclub → RC` ist Wissen über eine Sportart, nicht über Regattaverwaltung.
 *
 * **Diese Regeln fassen [ClubNameKey] nicht an.** Der Schlüssel beantwortet "ist das derselbe
 * Verein" und ist Primärschlüssel der gepflegten Kurzformen; die Regeln beantworten "wie schreibe
 * ich ihn kurz". Würde eine Regeländerung den Schlüssel verschieben, verlöre jeder gepflegte
 * Eintrag still seine Zuordnung. Die beiden gehören deshalb nicht zusammengelegt, so ähnlich ihre
 * Listen auch aussehen.
 */
object ClubShortNameLogic {

    // Strukturelle Regeln lassen sich nicht als Wort aufschreiben; ihre Muster stehen deshalb hier
    // und nicht in der Tabelle. Was in der Tabelle steht, ist nur die Angabe, ob sie greifen.
    private val BRACKETED_WITH_DIGIT = Regex("""\s*\([^)]*\d[^)]*\)""")

    private val FOUNDING_YEARS = listOf(
        Regex("""\s*\bvon\s+\d{4}\b""", RegexOption.IGNORE_CASE),
        Regex("""\s*\bv\.\s*\d{4}\b""", RegexOption.IGNORE_CASE),
        // Nachgestellte Jahreszahl, z.B. "München 1972". Zuletzt, damit "von 1889" nicht als
        // blanke Jahreszahl behandelt wird und das "von" stehenbleibt.
        Regex("""\s+\d{4}\b"""),
    )

    /**
     * Kurzform eines Vereinsnamens für die Listenansicht. [rules] greifen in ihrer Reihenfolge -
     * die ist inhaltlich: stünde `Verein` vor `Ruderverein`, bliebe aus `Ruder-Verein` ein
     * `Ruder-V` stehen.
     */
    fun heuristic(name: String, rules: List<ClubNameRule>): String {
        val processed = rules.fold(name) { acc, rule ->
            when (rule.kind) {
                ClubNameRuleKind.ABBREVIATION -> replaceTerm(acc, rule.term, rule.replacement ?: "")
                ClubNameRuleKind.REMOVE_TERM -> replaceTerm(acc, rule.term, "")
                ClubNameRuleKind.REMOVE_BRACKETED -> acc.replace(BRACKETED_WITH_DIGIT, " ")
                ClubNameRuleKind.REMOVE_YEARS -> FOUNDING_YEARS.fold(acc) { text, pattern ->
                    text.replace(pattern, " ")
                }
            }
        }

        return processed
            .replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""\s+,"""), ",")
            .trim()
    }

    /**
     * Ersetzt [term] wortgenau und ohne Rücksicht auf Groß-/Kleinschreibung.
     *
     * "Wortgenau" heißt: links und rechts steht kein Buchstabe und keine Ziffer. Damit trifft
     * `Sportverein` nicht die `Sportvereinigung`, und `e.V.` nicht das `eV` mitten in einem Wort -
     * ohne dass jemand dafür einen regulären Ausdruck aufschreiben müsste.
     *
     * Der Preis ist, dass Schreibvarianten eigene Zeilen brauchen (`Ruderclub` *und*
     * `Ruder-Club`). Das ist gewollt: eine Zeile, die man liest, ist besser als ein Muster, das
     * man sich zusammenreimt.
     */
    private fun replaceTerm(text: String, term: String?, replacement: String): String {
        if (term.isNullOrEmpty()) return text

        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val matches = text.regionMatches(index, term, 0, term.length, ignoreCase = true)
                && isBoundary(text.getOrNull(index - 1))
                && isBoundary(text.getOrNull(index + term.length))

            if (matches) {
                result.append(replacement)
                index += term.length
            } else {
                result.append(text[index])
                index++
            }
        }
        return result.toString()
    }

    private fun isBoundary(char: Char?): Boolean = char == null || !char.isLetterOrDigit()

    /**
     * Die anzuzeigende Kurzform: gepflegter Eintrag vor Heuristik. [settings] trägt beides und
     * wird einmal je Abruf geladen, nicht je Verein.
     */
    fun shorten(name: String, settings: ClubShortNameSettings): String =
        settings.aliases[ClubNameKey.of(name)] ?: heuristic(name, settings.rules)
}
