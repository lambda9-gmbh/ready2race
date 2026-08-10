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
    /**
     * Zeitpunkt des letzten Scans in Millisekunden.
     *
     * Warum ein eigenes Feld statt „der neueste Code steht vorn": Die Reihenfolge der Liste
     * trägt im Umschalter die Position der Knöpfe. Würde der zuletzt gescannte Code nach vorn
     * wandern, verschöbe schon das Nachtragen des Anzeigenamens nach dem ersten Abruf die
     * Knöpfe unter dem Finger. Also bleibt die Reihenfolge die des ersten Scans (stabil), und
     * welche Person beim Öffnen angezeigt wird, entscheidet dieses Feld.
     */
    lastSeenAt?: number
}

export const MY_EVENT_STORAGE_KEY = 'my_event_codes'

const isCode = (value: unknown): value is MyEventCode =>
    typeof value === 'object' &&
    value !== null &&
    typeof (value as MyEventCode).qrCode === 'string' &&
    typeof (value as MyEventCode).eventId === 'string'

// Diese Seite wird per QR-Code auf beliebigen fremden Telefonen geöffnet. Im privaten Modus
// von Safari und bei abgeschaltetem Speicher wirft schon der bloße Zugriff auf localStorage
// eine Ausnahme — ohne Absicherung zerlegt das die ganze Ergebnisseite, nicht nur den Reiter
// „Mein Event". Deshalb kapseln wir jeden Zugriff hier und schlucken Fehler bewusst.
const safeGetItem = (key: string): string | null => {
    try {
        return localStorage.getItem(key)
    } catch {
        return null
    }
}

const safeSetItem = (key: string, value: string): void => {
    try {
        localStorage.setItem(key, value)
    } catch {
        // Speicher voll oder gesperrt: der Aufruf bleibt wirkungslos statt die Seite zu sprengen.
    }
}

export const readMyEventCodes = (): MyEventCode[] => {
    const raw = safeGetItem(MY_EVENT_STORAGE_KEY)
    if (!raw) return []
    try {
        const parsed: unknown = JSON.parse(raw)
        if (!Array.isArray(parsed)) return []
        // Ein einzelner kaputter Eintrag verwirft die ganze Liste: der Speicher ist Beiwerk,
        // ein neuer Scan stellt ihn in Sekunden wieder her, und halb gelesene Zustände
        // wären schwerer zu verstehen als ein leerer.
        return parsed.every(isCode) ? parsed : []
    } catch {
        return []
    }
}

const write = (codes: MyEventCode[]) => safeSetItem(MY_EVENT_STORAGE_KEY, JSON.stringify(codes))

// Einträge aus einer älteren Fassung tragen den Zeitstempel noch nicht; sie gelten als
// „lange nicht gesehen" und verlieren damit gegen jeden frisch gescannten Code.
const seenAt = (code: MyEventCode): number =>
    typeof code.lastSeenAt === 'number' ? code.lastSeenAt : 0

/**
 * Alle Codes dieser Veranstaltung in der Reihenfolge des ersten Scans. Die Reihenfolge ist
 * bewusst stabil — der Umschalter rendert sie unverändert, und ein Knopf, der seine Position
 * wechselt, wird auf dem Telefon danebengetippt.
 */
export const codesForEvent = (eventId: string): MyEventCode[] =>
    readMyEventCodes().filter(c => c.eventId === eventId)

/**
 * Die Person, die beim Öffnen gezeigt werden soll: die zuletzt gescannte. Wer auf demselben
 * Telefon das Band des zweiten Kindes scannt, will dessen Startzeit sehen und nicht die des
 * ersten. Bei Gleichstand (Alteinträge ohne Zeitstempel) gewinnt der spätere Listeneintrag.
 */
export const activeCodeForEvent = (eventId: string): MyEventCode | undefined =>
    codesForEvent(eventId).reduce<MyEventCode | undefined>(
        (best, code) => (best === undefined || seenAt(code) >= seenAt(best) ? code : best),
        undefined,
    )

export const rememberMyEventCode = (code: MyEventCode) => {
    const all = readMyEventCodes()
    const stamped = {...code, lastSeenAt: code.lastSeenAt ?? Date.now()}
    const index = all.findIndex(c => c.qrCode === code.qrCode)
    if (index < 0) {
        write([...all, stamped])
        return
    }
    // Ein erneuter Scan lässt den Eintrag an seiner Stelle stehen und behält den bereits
    // bekannten Anzeigenamen — der Scan selbst kennt ihn nicht.
    write(all.map((c, i) => (i === index ? {...c, ...stamped} : c)))
}

/**
 * Trägt den erst mit der Antwort bekannten Namen nach, ohne Position und Zeitstempel zu
 * berühren: das Nachtragen ist eine Ergänzung, kein neuer Scan.
 */
export const rememberMyEventDisplayName = (qrCode: string, displayName: string) => {
    const all = readMyEventCodes()
    const index = all.findIndex(c => c.qrCode === qrCode)
    if (index < 0 || all[index].displayName === displayName) {
        return
    }
    write(all.map((c, i) => (i === index ? {...c, displayName} : c)))
}

export const forgetMyEventCode = (qrCode: string) => {
    write(readMyEventCodes().filter(c => c.qrCode !== qrCode))
}
