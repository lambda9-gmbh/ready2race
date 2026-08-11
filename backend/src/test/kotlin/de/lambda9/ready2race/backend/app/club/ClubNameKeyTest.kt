package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Alle Namenspaare stammen aus dem Produktivstand der CRF 2026 - dort steht derselbe Verein
 * mehrfach verschieden geschrieben an den Personen, weil der Verein eines Gastruderers Freitext
 * ist.
 */
class ClubNameKeyTest {

    @Test
    fun spellingsThatDifferOnlyInBallastShareAKey() {
        assertEquals(
            ClubNameKey.of("Rostocker Ruderclub"),
            ClubNameKey.of("Rostocker Ruder-Club von 1885 e.V."),
        )
        assertEquals(
            ClubNameKey.of("Pirnaer Ruderverein"),
            ClubNameKey.of("Pirnaer Ruderverein 1872 e.V."),
        )
        assertEquals(
            ClubNameKey.of("Kölner Ruderverein von 1877"),
            ClubNameKey.of("Kölner Ruderverein von 1877 e.V."),
        )
        // Die Rechtsform mitten im Namen, nicht nur am Ende.
        assertEquals(
            ClubNameKey.of("RG Wiking Berlin"),
            ClubNameKey.of("RG Wiking e.V. Berlin"),
        )
    }

    /** "e.V.", "e. V." und ein abgeschnittenes Leerzeichen am Ende sind derselbe Verein. */
    @Test
    fun theLegalFormIsRemovedInAllItsSpellings() {
        val expected = ClubNameKey.of("Marburger Ruderverein von 1911")

        assertEquals(expected, ClubNameKey.of("Marburger Ruderverein von 1911 e.V."))
        assertEquals(expected, ClubNameKey.of("Marburger Ruderverein von 1911 e. V."))
        assertEquals(expected, ClubNameKey.of("Marburger Ruderverein von 1911 e. V. "))
        assertEquals(expected, ClubNameKey.of("Marburger Ruderverein von 1911 eV"))
    }

    /**
     * "v. 1866" ist die verkürzte Schreibweise von "von 1866"; die Nummerierung in Klammern hängt
     * an den Mannschaften desselben Vereins.
     */
    @Test
    fun shortenedFoundingYearsAndNumberingInBracketsFallAway() {
        val expected = ClubNameKey.of("RC Allemannia Hamburg")

        assertEquals(expected, ClubNameKey.of("RC Allemannia Hamburg v. 1866"))
        assertEquals(expected, ClubNameKey.of("RC Allemannia Hamburg v. 1866 (1)"))
        assertEquals(expected, ClubNameKey.of("RC Allemannia Hamburg v. 1866 (2)"))

        // Gründungsjahre stehen auch als Spanne in der Klammer.
        assertEquals(
            ClubNameKey.of("Bremer Ruder-Club HANSA"),
            ClubNameKey.of("Bremer Ruder-Club HANSA (1879/83) e.V."),
        )
    }

    /**
     * Die bewusste Grenze der Automatik: eine Abkürzung ist keine Schreibvariante, sondern ein
     * anderer Text. Sie zusammenzuziehen ginge nur mit Regeln, die auch verschiedene Vereine
     * verschmelzen. Beide Zeilen führt die Pflegeseite zusammen, indem sie dieselbe Kurzform
     * bekommen - nicht der Schlüssel.
     *
     * Schlägt dieser Test fehl, weil jemand die Normalisierung "schlauer" gemacht hat, ist das der
     * Moment, in dem geprüft werden muss, welche fremden Vereine dabei ebenfalls verschmelzen.
     */
    @Test
    fun abbreviationsAreDeliberatelyNotResolved() {
        assertNotEquals(
            ClubNameKey.of("ARV Kiel"),
            ClubNameKey.of("Akademischer Ruderverein Kiel e.V."),
        )
        assertNotEquals(
            ClubNameKey.of("RC Bergedorf"),
            ClubNameKey.of("Ruderclub Bergedorf e.V."),
        )
    }

    /** Verschiedene Vereine bleiben verschieden, auch wenn sie sich ähneln. */
    @Test
    fun differentClubsKeepDifferentKeys() {
        assertNotEquals(
            ClubNameKey.of("Lübecker Rudergesellschaft"),
            ClubNameKey.of("Ruderklub Flensburg e.V."),
        )
        assertNotEquals(
            ClubNameKey.of("Ruderunion Arkona"),
            ClubNameKey.of("Ruder-Union Arkona Berlin - 1879 - e.V."),
        )
    }

    /**
     * Umlaute kommen aus Formular, CSV-Import und Feed unterschiedlich zerlegt an. Ohne die
     * NFC-Normalisierung stünde derselbe Verein zweimal in der Pflegeliste, ohne dass man den
     * Unterschied sähe.
     */
    @Test
    fun decomposedUmlautsLandOnTheSameKey() {
        val composed = "Kölner Ruderverein"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)

        assertNotEquals(composed, decomposed, "Die Testdaten müssen sich in der Zerlegung unterscheiden")
        assertEquals(ClubNameKey.of(composed), ClubNameKey.of(decomposed))
    }

    /** Der Schlüssel ist reiner Kleinbuchstaben- und Ziffernrest - kein Trennzeichen bleibt übrig. */
    @Test
    fun theKeyKeepsOnlyLettersAndDigits() {
        assertEquals("rostockerruderclub", ClubNameKey.of("Rostocker Ruder-Club von 1885 e.V."))
        assertEquals("1kieleryachtclub", ClubNameKey.of("1. Kieler Yacht-Club"))
    }
}
