package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailIndividualTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_INDIVIDUAL_TEMPLATE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object EmailTemplateRepo {

    fun create(
        record: EmailIndividualTemplateRecord,
    ): JIO<Unit> = Jooq.query {
        with(EMAIL_INDIVIDUAL_TEMPLATE) {
            insertInto(this)
                .set(record)
                .execute()
        }
    }

    fun get(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): JIO<EmailIndividualTemplateRecord?> = Jooq.query {
        with(EMAIL_INDIVIDUAL_TEMPLATE) {
            selectFrom(this)
                .where(KEY.eq(key.name))
                .and(LANGUAGE.eq(language.name))
                .fetchOne()
        }
    }

    fun delete(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): JIO<Unit> = Jooq.query {
        with(EMAIL_INDIVIDUAL_TEMPLATE) {
            deleteFrom(this)
                .where(KEY.eq(key.name))
                .and(LANGUAGE.eq(language.name))
                .execute()
        }
    }
}