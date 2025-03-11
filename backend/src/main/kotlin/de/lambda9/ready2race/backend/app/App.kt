package de.lambda9.ready2race.backend.app

import de.lambda9.ready2race.backend.Config
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.jooq.Jooq
import javax.sql.DataSource

data class Env(
    val config: Config,
) {

    companion object {

        fun create(config: Config): Pair<JEnv, DataSource> {
            val env = Env(
                config = config
            )

            return Jooq.create(env) {
                url = config.database.url
                user = config.database.user
                password = config.database.password
                schema = "ready2race"
                queryPrinter = null
            }
        }

            val ds = HikariDataSource(HikariConfig(props).apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                password = config.password
                username = config.user
                schema = config.schema
            })

            val configuration = DefaultConfiguration()
                .set(ds)
                /*.set(JooqQueryPrinter())*/
                .set(config.dialect)

            return Jooq(
                dsl = DSL.using(configuration),
                env = env,
            ) to ds
        }
    }

}

typealias JEnv = Jooq<Env>

typealias App<E, A> = KIO<JEnv, E, A>


