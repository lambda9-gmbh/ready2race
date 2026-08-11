import {describe, expect, it} from 'vitest'
import {
    DISMISSED_KEY,
    dismissHint,
    isHintDismissed,
    isIosSafari,
    isStandalone,
    WebStorageLike,
} from './installHint.ts'

const fakeStore = () => {
    const data = new Map<string, string>()
    return {
        getItem: (k: string) => data.get(k) ?? null,
        setItem: (k: string, v: string) => void data.set(k, v),
        removeItem: (k: string) => void data.delete(k),
        size: () => data.size,
    }
}

// Ein Speicher, wie ihn Safari im privaten Modus liefern kann: jede Operation wirft.
const brokenStore: WebStorageLike = {
    getItem: () => {
        throw new Error('kaputt')
    },
    setItem: () => {
        throw new Error('kaputt')
    },
    removeItem: () => {
        throw new Error('kaputt')
    },
}

const UA_IOS_SAFARI =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
const UA_IPAD_SAFARI =
    'Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
const UA_IOS_CHROME =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/126.0.6478.54 Mobile/15E148 Safari/604.1'
const UA_IOS_FIREFOX =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/127.0 Mobile/15E148 Safari/605.1.15'
const UA_IOS_WEBVIEW =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148'
const UA_ANDROID_CHROME =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36'
const UA_MAC_SAFARI =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15'

describe('isStandalone', () => {
    it('erkennt den standalone-Anzeigemodus über die Media-Query', () => {
        expect(isStandalone({matchMedia: () => ({matches: true})})).toBe(true)
    })

    it('erkennt iOS über navigator.standalone, auch ohne Media-Query-Treffer', () => {
        expect(
            isStandalone({matchMedia: () => ({matches: false}), navigatorStandalone: true}),
        ).toBe(true)
    })

    it('meldet im Browser-Tab: nicht installiert', () => {
        expect(
            isStandalone({matchMedia: () => ({matches: false}), navigatorStandalone: false}),
        ).toBe(false)
    })

    it('fällt ohne matchMedia-Unterstützung auf „nicht installiert“ zurück', () => {
        expect(isStandalone({})).toBe(false)
    })

    it('fängt eine werfende matchMedia-Implementierung ab', () => {
        expect(
            isStandalone({
                matchMedia: () => {
                    throw new Error('kaputt')
                },
            }),
        ).toBe(false)
    })
})

describe('isIosSafari', () => {
    it('erkennt Safari auf dem iPhone', () => {
        expect(isIosSafari(UA_IOS_SAFARI)).toBe(true)
    })

    it('erkennt Safari auf dem iPad', () => {
        expect(isIosSafari(UA_IPAD_SAFARI)).toBe(true)
    })

    it('lehnt Chrome auf iOS ab - dessen Teilen-Menü sieht anders aus', () => {
        expect(isIosSafari(UA_IOS_CHROME)).toBe(false)
    })

    it('lehnt Firefox auf iOS ab', () => {
        expect(isIosSafari(UA_IOS_FIREFOX)).toBe(false)
    })

    it('lehnt In-App-WebViews ohne Safari-Kennung ab', () => {
        expect(isIosSafari(UA_IOS_WEBVIEW)).toBe(false)
    })

    it('lehnt Chrome auf Android ab - dort kommt der Install-Button', () => {
        expect(isIosSafari(UA_ANDROID_CHROME)).toBe(false)
    })

    it('lehnt Safari auf dem Mac ab', () => {
        expect(isIosSafari(UA_MAC_SAFARI)).toBe(false)
    })

    it('zeigt bei leerem User-Agent im Zweifel nichts', () => {
        expect(isIosSafari('')).toBe(false)
    })
})

describe('Wegklick-Merker', () => {
    it('ist anfangs nicht gesetzt', () => {
        expect(isHintDismissed(fakeStore())).toBe(false)
    })

    it('bleibt nach dem Wegklicken dauerhaft gesetzt', () => {
        const store = fakeStore()
        dismissHint(store)
        expect(isHintDismissed(store)).toBe(true)
        expect(store.getItem(DISMISSED_KEY)).not.toBeNull()
    })

    it('wertet einen werfenden Speicher als „nicht weggeklickt“', () => {
        expect(isHintDismissed(brokenStore)).toBe(false)
    })

    it('wirft beim Wegklicken nicht, wenn der Speicher ablehnt', () => {
        expect(() => dismissHint(brokenStore)).not.toThrow()
    })
})
