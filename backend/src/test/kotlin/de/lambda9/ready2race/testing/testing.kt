package de.lambda9.ready2race.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.module
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
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
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail

fun testApplicationKIO(block: suspend TestComprehensionScope<JEnv>.() -> Unit) =
    ApplicationTestRunner.run(block)

interface TestComprehensionScope<R> : KIO.ComprehensionScope<R, Any?>, ClientProvider {

    fun <A> assertKIOSucceeds(expected: A? = null, kio: () -> KIO<R, Any?, A>)

    fun <E> assertKIOFails(expected: E? = null, kio: () -> KIO<R, E, Any?>)

    fun assertKIODies(expected: Throwable? = null, kio: () -> KIO<JEnv, Any?, Any?>)

}

private object ApplicationTestRunner {

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
    }

    fun run(block: suspend TestComprehensionScope<JEnv>.() -> Unit) = testApplication {

        val connection = connectionProvider.acquire()
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

        if (connection == null) {
            fail("DB connection provider could not be acquired")
        } else {
            try {
                connection.autoCommit = false
                val env = Jooq(
                    dsl = DSL.using(configuration),
                    env = Env(config),
                )

                application {
                    module(env)
                }
                val scope = object : TestComprehensionScope<JEnv>, ClientProvider by this {
                    override operator fun <A> KIO<JEnv, Any?, A>.not(): A = unsafeRunSync(env).fold(
                        onSuccess = { it },
                        onError = {
                            fail("Expected computation success failed with: $it")
                        },
                        onDefect = {
                            fail("Expected computation success threw: $it")
                        }
                    )

                    override fun <A> assertKIOSucceeds(expected: A?, kio: () -> KIO<JEnv, Any?, A>) {
                        val actual = !kio()
                        if (expected != null) {
                            assertEquals(expected, actual)
                        }
                    }

                    override fun <E> assertKIOFails(expected: E?, kio: () -> KIO<JEnv, E, Any?>) = kio().unsafeRunSync(env).fold(
                        onSuccess = {
                            fail("Expected computation failure succeeded with: $it")
                        },
                        onError = { actual ->
                            if (expected != null) {
                                assertEquals(expected, actual)
                            }
                        },
                        onDefect = {
                            fail("Expected computation failure threw: $it")
                        }
                    )

                    override fun assertKIODies(expected: Throwable?, kio: () -> KIO<JEnv, Any?, Any?>) = kio().unsafeRunSync(env).fold(
                        onSuccess = {
                            fail("Expected computation defect succeeded with: $it")
                        },
                        onError = {
                            fail("Expected computation defect failed with: $it")
                        },
                        onDefect = { actual ->
                            if (expected != null) {
                                assertEquals(expected, actual)
                            }
                        }
                    )
                }
                block(scope)
            } catch (t: Throwable) {
                throw t
            } finally {
                connection.rollback()
                connection.autoCommit = true
                connectionProvider.release(connection)
            }
        }
    }
}