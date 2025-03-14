package de.lambda9.ready2race.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.module
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.jooq.Jooq
import io.ktor.server.testing.*
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.*
import kotlin.test.fail

class TestComprehensionScope(
    private val builder: ApplicationTestBuilder,
    private val env: JEnv,
) : KIO.ComprehensionScope<JEnv, Any>, ClientProvider by builder {

    override fun <A> KIO<JEnv, Any, A>.not(): A = unsafeRunSync(env).fold(
        onSuccess = { it },
        onError = { fail("Expected successful computation failed with: $it") },
        onDefect = { fail("Expected successful computation threw: $it") }
    )

}

fun testComprehension(block: KIO.ComprehensionScope<Any?, Any>.() -> Unit) {
    val scope = object : KIO.ComprehensionScope<Any?, Any> {
        override fun <A> IO<Any, A>.not(): A = unsafeRunSync().fold(
            onSuccess = { it },
            onError = { fail("Expected successful IO computation failed with: $it") },
            onDefect = { fail("Expected successful IO computation threw: $it") }
        )
    }
    block(scope)
}

fun testApplicationKIO(block: suspend TestComprehensionScope.() -> Unit) =
    ApplicationTestRunner.run(block)

private object ApplicationTestRunner {

    private val postgres = PostgreSQLContainer("postgres:17")
    private val config: Config
    private val dataSource: HikariDataSource

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
        dataSource = HikariDataSource(HikariConfig(hikariProps).apply {
            jdbcUrl = postgres.jdbcUrl
            driverClassName = "org.postgresql.Driver"
            username = postgres.username
            password = postgres.password
            schema = "ready2race"
        })

        val flyway = Flyway.configure().dataSource(dataSource).defaultSchema("ready2race")
        Flyway(flyway).migrate()
    }

    fun run(block: suspend TestComprehensionScope.() -> Unit) = testApplication {

        val dsl = DSL.using(dataSource, SQLDialect.POSTGRES)
        val configuration = dsl.configuration()

        val connection = configuration.connectionProvider().acquire()

        if (connection == null) {
            fail("DB connection provider could not be acquired")
        } else {
            try {
                connection.autoCommit = false
                val env = Jooq(
                    dsl = DSL.using(connection),
                    env = Env(config)
                )

                application {
                    module(env)
                }

                val scope = TestComprehensionScope(this, env)
                block(scope)
            } catch (t: Throwable) {
                throw t
            } finally {
                connection.rollback()
                connection.autoCommit = true
            }

        }

    }

}