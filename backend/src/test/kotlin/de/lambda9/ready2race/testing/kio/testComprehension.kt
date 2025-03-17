package de.lambda9.ready2race.testing.kio

import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import kotlin.test.assertEquals
import kotlin.test.fail

fun <R> KIO.Companion.testComprehension(env: R, block: TestComprehensionScope<R>.() -> Unit) =
    block(DefaultTestComprehensionScope(env))

interface TestComprehensionScope<R> {

    operator fun <A> KIO<R, Any?, A>.not(): A

    fun <A> assertKIOSucceeds(expected: A? = null, kio: () -> KIO<R, Any?, A>)

    fun <E> assertKIOFails(expected: E? = null, kio: () -> KIO<R, E, Any?>)

    fun assertKIODies(expected: Throwable? = null, kio: () -> KIO<R, Any?, Any?>)

}

open class DefaultTestComprehensionScope<R>(private val env: R) : TestComprehensionScope<R> {
    override operator fun <A> KIO<R, Any?, A>.not(): A = unsafeRunSync(env).fold(
        onSuccess = { it },
        onError = {
            fail("Expected computation success failed with: $it")
        },
        onDefect = {
            fail("Expected computation success threw: $it")
        }
    )

    override fun <A> assertKIOSucceeds(expected: A?, kio: () -> KIO<R, Any?, A>) {
        val actual = !kio()
        if (expected != null) {
            assertEquals(expected, actual)
        }
    }

    override fun <E> assertKIOFails(expected: E?, kio: () -> KIO<R, E, Any?>) = kio().unsafeRunSync(env).fold(
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

    override fun assertKIODies(expected: Throwable?, kio: () -> KIO<R, Any?, Any?>) = kio().unsafeRunSync(env).fold(
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