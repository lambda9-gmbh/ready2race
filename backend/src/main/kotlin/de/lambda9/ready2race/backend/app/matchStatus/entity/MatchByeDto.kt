package de.lambda9.ready2race.backend.app.matchStatus.entity

/**
 * Warum ein Lauf ein Freilos ist - so weit es sich aus den Daten belegen lässt.
 *
 * [DEREGISTRATION] wird nur vergeben, wenn eine der nicht fahrenden Zeilen des Laufs einen
 * Abmelde-Datensatz trägt. `competition_deregistration` hat einen Unique-Index auf
 * `competition_registration`: eine Meldung ist entweder abgemeldet oder nicht, unabhängig davon, in
 * welcher Runde das geschah. Deshalb greift die Prüfung auch für eine Zeile, die nur als `out` aus
 * einer früheren Runde mitgeführt wird - und genau die ist im Betrieb der häufige Fall.
 *
 * [NO_OPPONENT] ist der neutrale Fallback für alles andere: es wurde von vornherein nur ein Boot in
 * diesen Lauf gesetzt, oder die Gegnerzeile ist ausgeschieden bzw. nicht weitergekommen. Ohne
 * Abmelde-Datensatz behauptet die Anzeige keine Abmeldung.
 */
enum class MatchByeCause { DEREGISTRATION, NO_OPPONENT }

/**
 * Das Freilos eines Laufs. Reine Anzeige: an der Lauf-Kette, an der Ergebnissperre und am
 * automatischen ersten Platz ändert dieser Datensatz nichts.
 *
 * Ausnahme [mustRace]: Das Flag (competition_match.bye_must_race) macht den Lauf operativ zu einem
 * echten Rennen - Ergebnissperre, Folgerunden-Automatik und Startlisten-Export behandeln ihn dann
 * wie jeden Lauf. Er bleibt trotzdem ein Freilos (dieses DTO bleibt gesetzt): Das Weiterkommen
 * hängt nicht an der gemessenen Zeit, sie läuft "außer Konkurrenz".
 */
data class MatchByeDto(
    val cause: MatchByeCause,
    /**
     * Die abgemeldeten Mannschaften, bei mehreren mit Komma verbunden - null bei
     * [MatchByeCause.NO_OPPONENT].
     */
    val teamName: String?,
    /**
     * Der gespeicherte Abmeldegrund - nur, wenn genau eine Zeile abgemeldet ist. Bei mehreren wäre
     * die Zuordnung Name -> Grund geraten, und geraten wird hier nichts.
     */
    val reason: String?,
    /** Muss trotz Freilos gefahren werden - die Zeit wird genommen und "außer Konkurrenz" gezeigt. */
    val mustRace: Boolean = false,
    /**
     * Die Setzungszahl der fahrenden Mannschaft - "Freilos 1" ist das Freilos des Bootes, das als
     * Erstes ins Ziel kam und deshalb weiterkommt. Sie ist der `seed` des Setup-Platzes
     * (competition_setup_participant), den die Mannschaft in diesem Lauf belegt (siehe
     * [MatchByeRepo.getByeInputs]); null, wenn kein Setup-Platz zur Startnummer passt - etwa bei
     * Erstrunden-Freilosen durch Abmeldung oder nachträglich umgetragenen Startnummern. Dann
     * bleibt es beim nackten "Freilos".
     */
    val seed: Int? = null,
)

/**
 * Eine Team-Zeile des Laufs, so weit die Freilos-Frage sie braucht. Bewusst ein eigener, minimaler
 * Typ statt eines der großen Team-DTOs - dasselbe Muster wie [MatchStatusTeam]: die Ableitung soll
 * ohne Datenbank und ohne Ansichtskontext prüfbar bleiben.
 */
data class MatchByeTeam(
    /** Fährt in diesem Lauf, ist also nicht als `out` aus einer früheren Runde mitgeführt. */
    val racing: Boolean,
    /** Anzeigename der Mannschaft - Verein, dahinter der Meldungsname, falls vorhanden. */
    val name: String,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    /**
     * Der `seed` des Setup-Platzes, den die Mannschaft belegt (siehe [MatchByeDto.seed]) -
     * null, wenn keiner zur Startnummer passt.
     */
    val seed: Int? = null,
)
