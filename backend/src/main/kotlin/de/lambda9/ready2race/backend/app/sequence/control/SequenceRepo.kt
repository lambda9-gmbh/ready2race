package de.lambda9.ready2race.backend.app.sequence.control

import de.lambda9.ready2race.backend.app.sequence.entity.SequenceConsumer
import de.lambda9.ready2race.backend.database.generated.tables.records.SequenceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.SEQUENCE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.DSLContext

object SequenceRepo {

    fun addMissing(): JIO<Unit> = Jooq.query {
        val persistedConsumer = fetch(SEQUENCE).map { it.consumer }
        val new = SequenceConsumer.entries
            .filter { !persistedConsumer.contains(it.name) }
            .map {
                SequenceRecord(
                    consumer = it.name,
                    value = 1,
                    step = 1,
                )
            }
        batchInsert(new).execute()
    }

    fun getAndIncrement(
        consumer: SequenceConsumer,
    ): JIO<Long> = Jooq.query {
        with(SEQUENCE) {
            selectFrom(this)
                .where(CONSUMER.eq(consumer.name))
                .forUpdate()
                .fetchOne()!!.let {
                    val value = it.value
                    it.value = value + 1
                    it.update()
                    value
                }
        }
    }

    fun set(
        consumer: SequenceConsumer,
        value: Long,
    ): JIO<Unit> = Jooq.query {
        with(SEQUENCE) {
            selectFrom(this)
                .where(CONSUMER.eq(consumer.name))
                .forUpdate()
                .fetchOne()!!
                .apply { this.value = value }
                .update()
        }
    }
}