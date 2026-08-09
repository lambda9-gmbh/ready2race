import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {PollerState, createPoller} from './polling'

const INTERVAL = 15_000

/** Ein Versprechen, das der Test von Hand einlöst - so ist jeder Abruf einzeln steuerbar. */
const deferred = <T,>() => {
    let resolve!: (value: T) => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

/** Sammelt die Zustände und den Abruf-Verlauf eines Takters. */
const harness = () => {
    const pending: {resolve: (value: string | null) => void; reject: (reason: unknown) => void; signal: AbortSignal}[] = []
    const states: PollerState<string>[] = []
    const poller = createPoller<string>({
        intervalMs: INTERVAL,
        onState: state => states.push(state),
        load: signal => {
            const d = deferred<string | null>()
            pending.push({resolve: d.resolve, reject: d.reject, signal})
            return d.promise
        },
    })
    const last = () => states[states.length - 1]
    return {poller, pending, states, last}
}

/** Lässt die Microtask-Warteschlange leerlaufen, ohne die Uhr zu bewegen. */
const flush = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
}

describe('createPoller', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })
    afterEach(() => {
        vi.useRealTimers()
    })

    it('lädt sofort beim Start und meldet die Daten', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        expect(pending).toHaveLength(1)

        pending[0].resolve('erster Stand')
        await flush()

        expect(last().data).toBe('erster Stand')
        expect(last().initialLoad).toBe(false)
        expect(last().failed).toBe(false)
        expect(last().lastUpdated).not.toBeNull()
        poller.stop()
    })

    it('taktet nach dem Intervall weiter', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)

        expect(pending).toHaveLength(2)
        poller.stop()
    })

    /** Der eigentliche Grund gegen setInterval: ein hängender Abruf darf keine Schlange bilden. */
    it('startet keinen zweiten Abruf, solange der erste läuft', async () => {
        const {poller, pending} = harness()
        poller.start()

        await vi.advanceTimersByTimeAsync(INTERVAL * 5)

        expect(pending).toHaveLength(1)
        poller.stop()
    })

    it('behält bei einem Fehler den letzten guten Stand', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].resolve('guter Stand')
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)
        pending[1].reject(new Error('kein Netz'))
        await flush()

        expect(last().data).toBe('guter Stand')
        expect(last().failed).toBe(true)
        poller.stop()
    })

    it('wertet eine Antwort ohne Nutzdaten als Fehlversuch', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].resolve(null)
        await flush()

        expect(last().data).toBeNull()
        expect(last().initialLoad).toBe(false)
        expect(last().failed).toBe(true)
        poller.stop()
    })

    it('taktet nach einem Fehler weiter und erholt sich', async () => {
        const {poller, pending, last} = harness()
        poller.start()
        pending[0].reject(new Error('kein Netz'))
        await flush()

        await vi.advanceTimersByTimeAsync(INTERVAL)
        expect(pending).toHaveLength(2)
        pending[1].resolve('wieder da')
        await flush()

        expect(last().data).toBe('wieder da')
        expect(last().failed).toBe(false)
        poller.stop()
    })

    it('bricht bei refreshNow den laufenden Abruf ab, ohne ihn als Fehler zu werten', async () => {
        const {poller, pending, last} = harness()
        poller.start()

        poller.refreshNow()
        expect(pending).toHaveLength(2)
        expect(pending[0].signal.aborted).toBe(true)

        // Der abgebrochene Abruf meldet sich verspätet mit einem AbortError.
        pending[0].reject(Object.assign(new Error('aborted'), {name: 'AbortError'}))
        await flush()
        expect(last().failed).toBe(false)

        pending[1].resolve('frisch')
        await flush()
        expect(last().data).toBe('frisch')
        poller.stop()
    })

    it('taktet im Hintergrund nicht und lädt beim Zurückkehren sofort', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        poller.suspend()
        await vi.advanceTimersByTimeAsync(INTERVAL * 3)
        expect(pending).toHaveLength(1)

        poller.resume()
        expect(pending).toHaveLength(2)
        poller.stop()
    })

    it('taktet nach stop nicht mehr', async () => {
        const {poller, pending} = harness()
        poller.start()
        pending[0].resolve('a')
        await flush()

        poller.stop()
        await vi.advanceTimersByTimeAsync(INTERVAL * 3)

        expect(pending).toHaveLength(1)
    })
})
