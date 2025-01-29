package de.lambda9.ready2race.backend.app

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.database.JooqQueryPrinter
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import java.io.PrintWriter
import java.util.*
import javax.sql.DataSource

data class Env(
    val config: Config,
) {

    companion object {

        fun create(config: Config): Pair<JEnv, DataSource> {
            val env = Env(
                config = config
            )

            return create(env) {
                url = config.database.url
                user = config.database.user
                password = config.database.password
                schema = "ready2race"
            }
        }

        private fun <R> create(
            env: R,
            init: Jooq.Config.() -> Unit
        ): Pair<Jooq<R>, HikariDataSource> {
            val config = Jooq.Config().apply(init)
            val props = Properties().apply {
                put("dataSource.logWriter", PrintWriter(System.out))
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
                //.set(JooqQueryPrinter())
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


