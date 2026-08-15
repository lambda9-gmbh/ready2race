package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CHECKED_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ParticipantHasRequirementForEventRepo {

    fun create(record: ParticipantHasRequirementForEventRecord) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.insert(record)

    /**
     * Schreibt genau EINE Erfüllung, ohne fremde Zeilen anzufassen - der Weg der Scan-App.
     *
     * Trifft die Zeile auf eine bereits vorhandene mit denselben Dimensionen (der eindeutige
     * Index aus V202608141900, `nulls not distinct`), wird nicht gescheitert, sondern nur die
     * Notiz nachgezogen: ein Doppel-Scan derselben Person ist Alltag an der Waage und darf
     * weder einen Fehler werfen noch etwas löschen. `coalesce` hält dabei eine bestehende
     * Notiz fest, wenn der neue Scan keine mitbringt - sonst radierte der zweite Scan die
     * Anmerkung des ersten aus. `created_at`/`created_by` bleiben beim Überschreiben stehen,
     * wie beim Upsert in `ClubShortNameRepo`: es zählt, wer zuerst bestätigt hat.
     */
    fun upsertFulfillment(record: ParticipantHasRequirementForEventRecord) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            insertInto(this)
                .set(record)
                .onConflict(PARTICIPANT, EVENT, PARTICIPANT_REQUIREMENT, EVENT_DAY, COMPETITION)
                .doUpdate()
                .set(NOTE, DSL.coalesce(DSL.excluded(NOTE), NOTE))
                .execute()
        }
    }

    /**
     * Nimmt die Bestätigung EINER Person zurück - das Gegenstück zu [upsertFulfillment] und
     * ebenfalls nur für diese eine Person.
     *
     * Gelöscht wird nicht die exakte Dimensionszeile, sondern alles, was den übergebenen
     * Bezugsrahmen im Sinne von `RequirementScopeLogic.covers` abdeckt: Verglichen wird nur,
     * was der jeweilige Schalter verlangt. Bei einer veranstaltungsweiten Bedingung fallen so
     * auch die Zeilen aus der Bestandsmigration (Tag = erster Wettkampftag) mit - eine exakte
     * null/null-Löschung verfehlte sie, und die Bestätigung bliebe unwiderruflich stehen. Bei
     * `perEventDay` verschwindet nur der übergebene Tag: die gestrige Waage war gestern
     * gültig und bleibt es.
     */
    fun deleteCovering(
        eventId: UUID,
        participantRequirementId: UUID,
        participantId: UUID,
        perEventDay: Boolean,
        eventDayId: UUID?,
        perCompetition: Boolean,
        competitionId: UUID?,
    ) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.delete {
        DSL.and(
            EVENT.eq(eventId),
            PARTICIPANT_REQUIREMENT.eq(participantRequirementId),
            PARTICIPANT.eq(participantId),
            if (perEventDay) EVENT_DAY.isNotDistinctFrom(eventDayId) else DSL.trueCondition(),
            if (perCompetition) COMPETITION.isNotDistinctFrom(competitionId) else DSL.trueCondition(),
        )
    }

    /**
     * Zieht die Freitext-Notiz einer bestehenden Erfüllung nach.
     *
     * Zwei Dinge sind hier seit der Migration V202608141900 anders, beide mit Absicht:
     *
     * 1. Geschrieben wird direkt per `update`-Anweisung statt über das sonst übliche
     *    Record-Muster. Die Tabelle trägt keinen Primärschlüssel mehr - er kann die nullbaren
     *    Dimensionen nicht aufnehmen -, und ohne Primärschlüssel erzeugt jOOQ kein
     *    `UpdatableRecord`, an dem die Erweiterung `TableImpl.update` hängt.
     * 2. Die Bedingung umfasst weiterhin nur (Person, Veranstaltung, Bedingung) und damit
     *    ausdrücklich alle Dimensionszeilen dieser Kombination. Die Meldestelle hakt über
     *    diesen Weg veranstaltungsweit ab; eine Einschränkung auf Tag oder Wettkampf würde
     *    hier die Zeilen aus der Bestandsmigration (Tag = erster Wettkampftag) verfehlen und
     *    die Notiz stillschweigend verlieren. Die dimensionsscharfe Pflege gehört zu den
     *    Prüf-Oberflächen, die auf [RequirementScopeLogic] aufsetzen.
     */
    fun updateNote(
        participantId: UUID,
        eventId: UUID,
        participantRequirementId: UUID,
        note: String?,
    ) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            update(this)
                .set(NOTE, note)
                .where(
                    DSL.and(
                        PARTICIPANT.eq(participantId),
                        EVENT.eq(eventId),
                        PARTICIPANT_REQUIREMENT.eq(participantRequirementId),
                    )
                )
                .execute()
        }
    }

    fun exists(eventId: UUID, participantRequirementId: UUID, participantId: UUID) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.exists {
            EVENT.eq(eventId).and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .and(PARTICIPANT.eq(participantId))
        }

    fun delete(eventId: UUID, participantRequirementId: UUID, participantId: UUID) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.delete {
            EVENT.eq(eventId)
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId).and(PARTICIPANT.eq(participantId)))
        }

    fun getApprovedParticipantIds(eventId: UUID, participantRequirementId: UUID) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            select(PARTICIPANT)
                .from(this)
                .where(EVENT.eq(eventId))
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .fetchInto(UUID::class.java)
        }
    }

    /**
     * Räumt beim Abgleich einer Bedingung auf: Wer nicht mehr in der Liste steht, verliert seine
     * Bestätigung - aber nur innerhalb des übergebenen Rahmens.
     *
     * Der Rahmen ist der Punkt. Bis zur Migration V202608141900 gab es je Person und Bedingung
     * genau eine Zeile, ein pauschales Löschen war deshalb richtig. Seither kann dieselbe Person
     * je Tag und je Wettkampf eine eigene Bestätigung haben - ein pauschales Löschen nähme dem
     * Abgleich für Wettkampf B die Waage-Bestätigung aus Wettkampf A mit, ohne dass es jemand
     * sähe. Verglichen wird deshalb wie in `RequirementScopeLogic.covers`: nur die Dimension,
     * die der jeweilige Schalter verlangt, und die exakt - bei veranstaltungsweiten Bedingungen
     * fällt so weiterhin alles, auch die tags-gestempelten Zeilen der Bestandsmigration.
     */
    fun deleteCoveringWhereParticipantNotInList(
        eventId: UUID,
        participantRequirementId: UUID,
        approvedParticipants: List<UUID>,
        perEventDay: Boolean,
        eventDayId: UUID?,
        perCompetition: Boolean,
        competitionId: UUID?,
    ) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.delete {
        DSL.and(
            EVENT.eq(eventId),
            PARTICIPANT_REQUIREMENT.eq(participantRequirementId),
            PARTICIPANT.notIn(approvedParticipants),
            if (perEventDay) EVENT_DAY.isNotDistinctFrom(eventDayId) else DSL.trueCondition(),
            if (perCompetition) COMPETITION.isNotDistinctFrom(competitionId) else DSL.trueCondition(),
        )
    }

    /**
     * Setzt die Notiz genau der Zeilen, die den übergebenen Rahmen abdecken - das Gegenstück zu
     * [updateNote], das bewusst alle Dimensionszeilen einer Person trifft.
     *
     * Beide werden gebraucht: Der Abgleich im Verwaltungs-UI pflegt einen bestimmten Wettkampf
     * und darf die Notiz des anderen nicht überschreiben; die veranstaltungsweite Pflege trifft
     * weiterhin alles, sonst verfehlte sie die Bestandszeilen.
     */
    fun updateNoteCovering(
        eventId: UUID,
        participantRequirementId: UUID,
        participantId: UUID,
        note: String?,
        perEventDay: Boolean,
        eventDayId: UUID?,
        perCompetition: Boolean,
        competitionId: UUID?,
    ) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            update(this)
                .set(NOTE, note)
                .where(
                    DSL.and(
                        EVENT.eq(eventId),
                        PARTICIPANT_REQUIREMENT.eq(participantRequirementId),
                        PARTICIPANT.eq(participantId),
                        if (perEventDay) EVENT_DAY.isNotDistinctFrom(eventDayId) else DSL.trueCondition(),
                        if (perCompetition) COMPETITION.isNotDistinctFrom(competitionId) else DSL.trueCondition(),
                    )
                )
                .execute()
        }
    }

    /**
     * Die Erfüllungen einer Person zu einer Veranstaltung samt ihrer Dimensionen (Tag,
     * Wettkampf). Grundlage für die laufbezogene Auswertung in
     * [de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic]:
     * Wer wissen will, ob eine Bedingung *in diesem Lauf* erfüllt ist, braucht die Zeilen
     * selbst und nicht nur die Kennungen der Bedingungen.
     *
     * Die Notiz kommt bewusst mit: Anders als bei den öffentlichen Wegen (siehe
     * `MyEventRepo.findFulfilledRequirementIds`) liest hier nur die Meldestelle mit.
     */
    fun getFulfillments(eventId: UUID, participantId: UUID) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.select {
            DSL.and(
                EVENT.eq(eventId),
                PARTICIPANT.eq(participantId),
            )
        }

    /** Batch-Fassung von [getFulfillments] - alle Personen eines Laufs in einer Abfrage. */
    fun getFulfillmentsForParticipants(eventId: UUID, participantIds: Collection<UUID>) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.select {
            DSL.and(
                EVENT.eq(eventId),
                PARTICIPANT.`in`(participantIds),
            )
        }

    fun getApprovedRequirements(eventId: UUID, participantId: UUID) =
        CHECKED_PARTICIPANT_REQUIREMENT.select {
            DSL.and(
                EVENT.eq(eventId),
                PARTICIPANT.eq(participantId)
            )
        }

}