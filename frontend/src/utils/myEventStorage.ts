/**
 * Die über den QR-Code am Band eingestiegenen Personen, gemerkt auf diesem Gerät.
 *
 * Bewusst eine Liste und kein Einzelwert: Eltern mit mehreren Kindern und Betreuende scannen
 * mehrere Bänder, und ein Einzelwert würde sich gegenseitig überschreiben. Der Code steht
 * absichtlich nur hier und nicht in der URL — so wandert kein personenbezogener Link durch
 * Chatgruppen.
 */
export type MyEventCode = {
    qrCode: string
    eventId: string
    displayName?: string
}

export const MY_EVENT_STORAGE_KEY = 'my_event_codes'

const isCode = (value: unknown): value is MyEventCode =>
    typeof value === 'object' &&
    value !== null &&
    typeof (value as MyEventCode).qrCode === 'string' &&
    typeof (value as MyEventCode).eventId === 'string'

export const readMyEventCodes = (): MyEventCode[] => {
    const raw = localStorage.getItem(MY_EVENT_STORAGE_KEY)
    if (!raw) return []
    try {
        const parsed: unknown = JSON.parse(raw)
        if (!Array.isArray(parsed)) return []
        // Ein einzelner kaputter Eintrag verwirft die ganze Liste: der Speicher ist Beiwerk,
        // ein neuer Scan stellt ihn in Sekunden wieder her, und halb gelesene Zustaende
        // waeren schwerer zu verstehen als ein leerer.
        return parsed.every(isCode) ? parsed : []
    } catch {
        return []
    }
}

const write = (codes: MyEventCode[]) =>
    localStorage.setItem(MY_EVENT_STORAGE_KEY, JSON.stringify(codes))

export const codesForEvent = (eventId: string): MyEventCode[] =>
    readMyEventCodes().filter(c => c.eventId === eventId)

export const rememberMyEventCode = (code: MyEventCode) => {
    const others = readMyEventCodes().filter(c => c.qrCode !== code.qrCode)
    write([...others, code])
}

export const forgetMyEventCode = (qrCode: string) => {
    write(readMyEventCodes().filter(c => c.qrCode !== qrCode))
}
