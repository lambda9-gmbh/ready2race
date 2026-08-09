package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameLogic
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Heuristik ist die 1:1 nach Kotlin gezogene `shortClubName` aus
 * `frontend/src/components/event/liveDashboard/common.ts`. Sie darf sich beim Umzug nicht
 * verändert haben - was die Schiedsrichter auf dem Board lesen, ist seit der Regatta-Vorbereitung
 * eingeübt.
 *
 * Alle Namen stammen aus dem Produktivstand der CRF 2026.
 */
class ClubShortNameLogicTest {

    @Test
    fun theLegalFormAndFoundingYearFallAway() {
        assertEquals("Rostocker RC", ClubShortNameLogic.heuristic("Rostocker Ruder-Club von 1885 e.V."))
        assertEquals("Erster Kieler RC", ClubShortNameLogic.heuristic("Erster Kieler Ruder-Club von 1862 e.V."))
        assertEquals("Marburger RV", ClubShortNameLogic.heuristic("Marburger Ruderverein von 1911 e. V."))
        assertEquals("Mainzer RV", ClubShortNameLogic.heuristic("Mainzer Ruder-Verein 1878 e.V."))
        assertEquals("RG München", ClubShortNameLogic.heuristic("Rudergesellschaft München 1972 e.V."))
    }

    /** Gründungsjahre stehen auch in Klammern, mitunter als Spanne. */
    @Test
    fun bracketedFoundingYearsFallAway() {
        assertEquals("Bremer RC HANSA", ClubShortNameLogic.heuristic("Bremer Ruder-Club HANSA (1879/83) e.V."))
    }

    @Test
    fun theCommonClubTypesAreAbbreviated() {
        assertEquals("RK Flensburg", ClubShortNameLogic.heuristic("Ruderklub Flensburg e.V."))
        assertEquals("RC Nürtingen", ClubShortNameLogic.heuristic("Ruderclub Nürtingen"))
        assertEquals("Neusser RV", ClubShortNameLogic.heuristic("Neusser Ruderverein e.V."))
        assertEquals("Lübecker RG", ClubShortNameLogic.heuristic("Lübecker Rudergesellschaft"))
        assertEquals("Akad. RV Kiel", ClubShortNameLogic.heuristic("Akademischer Ruderverein Kiel e.V."))
    }

    /**
     * Die Rechtsform mitten im Namen, und die längere Form gewinnt vor der kürzeren:
     * "Sportvereinigung" wird SVg, nicht "SVereinigung".
     */
    @Test
    fun theLongerClubTypeWinsOverTheShorterOne() {
        assertEquals(
            "SVg Scharnebeck Ruderabteilung",
            ClubShortNameLogic.heuristic("Sportvereinigung Scharnebeck e.V. Ruderabteilung"),
        )
        assertEquals("RVg Bille", ClubShortNameLogic.heuristic("Rudervereinigung Bille"))
    }

    /** Nach dem Streichen einer Jahreszahl darf kein Leerzeichen vor dem Komma stehenbleiben. */
    @Test
    fun whitespaceLeftOverFromRemovalIsCleanedUp() {
        assertEquals(
            "Kitzinger RV, Abteilung Rudern",
            ClubShortNameLogic.heuristic("Kitzinger Ruderverein 1897, Abteilung Rudern"),
        )
        assertEquals("Waginger RV", ClubShortNameLogic.heuristic("  Waginger Ruderverein e.V.  "))
    }

    /**
     * Zwei bekannte Lücken der Heuristik, hier festgehalten statt stillschweigend mitgeschleppt:
     * der Bindestrich in "Ruder-Gesellschaft" steht nicht in der Abkürzungsliste, und die verkürzte
     * Jahresangabe "v. 1896" kennt nur [ClubNameKey], nicht die Heuristik.
     *
     * Beides ist bewusst so aus dem Frontend übernommen. Wer es verbessert, ändert damit die
     * Vorbelegung der Pflegeseite - und sollte hier ansetzen, nicht an einer der Anzeigen.
     */
    @Test
    fun theKnownGapsOfTheHeuristicAreWrittenDown() {
        assertEquals(
            "Mülheimer Ruder-Gesellschaft",
            ClubShortNameLogic.heuristic("Mülheimer Ruder-Gesellschaft e.V."),
        )
        assertEquals("RVg „Bille“ v.", ClubShortNameLogic.heuristic("Ruder-Vereinigung „Bille“ v. 1896 e.V."))
    }

    /** Eine gepflegte Kurzform schlägt die Heuristik - genau dafür gibt es die Tabelle. */
    @Test
    fun aMaintainedShortNameBeatsTheHeuristic() {
        val name = "Erster Kieler Ruder-Club von 1862 e.V."
        val aliases = mapOf(ClubNameKey.of(name) to "1. KRC")

        assertEquals("Erster Kieler RC", ClubShortNameLogic.heuristic(name))
        assertEquals("1. KRC", ClubShortNameLogic.shorten(name, aliases))
    }

    /**
     * Der gepflegte Eintrag greift für jede Schreibweise desselben Schlüssels - gepflegt wird
     * einmal, nicht je Variante.
     */
    @Test
    fun aMaintainedShortNameCoversEverySpellingOfItsKey() {
        val aliases = mapOf(ClubNameKey.of("Rostocker Ruderclub") to "Rostocker RC")

        assertEquals("Rostocker RC", ClubShortNameLogic.shorten("Rostocker Ruder-Club von 1885 e.V.", aliases))
    }

    @Test
    fun anUnknownNameFallsBackToTheHeuristic() {
        assertEquals("Itzehoer RC", ClubShortNameLogic.shorten("Itzehoer Ruderclub", emptyMap()))
    }
}
