package de.lambda9.ready2race.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.ready2race.backend.Config
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

fun testComprehension(block: TestComprehensionScope<JEnv>.() -> Unit) =
    TestRunner.runComprehension(block)

fun testApplicationComprehension(block: suspend TestApplicationComprehensionScope<JEnv>.() -> Unit) =
    TestRunner.runApplication(block)

interface TestApplicationComprehensionScope<R> : TestComprehensionScope<R>, ClientProvider

private object TestRunner {

    private val postgres = PostgreSQLContainer("postgres:17")
    private val config: Config
    private val connectionProvider: ConnectionProvider

    init {
        postgres.start()

        val port = ServerSocket(0).use { it.localPort }

        config = Config(
            mode = Config.Mode.TEST,
            http = Config.Http(
                host = "localhost",
                port = port,
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

        val flywayConfig = Flyway.configure().dataSource(datasource).defaultSchema("ready2race")
        Flyway(flywayConfig).migrate()

        val env = Jooq(
            dsl = DSL.using(datasource, SQLDialect.POSTGRES),
            env = Env(config)
        )
        initializeDatabase(env)
    }

    fun runComprehension(block: TestComprehensionScope<JEnv>.() -> Unit) {

        val connection = getConnection()
        val env = getEnv(connection)

        try {

            KIO.testComprehension(env, block)

        } catch (t: Throwable) {
            throw t
        } finally {
            closeConnection(connection)
        }
    }

    fun runApplication(block: suspend TestApplicationComprehensionScope<JEnv>.() -> Unit) = testApplication {

        val connection = getConnection()
        val env = getEnv(connection)

        try {

            application {
                module(env)
            }

            val scope = object : TestApplicationComprehensionScope<JEnv>,
                TestComprehensionScope<JEnv> by DefaultTestComprehensionScope(env),
                ClientProvider by this {}
            block(scope)

        } catch (t: Throwable) {
            throw t
        } finally {
            closeConnection(connection)
        }
    }

    private fun getConnection(): Connection {
        val connection = connectionProvider.acquire()

        if (connection == null) {
            fail("DB connection could not be acquired")
        } else {
            connection.autoCommit = false
            return connection
        }
    }

    private fun closeConnection(connection: Connection) {
        connection.rollback()
        connection.autoCommit = true
        connectionProvider.release(connection)
    }

    private fun getEnv(connection: Connection): JEnv {
        val configuration = DefaultConfiguration()
            .set(SQLDialect.POSTGRES)
            .set(connection)
            .let {
                if (config.database.logQueries) {
                    it.set(JooqQueryPrinter())
                } else {
                    it
                }
            }
        return Jooq(
            dsl = DSL.using(configuration),
            env = Env(config),
        )
    }
}