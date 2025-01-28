package de.lambda9.ready2race.backend.pagination

interface ResponsePage<T, S: Sortable> {
    val data: List<T>
    val pagination: Pagination<S>
}