package de.lambda9.ready2race.backend.app.club.entity

enum class ClubNameRuleKind {
    /** [ClubNameRule.term] wird durch [ClubNameRule.replacement] ersetzt: `Ruderverein` → `RV`. */
    ABBREVIATION,

    /** [ClubNameRule.term] fällt weg: `e.V.`. */
    REMOVE_TERM,

    /** Gründungsjahre: `von 1889`, `v. 1899`, nachgestellte Jahreszahl. */
    REMOVE_YEARS,

    /** Klammerzusätze mit Zahl: `(1879/83)`. */
    REMOVE_BRACKETED,
    ;

    /**
     * Die beiden strukturellen Arten lassen sich nicht als Wort aufschreiben und sind deshalb in
     * der Oberfläche Schalter statt Listeneinträge - eine Zeile ohne `term`, vorhanden heißt aktiv.
     */
    val structural: Boolean get() = this == REMOVE_YEARS || this == REMOVE_BRACKETED
}

/**
 * Eine Regel, nach der ein Vereinsname gekürzt wird - in der Reihenfolge, in der sie greift.
 *
 * Bewusst **keine** regulären Ausdrücke: gepflegt werden Wortpaare und literale Bestandteile. Ein
 * Tippfehler in einem Muster würde die Anzeige aller Vereine zerlegen, ein unglücklich
 * verschachteltes Muster den Server hängen - und die Seite bedient jemand, der eine Regatta
 * organisiert.
 */
data class ClubNameRule(
    val kind: ClubNameRuleKind,
    val term: String?,
    val replacement: String?,
)
