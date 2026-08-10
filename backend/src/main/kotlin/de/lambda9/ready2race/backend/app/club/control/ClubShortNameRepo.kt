package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB_SHORT_NAME
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ClubShortNameRepo {

    /**
     * Alle gepflegten Kurzformen auf einmal. Die Tabelle ist so groß wie die Zahl der
     * Vereinsschreibweisen im System (Größenordnung: Dutzende) - jede Anzeige zieht sie einmal und
     * löst danach ohne weitere Abfrage auf, statt je Boot nachzuschlagen.
     */
    fun all(): JIO<List<ClubShortNameRecord>> = Jooq.query {
        selectFrom(CLUB_SHORT_NAME).fetch()
    }

    /** Die Zuordnung, wie [de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameLogic] sie erwartet. */
    fun aliases(): JIO<Map<String, String>> = Jooq.query {
        selectFrom(CLUB_SHORT_NAME).fetch().associate { it.nameKey to it.shortName }
    }

    /**
     * Setzt die Kurzform eines Schlüssels. Beim Überschreiben bleiben `created_at`/`created_by`
     * stehen - wer die Kurzform später ändert, ist nicht der, der sie angelegt hat.
     */
    fun upsert(record: ClubShortNameRecord): JIO<Int> = Jooq.query {
        insertInto(CLUB_SHORT_NAME)
            .set(record)
            .onConflict(CLUB_SHORT_NAME.NAME_KEY)
            .doUpdate()
            .set(CLUB_SHORT_NAME.SAMPLE_NAME, record.sampleName)
            .set(CLUB_SHORT_NAME.SHORT_NAME, record.shortName)
            .set(CLUB_SHORT_NAME.UPDATED_AT, record.updatedAt)
            .set(CLUB_SHORT_NAME.UPDATED_BY, record.updatedBy)
            .execute()
    }

    /** Löschen heißt "zurück zur Heuristik", nicht "keine Kurzform". */
    fun delete(nameKey: String): JIO<Int> = CLUB_SHORT_NAME.delete { NAME_KEY.eq(nameKey) }

    /**
     * Jede Vereinsschreibweise, die im System vorkommt - die Vereins-Datensätze und der Freitext an
     * den Gastruderern in einem Topf.
     *
     * Beide Quellen, weil die Anzeige den Verein zeigt, den die Person *trägt*: von den 46
     * Schreibweisen, die im Produktivstand der CRF 2026 an Personen stehen, ließen sich nur 17
     * einem Vereins-Datensatz zuordnen. Wer nur `club` liest, bekommt die Mehrzahl der Namen nicht
     * zu Gesicht, die auf dem Board erscheinen.
     */
    fun occurringNames(): JIO<List<String>> = Jooq.query {
        val fromClubs = select(CLUB.NAME)
            .from(CLUB)
            .fetch(CLUB.NAME)

        val fromParticipants = select(PARTICIPANT.EXTERNAL_CLUB_NAME)
            .from(PARTICIPANT)
            .where(PARTICIPANT.EXTERNAL_CLUB_NAME.isNotNull)
            .fetch(PARTICIPANT.EXTERNAL_CLUB_NAME)

        (fromClubs + fromParticipants).filterNotNull()
    }

    /**
     * Dasselbe, eingeschränkt auf die Vereine, die in [eventId] tatsächlich am Start sind - vor
     * einer Regatta sollen die paar Dutzend gemeldeten Namen dastehen und nicht der Bestand
     * mehrerer Jahre.
     *
     * Gelesen wird je Person, nicht je Meldung: `PARTICIPANT_FOR_EVENT.CLUB_NAME` wäre der
     * *meldende* Verein, und genau der ist für die Anzeige bedeutungslos.
     */
    fun occurringNamesForEvent(eventId: UUID): JIO<List<String>> = Jooq.query {
        select(PARTICIPANT_FOR_EVENT.EXTERNAL, PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME, CLUB.NAME)
            .from(PARTICIPANT_FOR_EVENT)
            .join(PARTICIPANT).on(PARTICIPANT.ID.eq(PARTICIPANT_FOR_EVENT.ID))
            .join(CLUB).on(CLUB.ID.eq(PARTICIPANT.CLUB))
            .where(PARTICIPANT_FOR_EVENT.EVENT_ID.eq(eventId))
            .fetch { record ->
                if (record[PARTICIPANT_FOR_EVENT.EXTERNAL] == true) {
                    record[PARTICIPANT_FOR_EVENT.EXTERNAL_CLUB_NAME]
                } else {
                    record[CLUB.NAME]
                }
            }
            .filterNotNull()
    }
}
