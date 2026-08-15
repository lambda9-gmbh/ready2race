import {describe, expect, test} from 'vitest'
import {startScannerWithRetry, StartableScanner} from './startScannerWithRetry.ts'

// Scanner-Attrappe: schlägt die ersten failCount Starts fehl, danach Erfolg.
const makeScanner = (failCount: number) => {
    let calls = 0
    const scanner: StartableScanner = {
        start: () => {
            calls++
            return calls <= failCount
                ? Promise.reject(new Error('NotReadableError'))
                : Promise.resolve()
        },
    }
    return {scanner, getCalls: () => calls}
}

// sleep-Attrappe, die die angefragten Pausen protokolliert und sofort weiterläuft.
const makeSleep = () => {
    const slept: number[] = []
    return {
        slept,
        sleep: (ms: number) => {
            slept.push(ms)
            return Promise.resolve()
        },
    }
}

describe('startScannerWithRetry', () => {
    test('erster Versuch erfolgreich: keine Pausen, ein Startaufruf', async () => {
        const {scanner, getCalls} = makeScanner(0)
        const {slept, sleep} = makeSleep()
        const result = await startScannerWithRetry(scanner, () => false, [500, 1000], sleep)
        expect(result).toBe('started')
        expect(getCalls()).toBe(1)
        expect(slept).toEqual([])
    })

    test('kurz belegte Kamera: wiederholt mit Backoff und startet dann', async () => {
        const {scanner, getCalls} = makeScanner(2)
        const {slept, sleep} = makeSleep()
        const result = await startScannerWithRetry(scanner, () => false, [500, 1000, 2000], sleep)
        expect(result).toBe('started')
        expect(getCalls()).toBe(3)
        expect(slept).toEqual([500, 1000])
    })

    test('dauerhaft blockiert: scheitert nach Ausschöpfen aller Pausen', async () => {
        const {scanner, getCalls} = makeScanner(Infinity)
        const {slept, sleep} = makeSleep()
        const result = await startScannerWithRetry(scanner, () => false, [500, 1000], sleep)
        expect(result).toBe('failed')
        // Erstversuch + eine Wiederholung je Pause
        expect(getCalls()).toBe(3)
        expect(slept).toEqual([500, 1000])
    })

    test('vor dem ersten Versuch abgebrochen: kein Startaufruf', async () => {
        const {scanner, getCalls} = makeScanner(0)
        const result = await startScannerWithRetry(scanner, () => true, [500])
        expect(result).toBe('cancelled')
        expect(getCalls()).toBe(0)
    })

    test('Abbruch während laufendem Start: Erfolg zählt als abgebrochen', async () => {
        // Simuliert den Unmount, während getUserMedia noch läuft: start() gelingt,
        // aber der Aufrufer hat inzwischen abgebrochen und räumt selbst auf.
        let cancelled = false
        let resolveStart: () => void = () => {}
        const scanner: StartableScanner = {
            start: () =>
                new Promise<void>(resolve => {
                    resolveStart = resolve
                }),
        }
        const resultPromise = startScannerWithRetry(scanner, () => cancelled, [500])
        cancelled = true
        resolveStart()
        expect(await resultPromise).toBe('cancelled')
    })

    test('Abbruch während der Pause: keine weitere Wiederholung', async () => {
        let cancelled = false
        const {scanner, getCalls} = makeScanner(Infinity)
        const sleep = () => {
            cancelled = true
            return Promise.resolve()
        }
        const result = await startScannerWithRetry(scanner, () => cancelled, [500, 1000], sleep)
        expect(result).toBe('cancelled')
        expect(getCalls()).toBe(1)
    })

    test('fehlgeschlagener Start nach Abbruch löst keine Pause mehr aus', async () => {
        let cancelled = false
        const scanner: StartableScanner = {
            start: () => {
                cancelled = true
                return Promise.reject(new Error('AbortError'))
            },
        }
        const {slept, sleep} = makeSleep()
        const result = await startScannerWithRetry(scanner, () => cancelled, [500], sleep)
        expect(result).toBe('cancelled')
        expect(slept).toEqual([])
    })
})
