package de.lambda9.ready2race.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.database.initializeDatabase
import de.lambda9.ready2race.backend.module
import de.lambda9.ready2race.testing.kio.DefaultTestComprehensionScope
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.kio.testComprehension
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.jooq.Jooq
import de.lambda9.tailwind.jooq.JooqQueryPrinter
import io.ktor.serialization.*
import io.ktor.server.application.Application
import io.ktor.server.testing.*
import org.flywaydb.core.Flyway
import org.jooq.ConnectionProvider
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import java.io.PrintWriter
import java.net.ServerSocket
import java.sql.Connection
import java.util.*
import kotlin.test.fail

/**
 * Use this function to unit test against the database, e.g. testing services and repos.
 */
fun testComprehension(block: TestComprehensionScope<JEnv>.() -> Unit) =
    TestRunner.runComprehension(block)

/**
 * Use this function to test end to end behaviour, when you also need access to the database.
 * This test function is pretty slow and should only be used for integration tests, because it has to reset the database.
 *
 * For unit testing just against the database without end to end behaviour, use [testComprehension].
 * For integration testing without needed access to the database inside the test to verify effects, use [testApplication].
 */
fun testApplicationComprehension(block: suspend TestApplicationComprehensionScope<JEnv>.() -> Unit) =
    TestRunner.runApplication(block)

interface TestApplicationExtension {

    fun extendApplication(block: Application.() -> Unit)

}

open class DefaultTestApplicationExtension(val builder: ApplicationTestBuilder) : TestApplicationExtension {
    override fun extendApplication(block: Application.() -> Unit) {
        builder.application { block() }
    }
}

interface TestApplicationComprehensionScope<R> : TestApplicationExtension, TestComprehensionScope<R>, ClientProvider

private object TestRunner {

    private val postgres = PostgreSQLContainer("postgres:17")
    private val config: Config
    private val connectionProvider: ConnectionProvider
    private val flyway: Flyway

    private var initial: Boolean
    private var dirty: Boolean

    init {
        postgres.start()

        config = Config(
            mode = Config.Mode.TEST,
            http = Config.Http(
                host = "localhost",
                port = -1,
            ),
            database = Config.Database(
                url = postgres.jdbcUrl,
                user = postgres.username,
                password = postgres.password,
                logQueries = true,
            ),
            smtp = null,
            security = Config.Security(
                pepper = "pepper",
            ),
            admin = Config.Admin(
                email = "admin",
                password = "admin",
            ),
            webDAV = Config.WebDAV(
                urlScheme = "https",
                host = "localhost",
                path = "",
                authUser = "admin",
                authPassword = "admin",
            )
        )

        val hikariProps = Properties().apply {
            put("dataSource.logWriter", PrintWriter(System.out))
        }
        val datasource = HikariDataSource(HikariConfig(hikariProps).apply {
            jdbcUrl = postgres.jdbcUrl
            driverClassName = "org.postgresql.Driver"
            username = postgres.username
            password = postgres.password
            schema = "ready2race"
        })

        connectionProvider = DataSourceConnectionProvider(datasource)

        val flywayConfig = Flyway.configure()
            .dataSource(datasource)
            .defaultSchema("ready2race")
            .cleanDisabled(false)

        flyway = Flyway(flywayConfig)

        initial = true
        dirty = true
    }

    fun runComprehension(block: TestComprehensionScope<JEnv>.() -> Unit) {

        cleanDatabase()

        val connection = connectionProvider.acquire()

        if (connection == null) {
            fail("DB connection could not be acquired")
        } else {

            connection.autoCommit = false
            val env = getEnv(connection)

            try {

                KIO.testComprehension(env, block)

            } catch (t: Throwable) {
                throw t
            } finally {
                connection.rollback()
                connection.autoCommit = true
                connectionProvider.release(connection)
            }
        }
    }

    fun runApplication(block: suspend TestApplicationComprehensionScope<JEnv>.() -> Unit) = testApplication {

        cleanDatabase()
        dirty = true

        val env = getEnv()

        application {
            module(env)
        }

        val scope = object : TestApplicationComprehensionScope<JEnv>,
            TestApplicationExtension by DefaultTestApplicationExtension(this),
            TestComprehensionScope<JEnv> by DefaultTestComprehensionScope(env),
            ClientProvider by this {}
        block(scope)

    }

    private fun cleanDatabase() {
        if (dirty) {
            val env = Jooq(
                dsl = DSL.using(connectionProvider, SQLDialect.POSTGRES),
                env = Env(config)
            )
            if (initial) {
                initial = false
            } else {
                flyway.clean()
            }
            flyway.migrate()
            initializeDatabase(env)
            dirty = false
        }
    }

    private fun getEnv(connection: Connection? = null): JEnv {
        val configuration = DefaultConfiguration().apply {
            set(SQLDialect.POSTGRES)
            if (connection != null) {
                set(connection)
            } else {
                set(connectionProvider)
            }
            if (config.database.logQueries) {
                set(JooqQueryPrinter())
            }
        }
        return Jooq(
            dsl = DSL.using(configuration),
            env = Env(config),
        )
    }
}