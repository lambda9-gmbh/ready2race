package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Crew steht in Bootsreihenfolge; die Kette hält diese Reihenfolge ein, weil sie sonst je
 * Abruf anders aussähe.
 */
class ClubCompositionTest {

    /**
     * Der Regelfall: 58 der 100 Meldungen der CRF 2026 sind reine Vereinsboote. Für sie darf sich
     * nichts ändern - voll steht der Vereinsname, kurz seine Kurzform, in beiden Fällen ohne
     * Trennzeichen.
     */
    @Test
    fun aSingleClubStandsForItself() {
        val composition = ClubComposition.of(
            listOf("Ruderclub Nürtingen", "Ruderclub Nürtingen", "Ruderclub Nürtingen"),
            emptyMap(),
        )

        assertEquals("Ruderclub Nürtingen", composition.full)
        assertEquals("RC Nürtingen", composition.short)
    }

    /**
     * Der Fall, um den es geht: bisher stand hier für jedes dieser Boote "Renngemeinschaft", und
     * der Schiedsrichter konnte sie nicht auseinanderhalten.
     */
    @Test
    fun fiveClubsBecomeAChainInBoatOrder() {
        val composition = ClubComposition.of(
            listOf(
                "Mainzer Ruder-Verein 1878 e.V.",
                "Marburger Ruderverein von 1911 e.V.",
                "Ruderklub Flensburg e.V.",
                "Ruderclub Nürtingen",
                "Erster Kieler Ruder-Club von 1862 e.V.",
            ),
            mapOf(ClubNameKey.of("Erster Kieler Ruder-Club von 1862 e.V.") to "1. KRC"),
        )

        assertEquals(
            "Mainzer Ruder-Verein 1878 e.V. / Marburger Ruderverein von 1911 e.V. / " +
                "Ruderklub Flensburg e.V. / Ruderclub Nürtingen / Erster Kieler Ruder-Club von 1862 e.V.",
            composition.full,
        )
        assertEquals(
            "Mainzer RV / Marburger RV / RK Flensburg / RC Nürtingen / 1. KRC",
            composition.short,
        )
    }

    /** Zwei Personen aus demselben Verein ergeben ein Glied, nicht zwei. */
    @Test
    fun theSameClubAppearsOnlyOnce() {
        val composition = ClubComposition.of(
            listOf("Rostocker Ruderclub", "Ruderclub Nürtingen", "Rostocker Ruderclub"),
            emptyMap(),
        )

        assertEquals("Rostocker Ruderclub / Ruderclub Nürtingen", composition.full)
        assertEquals("Rostocker RC / RC Nürtingen", composition.short)
    }

    /**
     * Derselbe Verein, von zwei Personen verschieden geschrieben: für die Kette ist das ein Verein.
     * Angezeigt wird die Schreibweise, die im Boot zuerst vorkommt.
     */
    @Test
    fun twoSpellingsOfTheSameClubCollapse() {
        val composition = ClubComposition.of(
            listOf("Rostocker Ruderclub", "Rostocker Ruder-Club von 1885 e.V."),
            emptyMap(),
        )

        assertEquals("Rostocker Ruderclub", composition.full)
        assertEquals("Rostocker RC", composition.short)
    }

    /**
     * Eine Abkürzung erkennt die Normalisierung nicht (siehe [ClubNameKeyTest]) - deshalb stehen
     * beide Schreibweisen in der vollen Kette. In der Kurzform fallen sie zusammen, sobald die
     * Pflegeseite ihnen dieselbe Kurzform gegeben hat. Genau dafür ist sie da.
     */
    @Test
    fun aMaintainedShortNameMergesWhatTheKeyCannot() {
        val composition = ClubComposition.of(
            listOf("ARV Kiel", "Akademischer Ruderverein Kiel e.V."),
            mapOf(
                ClubNameKey.of("ARV Kiel") to "ARV Kiel",
                ClubNameKey.of("Akademischer Ruderverein Kiel e.V.") to "ARV Kiel",
            ),
        )

        assertEquals("ARV Kiel / Akademischer Ruderverein Kiel e.V.", composition.full)
        assertEquals("ARV Kiel", composition.short)
    }

    /**
     * Personen ohne Verein und der Platzhalter "N.N." (steht so in den echten Meldedaten) fallen
     * still raus - sonst klaffte in der Kette eine leere Stelle oder ein " / / ".
     */
    @Test
    fun peopleWithoutAClubAndPlaceholdersDropOut() {
        val composition = ClubComposition.of(
            listOf(null, "Ruderclub Nürtingen", "   ", "N.N.", "n. n.", "Rostocker Ruderclub"),
            emptyMap(),
        )

        assertEquals("Ruderclub Nürtingen / Rostocker Ruderclub", composition.full)
        assertEquals("RC Nürtingen / Rostocker RC", composition.short)
    }

    /**
     * Die Regel, aus der jede Kette gebaut wird. Sie steht hier und nicht in den Abfragen, weil
     * jede Anzeige sie sonst einzeln richtig treffen müsste - und weil in den Abfragen der
     * *meldende* Verein danebensteht und sich anbietet.
     */
    @Test
    fun aGuestRowerWearsTheirFreeTextClub() {
        assertEquals(
            "Marburger Ruderverein von 1911 e.V.",
            ClubComposition.clubWorn(
                external = true,
                externalClubName = "Marburger Ruderverein von 1911 e.V.",
                ownClubName = "Erster Kieler Ruder-Club von 1862 e.V.",
            ),
        )
    }

    /**
     * Bei einem Mitglied zählt der eigene Verein - auch wenn an der Person noch ein alter
     * Freitext hängt, und ausdrücklich nie der Verein, der die Mannschaft gemeldet hat.
     */
    @Test
    fun aMemberWearsTheirOwnClub() {
        assertEquals(
            "Ruderclub Nürtingen",
            ClubComposition.clubWorn(
                external = false,
                externalClubName = "irgendwas Altes",
                ownClubName = "Ruderclub Nürtingen",
            ),
        )
        // `external` ist in der Datenbank nullable; ohne Kennzeichen gilt "Mitglied".
        assertEquals(
            "Ruderclub Nürtingen",
            ClubComposition.clubWorn(external = null, externalClubName = null, ownClubName = "Ruderclub Nürtingen"),
        )
    }

    /**
     * Ein Boot, dessen Crew noch gar nicht steht, liefert eine leere Zeile - was die Anzeige damit
     * macht, entscheidet die Anzeige, nicht dieser Baustein.
     */
    @Test
    fun aCrewWithoutAnyClubYieldsAnEmptyChain() {
        val composition = ClubComposition.of(listOf(null, "N.N."), emptyMap())

        assertEquals("", composition.full)
        assertEquals("", composition.short)
    }
}
