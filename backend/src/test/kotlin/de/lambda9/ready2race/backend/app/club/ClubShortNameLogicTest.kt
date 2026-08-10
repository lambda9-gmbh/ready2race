package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameLogic
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRule
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Heuristik ist die 1:1 nach Kotlin gezogene `shortClubName` aus
 * `frontend/src/components/event/liveDashboard/common.ts`. Was die Schiedsrichter auf dem Board
 * lesen, ist seit der Regatta-Vorbereitung eingeübt und darf sich nicht verändert haben - deshalb
 * laufen die Fälle hier gegen die Regeln, die eine Ruder-Installation nach dem Seed hat
 * ([ClubNameRuleFixtures.rowing]).
 *
 * Alle Namen stammen aus dem Produktivstand der CRF 2026.
 */
class ClubShortNameLogicTest {

    private fun heuristic(name: String) = ClubShortNameLogic.heuristic(name, ClubNameRuleFixtures.rowing)

    @Test
    fun theLegalFormAndFoundingYearFallAway() {
        assertEquals("Rostocker RC", heuristic("Rostocker Ruder-Club von 1885 e.V."))
        assertEquals("Erster Kieler RC", heuristic("Erster Kieler Ruder-Club von 1862 e.V."))
        assertEquals("Marburger RV", heuristic("Marburger Ruderverein von 1911 e. V."))
        assertEquals("Mainzer RV", heuristic("Mainzer Ruder-Verein 1878 e.V."))
        assertEquals("RG München", heuristic("Rudergesellschaft München 1972 e.V."))
    }

    /** Gründungsjahre stehen auch in Klammern, mitunter als Spanne. */
    @Test
    fun bracketedFoundingYearsFallAway() {
        assertEquals("Bremer RC HANSA", heuristic("Bremer Ruder-Club HANSA (1879/83) e.V."))
    }

    @Test
    fun theCommonClubTypesAreAbbreviated() {
        assertEquals("RK Flensburg", heuristic("Ruderklub Flensburg e.V."))
        assertEquals("RC Nürtingen", heuristic("Ruderclub Nürtingen"))
        assertEquals("Neusser RV", heuristic("Neusser Ruderverein e.V."))
        assertEquals("Lübecker RG", heuristic("Lübecker Rudergesellschaft"))
        assertEquals("Akad. RV Kiel", heuristic("Akademischer Ruderverein Kiel e.V."))
    }

    /**
     * Die Rechtsform mitten im Namen, und die längere Zusammensetzung bleibt heil:
     * "Sportvereinigung" wird SVg, nicht "SVereinigung".
     */
    @Test
    fun theLongerClubTypeWinsOverTheShorterOne() {
        assertEquals(
            "SVg Scharnebeck Ruderabteilung",
            heuristic("Sportvereinigung Scharnebeck e.V. Ruderabteilung"),
        )
        assertEquals("RVg Bille", heuristic("Rudervereinigung Bille"))
    }

    /** Nach dem Streichen einer Jahreszahl darf kein Leerzeichen vor dem Komma stehenbleiben. */
    @Test
    fun whitespaceLeftOverFromRemovalIsCleanedUp() {
        assertEquals("Kitzinger RV, Abteilung Rudern", heuristic("Kitzinger Ruderverein 1897, Abteilung Rudern"))
        assertEquals("Waginger RV", heuristic("  Waginger Ruderverein e.V.  "))
    }

    /**
     * Zwei Fälle, an denen die alten regulären Ausdrücke vorbeigingen: dem Abkürzungsmuster fehlte
     * das optionale Trennzeichen, und die verkürzte Jahresangabe "v. 1896" ließ ein einsames "v."
     * stehen. Beides waren Fehler, keine Absicht - literale Zeilen und die strukturelle Regel
     * decken sie jetzt ab.
     */
    @Test
    fun theGapsOfTheOldPatternsAreClosed() {
        assertEquals("Mülheimer RG", heuristic("Mülheimer Ruder-Gesellschaft e.V."))
        assertEquals("RVg „Bille“", heuristic("Ruder-Vereinigung „Bille“ v. 1896 e.V."))
    }

    /** Eine gepflegte Kurzform schlägt die Heuristik - genau dafür gibt es die Tabelle. */
    @Test
    fun aMaintainedShortNameBeatsTheHeuristic() {
        val name = "Erster Kieler Ruder-Club von 1862 e.V."
        val settings = ClubNameRuleFixtures.rowingSettings(mapOf(ClubNameKey.of(name) to "1. KRC"))

        assertEquals("Erster Kieler RC", heuristic(name))
        assertEquals("1. KRC", ClubShortNameLogic.shorten(name, settings))
    }

    /**
     * Der gepflegte Eintrag greift für jede Schreibweise desselben Schlüssels - gepflegt wird
     * einmal, nicht je Variante.
     */
    @Test
    fun aMaintainedShortNameCoversEverySpellingOfItsKey() {
        val settings = ClubNameRuleFixtures.rowingSettings(
            mapOf(ClubNameKey.of("Rostocker Ruderclub") to "Rostocker RC")
        )

        assertEquals("Rostocker RC", ClubShortNameLogic.shorten("Rostocker Ruder-Club von 1885 e.V.", settings))
    }

    @Test
    fun anUnknownNameFallsBackToTheHeuristic() {
        assertEquals("Itzehoer RC", ClubShortNameLogic.shorten("Itzehoer Ruderclub", ClubNameRuleFixtures.rowingSettings()))
    }

    /**
     * Der Grund, warum die Regeln überhaupt in die Datenbank gezogen sind: eine Installation ohne
     * Ruder-Seed kürzt nichts, was nach Rudern klingt. Ohne jede Regel bleibt der Name, wie er ist -
     * kein stiller Rest einkompilierten Sportart-Wissens.
     */
    @Test
    fun withoutRulesTheNameStaysAsItIs() {
        assertEquals(
            "Ruderclub Nürtingen e.V.",
            ClubShortNameLogic.heuristic("Ruderclub Nürtingen e.V.", emptyList()),
        )
        assertEquals("Ruderclub Nürtingen e.V.", ClubShortNameSettings.none.shorten("Ruderclub Nürtingen e.V."))
    }

    /** Die mitgelieferten Regeln allein: Rechtsform und Jahre weg, Vereinstyp unangetastet. */
    @Test
    fun theShippedRulesCarryNoSportAtAll() {
        assertEquals(
            "Rostocker Ruder-Club",
            ClubShortNameLogic.heuristic("Rostocker Ruder-Club von 1885 e.V.", ClubNameRuleFixtures.shipped),
        )
    }

    /**
     * Die Reihenfolge ist keine Kosmetik: stünde `Verein` vor `Ruder-Verein`, bliebe aus
     * "Ruder-Verein" ein "Ruder-V" stehen. Genau deshalb hat die Tabelle eine `sort_order` und die
     * Oberfläche Pfeiltasten.
     */
    @Test
    fun theOrderOfTheRulesDecidesTheResult() {
        val general = ClubNameRule(ClubNameRuleKind.ABBREVIATION, "Verein", "V")
        val specific = ClubNameRule(ClubNameRuleKind.ABBREVIATION, "Ruder-Verein", "RV")

        assertEquals("Mainzer RV", ClubShortNameLogic.heuristic("Mainzer Ruder-Verein", listOf(specific, general)))
        assertEquals("Mainzer Ruder-V", ClubShortNameLogic.heuristic("Mainzer Ruder-Verein", listOf(general, specific)))
    }

    /**
     * Wortgenau und ohne Rücksicht auf Groß-/Kleinschreibung - das ist die ganze Ausdruckskraft,
     * die die Oberfläche braucht. Ein Bestandteil mitten in einem Wort bleibt unberührt, sonst
     * würde aus "Rudervereinigung" ein "RVigung".
     */
    @Test
    fun aTermMatchesWholeWordsInAnyCase() {
        val rules = listOf(ClubNameRule(ClubNameRuleKind.ABBREVIATION, "Ruderverein", "RV"))

        assertEquals("Neusser RV", ClubShortNameLogic.heuristic("Neusser RUDERVEREIN", rules))
        assertEquals("Rudervereinigung Bille", ClubShortNameLogic.heuristic("Rudervereinigung Bille", rules))
    }
}
