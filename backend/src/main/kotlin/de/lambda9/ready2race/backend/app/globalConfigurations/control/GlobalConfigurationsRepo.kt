package de.lambda9.ready2race.backend.app.globalConfigurations.control

import de.lambda9.ready2race.backend.database.generated.tables.records.GlobalConfigurationsRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GLOBAL_CONFIGURATIONS
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import org.jooq.impl.DSL

object GlobalConfigurationsRepo {
    fun get() = GLOBAL_CONFIGURATIONS.selectOne { ID.isTrue }

    fun update(f: GlobalConfigurationsRecord.() -> Unit) = GLOBAL_CONFIGURATIONS.update(f) { DSL.trueCondition() }

}