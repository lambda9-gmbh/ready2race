package de.lambda9.ready2race.backend.app.sequence.control

import de.lambda9.ready2race.backend.app.sequence.entity.SequenceConsumer
import de.lambda9.ready2race.backend.database.generated.tables.references.SEQUENCE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.DSLContext

object SequenceRepo {

    private fun DSLContext.createMissingSequence(
        consumer: SequenceConsumer,
    ): Long = with(SEQUENCE) {
        insertInto(this)
            .set(CONSUMER, consumer.name)
            .set(VALUE, 1)
            .set(STEP, 1)
            .returningResult(VALUE)
            .fetchOne()!!
            .value1()!!
    }

    fun getAndIncrement(
        consumer: SequenceConsumer,
    ): JIO<Long> = Jooq.query {
        with(SEQUENCE) {
            selectFrom(this)
                .where(CONSUMER.eq(consumer.name))
                .forUpdate()
                .fetchOne()?.let {
                    val value = it.value
                    it.value = value + 1
                    it.update()
                    value
                } ?: createMissingSequence(consumer)
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
                .fetchOne()
                ?.apply { this.value = value }
                ?.update()
                ?: createMissingSequence(consumer)
        }
    }
}