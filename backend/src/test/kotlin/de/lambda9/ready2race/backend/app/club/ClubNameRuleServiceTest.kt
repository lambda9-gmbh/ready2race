package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameRuleService
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleError
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleOrderRequest
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleRequest
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [ClubNameRuleService] gegen ein echtes Postgres.
 *
 * Geprüft wird, was keine reine Funktion prüfen kann: dass die Migration die richtige Voreinstellung
 * mitbringt (nur Sportartübergreifendes, kein Rudern), dass ein Schalter ein Schalter bleibt und
 * dass die Reihenfolge tatsächlich ankommt - an ihr hängt, ob aus "Ruder-Verein" ein "RV" oder ein
 * "Ruder-V" wird.
 */
class ClubNameRuleServiceTest {

    private fun TestComprehensionScope<JEnv>.adminId(): UUID =
        assertNotNull(!Jooq.query { select(APP_USER.ID).from(APP_USER).limit(1).fetchOne(APP_USER.ID) })

    private fun TestComprehensionScope<JEnv>.rules() = (!ClubNameRuleService.all()).data

    /**
     * Was ready2race ausliefert, darf keine Sportart kennen. Die Rechtsformen und die beiden
     * Schalter gelten überall; `Ruderclub → RC` gehört in eine Seed-Datei, nicht in die Migration.
     */
    @Test
    fun theMigrationShipsNothingSportSpecific() = testComprehension {
        val shipped = rules()

        assertEquals(
            listOf("e.V.", "e. V.", "eV"),
            shipped.filter { it.kind == ClubNameRuleKind.REMOVE_TERM }.map { it.term },
        )
        assertEquals(
            listOf(ClubNameRuleKind.REMOVE_BRACKETED, ClubNameRuleKind.REMOVE_YEARS),
            shipped.filter { it.kind.structural }.map { it.kind },
        )
        assertEquals(emptyList(), shipped.filter { it.kind == ClubNameRuleKind.ABBREVIATION })
    }

    /** Ein Wortpaar wird angelegt und wirkt sofort - der Ladeweg der Anzeigen liest dieselbe Tabelle. */
    @Test
    fun anAddedWordPairShortensImmediately() = testComprehension {
        !ClubNameRuleService.add(
            ClubNameRuleRequest(ClubNameRuleKind.ABBREVIATION, term = "Ruderverein", replacement = "RV"),
            adminId(),
        )

        assertEquals("Neusser RV", (!ClubShortNameSettings.load()).shorten("Neusser Ruderverein e.V."))
    }

    /**
     * Derselbe Bestandteil ein zweites Mal wäre eine Zeile, die nie greift - die erste hat den
     * Bestandteil schon aufgebraucht. Sie stünde als stiller Irrtum in der Liste.
     */
    @Test
    fun theSameTermIsRefusedASecondTime() = testComprehension {
        val request = ClubNameRuleRequest(ClubNameRuleKind.ABBREVIATION, term = "Ruderverein", replacement = "RV")
        !ClubNameRuleService.add(request, adminId())

        assertKIOFails(ClubNameRuleError.DuplicateTerm) {
            // Groß-/Kleinschreibung ist egal, also ist auch die andere Schreibung dieselbe Regel.
            ClubNameRuleService.add(request.copy(term = "ruderverein"), adminId())
        }
    }

    /** Ein Wortpaar ohne Kürzel täte nichts - das ist kein Eintrag, sondern ein halber Gedanke. */
    @Test
    fun aWordPairWithoutItsReplacementIsRefused() = testComprehension {
        assertKIOFails(ClubNameRuleError.TermMissing) {
            ClubNameRuleService.add(
                ClubNameRuleRequest(ClubNameRuleKind.ABBREVIATION, term = "Ruderverein", replacement = " "),
                adminId(),
            )
        }
    }

    /**
     * Die strukturellen Regeln sind Schalter, keine Listeneinträge: zweimal einschalten ist ein
     * doppelter Klick, ausschalten und wieder einschalten muss gehen.
     */
    @Test
    fun aSwitchStaysASwitch() = testComprehension {
        val existing = rules().single { it.kind == ClubNameRuleKind.REMOVE_YEARS }

        !ClubNameRuleService.add(ClubNameRuleRequest(ClubNameRuleKind.REMOVE_YEARS, null, null), adminId())
        assertEquals(1, rules().count { it.kind == ClubNameRuleKind.REMOVE_YEARS })

        !ClubNameRuleService.remove(existing.id)
        assertEquals(0, rules().count { it.kind == ClubNameRuleKind.REMOVE_YEARS })
        // Ausgeschaltet heißt: die Jahreszahl bleibt stehen.
        assertEquals(
            "Rostocker Ruder-Club von 1885",
            (!ClubShortNameSettings.load()).shorten("Rostocker Ruder-Club von 1885 e.V."),
        )

        !ClubNameRuleService.add(ClubNameRuleRequest(ClubNameRuleKind.REMOVE_YEARS, null, null), adminId())
        assertEquals(
            "Rostocker Ruder-Club",
            (!ClubShortNameSettings.load()).shorten("Rostocker Ruder-Club von 1885 e.V."),
        )
    }

    /**
     * Der Fall, für den es die Reihenfolge überhaupt gibt: greift `Verein` vor `Ruder-Verein`,
     * bleibt ein `Ruder-V` stehen. Umsortieren muss das drehen können.
     */
    @Test
    fun theOrderDecidesWhichRuleGetsThereFirst() = testComprehension {
        val general = (!ClubNameRuleService.add(
            ClubNameRuleRequest(ClubNameRuleKind.ABBREVIATION, term = "Verein", replacement = "V"),
            adminId(),
        )).id
        val specific = (!ClubNameRuleService.add(
            ClubNameRuleRequest(ClubNameRuleKind.ABBREVIATION, term = "Ruder-Verein", replacement = "RV"),
            adminId(),
        )).id

        // Angelegt wird hinten angehängt, also greift hier zuerst die allgemeine Regel.
        assertEquals("Mainzer Ruder-V", (!ClubShortNameSettings.load()).shorten("Mainzer Ruder-Verein"))

        val reordered = rules().map { it.id }.filter { it != general && it != specific } + listOf(specific, general)
        !ClubNameRuleService.reorder(ClubNameRuleOrderRequest(reordered), adminId())

        assertEquals("Mainzer RV", (!ClubShortNameSettings.load()).shorten("Mainzer Ruder-Verein"))
        assertEquals(reordered, rules().map { it.id })
    }

    /** Eine gelöschte Regel wirkt nicht mehr, und ein zweites Löschen ist keine Störung. */
    @Test
    fun deletingARuleTakesItOutOfTheChain() = testComprehension {
        val legalForm = rules().single { it.term == "e.V." }

        !ClubNameRuleService.remove(legalForm.id)
        assertEquals(
            "Ruderclub Nürtingen e.V.",
            (!ClubShortNameSettings.load()).shorten("Ruderclub Nürtingen e.V."),
        )

        assertKIOSucceeds { ClubNameRuleService.remove(legalForm.id) }
    }
}
