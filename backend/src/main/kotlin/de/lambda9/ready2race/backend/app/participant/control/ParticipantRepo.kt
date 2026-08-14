package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.app.ratingcategory.entity.AgeRestriction
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_ADDITIONAL_CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.util.*

object ParticipantRepo {

    /**
     * Ab wie vielen Zeichen die vereinsübergreifende Suche überhaupt etwas liefert.
     *
     * Zwei, nicht drei: der Auftraggeber nennt "Bo" als Beispiel, und kurze Nachnamen sind im
     * Rudern nicht selten. Weniger als zwei würde die Suche zur durchblätterbaren Liste aller
     * Personen aller Vereine machen — genau das, was sie nicht sein soll.
     */
    const val CROSS_CLUB_SEARCH_MIN_LENGTH = 2

    /**
     * Deckel für die vereinsübergreifende Suche. Er ist Teil der Zusage, dass hier niemand einen
     * fremden Mitgliederbestand abziehen kann: wer mehr Treffer will, muss genauer tippen.
     */
    const val CROSS_CLUB_SEARCH_LIMIT = 20

    private fun ParticipantView.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    /**
     * "Gehört diese Person dem Verein [clubId] an?" — Stammverein ODER Eintrag in
     * `participant_additional_club` (Migration V202608142000).
     *
     * Diese Bedingung ist die eine Stelle, an der die Mehrfach-Zugehörigkeit auf der LESESEITE
     * wirkt. Sie ersetzt bewusst nicht jedes `PARTICIPANT.CLUB.eq(...)` im Repo: die Schreibwege
     * (update/delete) prüfen weiterhin allein den Stammverein, sonst könnte ein Zweitverein die
     * Stammdaten ändern.
     *
     * [participantIdField] ist der Schlüssel der äußeren Abfrage — `PARTICIPANT.ID` oder
     * `PARTICIPANT_VIEW.ID`, je nachdem, worüber gerade gefiltert wird.
     */
    private fun belongsToClub(participantIdField: Field<UUID?>, clubField: Field<UUID?>, clubId: UUID): Condition =
        clubField.eq(clubId).or(
            DSL.exists(
                DSL.selectOne()
                    .from(PARTICIPANT_ADDITIONAL_CLUB)
                    .where(PARTICIPANT_ADDITIONAL_CLUB.PARTICIPANT.eq(participantIdField))
                    .and(PARTICIPANT_ADDITIONAL_CLUB.CLUB.eq(clubId))
            )
        )

    /**
     * Dieselbe Bedingung für Abfragen, die außerhalb dieses Repos auf `PARTICIPANT` verbinden
     * (siehe EventRegistrationRepo, das die Personenliste des Meldeformulars zusammenstellt).
     * Öffentlich, damit es genau EINE Definition davon gibt, was "gehört diesem Verein an"
     * heißt — zwei Fassungen würden sich beim nächsten Zuschnitt auseinanderentwickeln.
     */
    fun belongsToClubCondition(clubId: UUID): Condition = belongsToClub(clubId)

    private fun belongsToClub(clubId: UUID): Condition =
        belongsToClub(PARTICIPANT.ID, PARTICIPANT.CLUB, clubId)

    private fun belongsToClubInView(clubId: UUID): Condition =
        belongsToClub(PARTICIPANT_VIEW.ID, PARTICIPANT_VIEW.CLUB, clubId)

    fun create(record: ParticipantRecord) = PARTICIPANT.insertReturning(record) { PARTICIPANT.ID }
    fun create(records: List<ParticipantRecord>) = PARTICIPANT.insert(records)

    fun get(id: UUID) = PARTICIPANT.selectOne { ID.eq(id) }

    fun getOverlapIds(ids: List<UUID>) = PARTICIPANT.select({ ID }) { ID.`in`(ids) }

    fun getAgeRange(participantIds: List<UUID>): JIO<Pair<Int, Int>?> = Jooq.query {
        with(PARTICIPANT) {
            selectFrom(this)
                .where(ID.`in`(participantIds))
                .fetch()
                .let { records ->
                    if (records.isEmpty()) null
                    else {
                        val years = records.map { it.year }
                        Pair(years.min(), years.max())
                    }
                }
        }
    }

    fun all() = PARTICIPANT.select()

    fun update(id: UUID, f: ParticipantRecord.() -> Unit) = PARTICIPANT.update(f) { ID.eq(id) }

    fun any() = PARTICIPANT.exists { DSL.trueCondition() }

    /**
     * Stammdaten ändern. Bleibt bewusst am STAMMVEREIN: `PARTICIPANT.CLUB.eq(...)`, nicht
     * [belongsToClub]. Ein Zweitverein darf die Person sehen und melden, aber niemals ihren
     * Namen, ihr Jahrgang oder ihre Kontaktdaten überschreiben — das ist die wichtigste Grenze
     * der Mehrfach-Zugehörigkeit (V202608142000).
     */
    fun update(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        f: ParticipantRecord.() -> Unit
    ) = PARTICIPANT.update(f) {
        ID.eq(id)
            .and(clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition())
            .and(filterScope(scope, user.club))
    }

    /** Wie [update]: Löschen bleibt dem Stammverein (und globalem Recht) vorbehalten. */
    fun delete(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ) = PARTICIPANT.delete {
        ID.eq(id).and(clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()).and(filterScope(scope, user.club))
    }

    fun exists(id: UUID) = PARTICIPANT.exists { PARTICIPANT.ID.eq(id) }

    /** Der Stammverein allein — für alles, was nur er darf (Zugehörigkeiten pflegen). */
    fun existsByIdAndHomeClub(id: UUID, clubId: UUID) =
        PARTICIPANT.exists { PARTICIPANT.ID.eq(id).and(PARTICIPANT.CLUB.eq(clubId)) }

    /**
     * Meldeweg über das Meldeformular (EventRegistrationService): geweitet auf den vollen
     * Vereinsbestand, damit ein Zweitverein "seine" Person melden kann.
     */
    fun existsByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.exists { PARTICIPANT.ID.eq(id).and(belongsToClub(clubId)) }

    /**
     * Meldeweg über die Wettkampf-Meldung (CompetitionRegistrationService): dieselbe Weitung
     * wie [existsByIdAndClub].
     */
    fun findByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.findOneBy { PARTICIPANT.ID.eq(id).and(belongsToClub(clubId)) }

    /**
     * Der Bestand eines Vereins: seine Mitglieder plus alle, die ihn als weiteren Verein tragen.
     * Speist den Ummelde-Vorrat (SubstitutionService) — ohne die Weitung könnte eine über den
     * Zweitverein gemeldete Person dort von niemandem ersetzt werden.
     */
    fun getByClubId(clubId: UUID): JIO<List<ParticipantRecord>> = PARTICIPANT.select { belongsToClub(clubId) }

    fun count(
        search: String?,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(
                        clubId?.let { belongsToClubInView(it) } ?: DSL.trueCondition()
                    )
                    .and(filterScopeForView(scope, user.club))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<List<ParticipantViewRecord>> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        clubId?.let { belongsToClubInView(it) } ?: DSL.trueCondition(),
                        filterScopeForView(scope, user.club)
                    )
                }
                .fetch()
        }
    }

    fun getByClubAndAgeRestriction(
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        ageRestriction: AgeRestriction?,
    ): JIO<List<ParticipantViewRecord>> = PARTICIPANT_VIEW.select {
        DSL.and(
            clubId?.let { belongsToClubInView(it) } ?: DSL.trueCondition(),
            filterScopeForView(scope, user.club)
                .and(filterAgeRestriction(ageRestriction))
        )
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<ParticipantViewRecord?> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            selectFrom(this)
                .where(ID.eq(id))
                .and(
                    clubId?.let { belongsToClubInView(it) } ?: DSL.trueCondition()
                )
                .and(filterScopeForView(scope, user.club))
                .fetchOne()
        }
    }

    /**
     * Der OWN-Riegel auf der SCHREIBSEITE (update/delete). Bleibt bewusst am Stammverein:
     * ein Nutzer des Zweitvereins hat hier nichts zu suchen, auch wenn er die Person sieht.
     */
    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) PARTICIPANT.CLUB.eq(clubId) else DSL.trueCondition()

    /**
     * Der OWN-Riegel auf der LESESEITE. Hier ist die Weitung nötig und nicht bloß bequem: ohne
     * sie würde ein Meldender mit OWN-Recht die eigene Personenliste zwar über den clubId-Filter
     * geweitet bekommen, der Scope-Riegel darüber würde die Gäste aber sofort wieder heraus-
     * filtern — die Zugehörigkeit wäre in der Oberfläche unsichtbar (nur die Datenbank wüsste
     * davon). Der Riegel bleibt scharf: er lässt genau die Personen durch, die dem eigenen
     * Verein angehören, Stammverein oder Zweitverein.
     */
    private fun filterScopeForView(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) {
        clubId?.let { belongsToClubInView(it) } ?: DSL.falseCondition()
    } else DSL.trueCondition()

    /**
     * Die vereinsübergreifende Suche — der einzige Weg zu Personen, die dem meldenden Verein
     * gar nicht angehören. Sie ist bewusst keine Liste:
     *
     * * Der Aufrufer muss [CROSS_CLUB_SEARCH_MIN_LENGTH] Zeichen mitbringen; das prüft der
     *   Service, bevor er hier überhaupt landet.
     * * [CROSS_CLUB_SEARCH_LIMIT] deckelt die Treffer.
     * * Der eigene Bestand fällt heraus ([excludeClubId]) — er steht schon in der regulären
     *   Liste, und doppelte Einträge in der Auswahl verwirren mehr, als sie helfen.
     *
     * Gesucht wird nur über Vor- und Nachname. Der Gastruderer-Freitext (`external_club_name`)
     * bleibt außen vor: über ihn zu suchen hieße, fremde Vereinslisten nach Vereinsnamen
     * durchblättern zu können.
     */
    fun searchAcrossClubs(
        search: String,
        excludeClubId: UUID,
    ): JIO<List<ParticipantViewRecord>> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            selectFrom(this)
                .where(search.metaSearch(listOf(FIRSTNAME, LASTNAME)))
                .and(DSL.not(belongsToClubInView(excludeClubId)))
                .orderBy(LASTNAME.asc(), FIRSTNAME.asc())
                .limit(CROSS_CLUB_SEARCH_LIMIT)
                .fetch()
        }
    }

    private fun filterAgeRestriction(ageRestriction: AgeRestriction?): Condition {
        if (ageRestriction == null) return DSL.trueCondition()

        val fromCondition = ageRestriction.from?.let { PARTICIPANT_VIEW.YEAR.greaterOrEqual(it) } ?: DSL.trueCondition()
        val toCondition = ageRestriction.to?.let { PARTICIPANT_VIEW.YEAR.lessOrEqual(it) } ?: DSL.trueCondition()

        return fromCondition.and(toCondition)
    }

    fun allAsJson() = PARTICIPANT.selectAsJson()

    fun insertJsonData(data: String) = PARTICIPANT.insertJsonData(data)

}