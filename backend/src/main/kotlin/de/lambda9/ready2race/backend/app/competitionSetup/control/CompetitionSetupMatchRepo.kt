package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupMatchRepo {
    fun create(records: Collection<CompetitionSetupMatchRecord>) = COMPETITION_SETUP_MATCH.insert(records)

    fun get(setupMatchId: UUID) = COMPETITION_SETUP_MATCH.selectOne { ID.eq(setupMatchId) }

    /**
     * Wendet eine Benennungs-Abweichung auf einen Setup-Lauf an - oder setzt ihn zurück.
     *
     * [name]/[executionOrder] kommen aus dem Naming-Satz für die aktuelle Bracket-Größe und sind
     * ABWEICHUNGEN vom Ausgangszustand (KDoc von `applyMatchNamings` im
     * CompetitionExecutionService). Null heißt deshalb "keine Abweichung -> Ausgangszustand",
     * nicht "Feld unverändert lassen": Genau das Unverändert-Lassen ließ Läufe den Namen einer
     * FRÜHEREN Anwendung behalten (zweimal "VF1", kein "VF2").
     *
     * Der Ausgangszustand wird beim ersten Überschreiben in base_name/base_execution_order
     * gesichert (V202608121200). base_execution_order ist zugleich der Marker "schon gesichert" -
     * execution_order ist NOT NULL, base_name allein könnte einen null-Ausgangsnamen nicht von
     * "nie gesichert" unterscheiden.
     */
    fun applyNaming(id: UUID, name: String?, executionOrder: Int?) = COMPETITION_SETUP_MATCH.update(
        f = {
            if (baseExecutionOrder == null) {
                baseName = this.name
                baseExecutionOrder = this.executionOrder
            }
            this.name = name ?: baseName
            this.executionOrder = executionOrder ?: baseExecutionOrder!!
        },
        condition = { ID.eq(id) },
    )

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupMatchRecord>> = Jooq.query {
        with(COMPETITION_SETUP_MATCH) {
            selectFrom(this)
                .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                .fetch()
        }
    }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_MATCH.select({ ID }) { ID.`in`(ids) }

    fun getAsJson(competitionSetupRoundIds: List<UUID>) =
        COMPETITION_SETUP_MATCH.selectAsJson { COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds) }

    fun insertJsonData(data: String) = COMPETITION_SETUP_MATCH.insertJsonData(data)
}