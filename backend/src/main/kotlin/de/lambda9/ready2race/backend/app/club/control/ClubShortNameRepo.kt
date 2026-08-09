package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB_SHORT_NAME
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

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
}
