import {describe, expect, it} from 'vitest'
import {
    AUTO_REFRESH_DEFAULT_SECONDS,
    AUTO_REFRESH_MAX_SECONDS,
    AUTO_REFRESH_MIN_SECONDS,
    clampRefreshSeconds,
    refreshIntervalMs,
    syncStatus,
} from './autoRefresh.ts'

describe('clampRefreshSeconds', () => {
    it('lässt einen Wert innerhalb der Grenzen unangetastet', () => {
        expect(clampRefreshSeconds(20)).toBe(20)
        expect(clampRefreshSeconds(AUTO_REFRESH_MIN_SECONDS)).toBe(AUTO_REFRESH_MIN_SECONDS)
        expect(clampRefreshSeconds(AUTO_REFRESH_MAX_SECONDS)).toBe(AUTO_REFRESH_MAX_SECONDS)
    })

    it('zieht einen zu schnellen Takt auf die Untergrenze', () => {
        expect(clampRefreshSeconds(1)).toBe(AUTO_REFRESH_MIN_SECONDS)
        expect(clampRefreshSeconds(0)).toBe(AUTO_REFRESH_MIN_SECONDS)
        expect(clampRefreshSeconds(-30)).toBe(AUTO_REFRESH_MIN_SECONDS)
    })

    it('zieht einen zu langsamen Takt auf die Obergrenze', () => {
        expect(clampRefreshSeconds(61)).toBe(AUTO_REFRESH_MAX_SECONDS)
        expect(clampRefreshSeconds(3600)).toBe(AUTO_REFRESH_MAX_SECONDS)
    })

    // Ein Bestand von vor der Einstellung liefert nichts; die Seite soll trotzdem nachziehen.
    it('fällt ohne Wert auf die Voreinstellung zurück', () => {
        expect(clampRefreshSeconds(null)).toBe(AUTO_REFRESH_DEFAULT_SECONDS)
        expect(clampRefreshSeconds(undefined)).toBe(AUTO_REFRESH_DEFAULT_SECONDS)
        expect(clampRefreshSeconds(Number.NaN)).toBe(AUTO_REFRESH_DEFAULT_SECONDS)
    })

    it('rundet krumme Sekunden', () => {
        expect(clampRefreshSeconds(12.4)).toBe(12)
        expect(clampRefreshSeconds(12.6)).toBe(13)
    })
})

describe('refreshIntervalMs', () => {
    it('gibt den eingestellten Takt in Millisekunden', () => {
        expect(refreshIntervalMs({enabled: true, seconds: 5})).toBe(5000)
        expect(refreshIntervalMs({enabled: true, seconds: 60})).toBe(60000)
    })

    // undefined und nicht 0: useFetch stellt darauf gar keinen Wecker.
    it('stellt bei abgeschaltetem Abgleich keinen Takt', () => {
        expect(refreshIntervalMs({enabled: false, seconds: 5})).toBeUndefined()
        expect(refreshIntervalMs({enabled: false, seconds: 60})).toBeUndefined()
    })

    it('hält den Takt an, solange ein Dialog offen ist', () => {
        expect(refreshIntervalMs({enabled: true, seconds: 5}, true)).toBeUndefined()
    })

    it('nimmt den Takt nach dem Schließen des Dialogs wieder auf', () => {
        expect(refreshIntervalMs({enabled: true, seconds: 5}, false)).toBe(5000)
    })

    it('begrenzt auch einen Takt, der so nie eingestellt werden konnte', () => {
        expect(refreshIntervalMs({enabled: true, seconds: 1})).toBe(AUTO_REFRESH_MIN_SECONDS * 1000)
        expect(refreshIntervalMs({enabled: true, seconds: 3600})).toBe(
            AUTO_REFRESH_MAX_SECONDS * 1000,
        )
    })
})

describe('syncStatus', () => {
    it('meldet nichts, solange die Abrufe durchgehen', () => {
        expect(syncStatus({failures: 0, hasData: true})).toBe('ok')
        expect(syncStatus({failures: 0, hasData: false})).toBe('ok')
    })

    // Der Kern der Sache: Der letzte gute Stand bleibt stehen, der Hinweis kommt daneben.
    it('meldet einen veralteten Stand, wenn Daten da sind', () => {
        expect(syncStatus({failures: 1, hasData: true})).toBe('stale')
        expect(syncStatus({failures: 7, hasData: true})).toBe('stale')
    })

    it('meldet einen Fehler, wenn nie etwas ankam', () => {
        expect(syncStatus({failures: 1, hasData: false})).toBe('error')
    })

    it('verschwindet, sobald wieder ein Abruf durchgeht', () => {
        expect(syncStatus({failures: 0, hasData: true})).toBe('ok')
    })
})
