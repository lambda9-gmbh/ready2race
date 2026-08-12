package de.lambda9.ready2race.backend.app.participantTracking.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantTrackingView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingChangeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object ParticipantTrackingRepo {

    private fun ParticipantTrackingView.searchFields() = listOf(FIRSTNAME, LASTNAME, CLUB_NAME, EXTERNAL_CLUB_NAME)


    fun insert(record: ParticipantTrackingRecord) = PARTICIPANT_TRACKING.insertReturning(record) { ID }

    fun get(participantId: UUID, eventId: UUID) = PARTICIPANT_TRACKING_VIEW.select {
        PARTICIPANT_ID.eq(participantId).and(
            EVENT_ID.eq(eventId)
        )
    }

    /**
     * Der Rohsatz eines Eintrags. Die Korrektur schreibt auf die Tabelle, nicht auf die Sicht -
     * und braucht dafür auch `participant` und `event`, um zu prüfen, ob der Eintrag überhaupt zu
     * der Person und der Veranstaltung im Pfad gehört.
     */
    fun findById(trackingId: UUID) = PARTICIPANT_TRACKING.selectOne { ID.eq(trackingId) }

    fun update(trackingId: UUID, f: ParticipantTrackingRecord.() -> Unit) =
        PARTICIPANT_TRACKING.update(f) { ID.eq(trackingId) }

    fun insertChange(record: ParticipantTrackingChangeRecord) =
        PARTICIPANT_TRACKING_CHANGE.insertReturning(record) { ID }

    /** Die Änderungsspur einer Person, jüngste zuerst - so liest der Dialog sie auch. */
    fun changes(participantId: UUID, eventId: UUID) = Jooq.query {
        with(PARTICIPANT_TRACKING_CHANGE_VIEW) {
            selectFrom(this)
                .where(PARTICIPANT_ID.eq(participantId).and(EVENT_ID.eq(eventId)))
                .orderBy(CREATED_AT.desc())
                .fetch()
        }
    }

    /**
     * Alle Scans der Veranstaltung (Live-Dashboard: "ist das Boot in der Arena?") - der
     * Aufrufer reduziert auf den letzten Scan je Person.
     */
    fun getScansByEvent(eventId: UUID) = Jooq.query {
        with(PARTICIPANT_TRACKING) {
            select(PARTICIPANT, SCAN_TYPE, SCANNED_AT)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch()
        }
    }

    fun count(
        search: String?,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        onlyLatest: Boolean,
        scanType: ParticipantScanType?,
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_TRACKING_VIEW) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(EVENT_ID.eq(eventId))
                    .and(filterScopeForView(scope, user.club))
                    .and(filterOnlyLatest(onlyLatest))
                    .and(filterScanType(scanType))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantTrackingSort>,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        onlyLatest: Boolean,
        scanType: ParticipantScanType?,
    ): JIO<List<ParticipantTrackingViewRecord>> = Jooq.query {
        with(PARTICIPANT_TRACKING_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        EVENT_ID.eq(eventId)
                            .and(filterScopeForView(scope, user.club))
                            .and(filterOnlyLatest(onlyLatest))
                            .and(filterScanType(scanType))
                    )
                }
                .fetch()
        }
    }

    /**
     * Reduziert das Protokoll auf das jüngste Ereignis je Person: eine Zeile bleibt stehen, wenn
     * dieselbe Person bei derselben Veranstaltung keinen späteren Scan hat. Als Anti-Join gegen die
     * Roh-Tabelle formuliert (die Entsprechung eines `distinct on (participant) ... order by
     * scanned_at desc`), damit Suche, Sortierung, Seitengrenzen und der Gesamtzähler unverändert
     * über die bestehende Bedingung laufen.
     *
     * Entscheidend für die Kombination mit [filterScanType]: der Teilnehmervergleich hier kennt den
     * Status-Filter bewusst NICHT. Erst wird je Person das jüngste Ereignis bestimmt, dann wird
     * dessen Status gefiltert. Andersherum (erst nach EXIT filtern, dann das jüngste EXIT nehmen)
     * fände "aufs Wasser abgemeldet und noch nicht zurück" auch alle, die längst wieder angemeldet
     * sind - deren letztes Ereignis ist ein ENTRY, aber ihr letztes EXIT existiert trotzdem.
     */
    private fun filterOnlyLatest(onlyLatest: Boolean): Condition =
        if (!onlyLatest) {
            DSL.trueCondition()
        } else {
            val later = PARTICIPANT_TRACKING.`as`("pt_later")
            DSL.notExists(
                DSL.selectOne()
                    .from(later)
                    .where(
                        later.PARTICIPANT.eq(PARTICIPANT_TRACKING_VIEW.PARTICIPANT_ID)
                            .and(later.EVENT.eq(PARTICIPANT_TRACKING_VIEW.EVENT_ID))
                            .and(
                                later.SCANNED_AT.gt(PARTICIPANT_TRACKING_VIEW.SCANNED_AT)
                                    // Zeitgleiche Einträge je Person verhindert die Sequenzprüfung;
                                    // falls doch welche existieren, entscheidet die ID, damit
                                    // deterministisch genau eine Zeile übrig bleibt.
                                    .or(
                                        later.SCANNED_AT.eq(PARTICIPANT_TRACKING_VIEW.SCANNED_AT)
                                            .and(later.ID.gt(PARTICIPANT_TRACKING_VIEW.ID))
                                    )
                            )
                    )
            )
        }

    private fun filterScanType(scanType: ParticipantScanType?): Condition =
        if (scanType == null) DSL.trueCondition()
        else PARTICIPANT_TRACKING_VIEW.SCAN_TYPE.eq(scanType.name)


    private fun filterScopeForView(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition =
        if (scope == Privilege.Scope.OWN) PARTICIPANT_TRACKING_VIEW.CLUB_ID.eq(clubId) else DSL.trueCondition()
}