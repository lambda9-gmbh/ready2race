package de.lambda9.ready2race.backend.kio

import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.extensions.exit.fold

fun <A, B> Exit<Nothing, A>.fold(
    onDefect: (Throwable) -> B,
    onSuccess: (A) -> B
): B = fold(
    onError = { it },
    onDefect = onDefect,
    onSuccess = onSuccess
)

fun <A> Exit<Nothing, A>.onDefect(
    f: (Throwable) -> Unit
): Unit = fold(
    onDefect = { f(it) },
    onSuccess = {}
)