package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Reine Logik der Frage "gilt diese Bedingung für diese Person **in diesem Lauf** als
 * erfüllt?" - bewusst ohne Datenbank- und Ktor-Bezug, wie [RequirementMatchLogic] daneben und
 * `EventScheduleLogic`.
 *
 * Hintergrund: Bis zur Migration V202608141900 wurde eine Bedingung genau einmal je Person und
 * Veranstaltung abgehakt. Seither trägt die Bedingung zwei Schalter ([Scope]) und die
 * Erfüllung zwei nullbare Dimensionen ([Fulfillment]) - Tag und Wettkampf. Welche davon beim
 * Nachschlagen überhaupt verglichen werden, entscheiden allein die Schalter: steht
 * `perEventDay` auf aus, ist der Tag der gespeicherten Zeile egal.
 */
object RequirementScopeLogic {

    /**
     * Die beiden Schalter einer Bedingung. Vier Kombinationen: beide aus = je Veranstaltung
     * (das Verhalten vor der Migration), nur Tag, nur Wettkampf, beide = je Wettkampf und Tag.
     */
    data class Scope(
        val perEventDay: Boolean,
        val perCompetition: Boolean,
    ) {
        companion object {
            /** Der Vorgabefall und zugleich der Bestand: gilt für die ganze Veranstaltung. */
            val forWholeEvent = Scope(perEventDay = false, perCompetition = false)
        }
    }

    /**
     * Eine gespeicherte Erfüllung, auf ihre Dimensionen reduziert (eine Zeile aus
     * `participant_has_requirement_for_event`). null heißt "ohne diese Einschränkung
     * eingetragen".
     */
    data class Fulfillment(
        val eventDay: UUID?,
        val competition: UUID?,
    )

    /** Der Bezugsrahmen eines Laufs: sein Wettkampf und der Tag, an dem er stattfindet. */
    data class MatchScope(
        val eventDay: UUID?,
        val competition: UUID?,
    )

    /** Ein Wettkampftag, wie ihn [eventDayOf] zum Zuordnen braucht. */
    data class EventDayRef(
        val id: UUID,
        val date: LocalDate,
    )

    /**
     * Die Dimensionen, mit denen eine neue Erfüllung gespeichert werden muss.
     *
     * Ausgeschaltete Schalter schreiben ausdrücklich null, statt den Tag "vorsichtshalber"
     * mitzunehmen: sonst stünde in der Zeile eine Einschränkung, die niemand gewollt hat, und
     * ein späteres Einschalten des Schalters würde alte Erfüllungen plötzlich auf einen Tag
     * festnageln, an dem gar nicht geprüft wurde. Der Bestand aus der Migration ist der
     * umgekehrte, bewusst gewählte Fall - dort ist der Tag bekannt und wird eingetragen.
     */
    fun keyFor(scope: Scope, match: MatchScope): Fulfillment = Fulfillment(
        eventDay = if (scope.perEventDay) match.eventDay else null,
        competition = if (scope.perCompetition) match.competition else null,
    )

    /**
     * Deckt diese eine gespeicherte Zeile den Lauf ab?
     *
     * Verglichen wird nur, was der Schalter verlangt. Steht er an, muss die Dimension exakt
     * stimmen - auch gegen null: Eine Zeile ohne Tag deckt bei `perEventDay` keinen Lauf ab,
     * der an einem Tag stattfindet, und ein Lauf ohne bestimmbaren Tag wird von keiner
     * Tageszeile abgedeckt. Das ist die vorsichtige Richtung: ein Fehlalarm schickt jemanden
     * einmal zu viel zur Meldestelle, die andere Richtung ließe jemanden ohne gültige Prüfung
     * an den Start.
     */
    fun covers(scope: Scope, fulfillment: Fulfillment, match: MatchScope): Boolean =
        (!scope.perEventDay || fulfillment.eventDay == match.eventDay) &&
            (!scope.perCompetition || fulfillment.competition == match.competition)

    /**
     * Gilt die Bedingung im Lauf als erfüllt? [fulfillments] sind alle gespeicherten Zeilen
     * dieser Person zu dieser Bedingung in dieser Veranstaltung; eine passende genügt.
     */
    fun isFulfilled(
        scope: Scope,
        fulfillments: Collection<Fulfillment>,
        match: MatchScope,
    ): Boolean = fulfillments.any { covers(scope, it, match) }

    /**
     * Der Wettkampftag eines Laufs. Ein Wettkampf kann an mehreren Tagen hängen
     * (`event_day_has_competition`), deshalb entscheidet zuerst das Datum der Startzeit; erst
     * wenn das nichts hergibt, greift der Fall "der Wettkampf hat ohnehin nur einen Tag".
     *
     * Tragen mehrere Tage dasselbe Datum - eine Fehlkonfiguration, die niemand beabsichtigt -
     * gewinnt die kleinste Kennung. Dieselbe Regel wie beim Nachtragen der Bestandsdaten in
     * V202608141900, damit beide Seiten denselben Tag meinen. Bleibt der Tag unbestimmbar,
     * kommt null zurück; was das für die Auswertung heißt, steht bei [covers].
     */
    fun eventDayOf(startTime: LocalDateTime?, days: List<EventDayRef>): UUID? {
        val onDate = startTime?.toLocalDate()?.let { date -> days.filter { it.date == date } } ?: emptyList()
        return when {
            // Vergleich über die Textfassung, nicht über UUID.compareTo: Postgres sortiert
            // uuid byteweise, Java vergleicht zwei vorzeichenbehaftete longs - bei gesetztem
            // obersten Bit kämen beide zu verschiedenen "kleinsten" Zeilen.
            onDate.isNotEmpty() -> onDate.minBy { it.id.toString() }.id
            days.size == 1 -> days.single().id
            else -> null
        }
    }

    /**
     * Der Bezugszeitpunkt, gegen den das Erledigungsfenster einer Bedingung rechnet.
     *
     * Bei `perCompetition` ist das der Lauf, um den es geht: Wer an einem Tag in zwei
     * Wettkämpfen startet, bekäme sonst die Grenzen des falschen Rennens angezeigt. Sonst
     * bleibt es beim [fallback] - für "Mein Event" ist das unverändert der nächste künftige
     * Start der Person, für eine tagesbezogene Bedingung reicht der Aufrufer den ersten Start
     * dieses Tages herein. Die Funktion erfindet keinen Bezugspunkt: fehlt der gewählte, gibt
     * es kein Fenster.
     */
    fun referencePoint(
        scope: Scope,
        matchStart: LocalDateTime?,
        fallback: LocalDateTime?,
    ): LocalDateTime? = if (scope.perCompetition) matchStart else fallback

    /**
     * Eine Grenze des Erledigungsfensters: [minutesBefore] Minuten vor [reference]. null,
     * sobald eine der beiden Größen fehlt - ein halbes Fenster gibt es, ein erfundenes nicht.
     *
     * Der Bezugszeitpunkt wird ausdrücklich übergeben statt hier aus "nächster Start der
     * Person" abgeleitet zu werden; genau diese Wahl trifft [referencePoint].
     */
    fun windowBound(reference: LocalDateTime?, minutesBefore: Int?): LocalDateTime? =
        if (reference != null && minutesBefore != null) {
            reference.minusMinutes(minutesBefore.toLong())
        } else {
            null
        }

    /** Beide Grenzen auf einmal - [from] ist die frühere, [until] die spätere. */
    data class CheckWindow(
        val from: LocalDateTime?,
        val until: LocalDateTime?,
    )

    fun window(
        reference: LocalDateTime?,
        earliestMinutesBefore: Int?,
        latestMinutesBefore: Int?,
    ): CheckWindow = CheckWindow(
        from = windowBound(reference, earliestMinutesBefore),
        until = windowBound(reference, latestMinutesBefore),
    )
}
