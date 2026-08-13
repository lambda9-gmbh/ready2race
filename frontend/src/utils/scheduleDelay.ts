/**
 * Verspätungsrechnung, geteilt zwischen dem Verspätungs-Element der Boards und dem Chip im
 * Verwaltungs-Zeitplan — eine Regel, ein Ort. Das Backend rechnet dieselbe Zahl in
 * `BoardLogic.currentDelaySeconds` (Boards bekommen sie fertig geliefert); der Zeitplan-Tab
 * hat die Ist-Starts bereits in seinen Slots und rechnet deshalb hier selbst, statt einen
 * eigenen Endpoint zu bekommen.
 */

export type DelayKind = 'late' | 'early' | 'onTime'

/**
 * Rundung auf ganze Minuten; unter ±60 Sekunden gilt der Zeitplan als eingehalten —
 * eine Anzeige, die zwischen „+1 min" und „pünktlich" flackert, hilft niemandem.
 */
export const delayParts = (seconds: number): {kind: DelayKind; minutes: number} => {
    if (Math.abs(seconds) < 60) return {kind: 'onTime', minutes: 0}
    const minutes = Math.round(Math.abs(seconds) / 60)
    return {kind: seconds > 0 ? 'late' : 'early', minutes}
}

/**
 * Die Farbe der großen Zahl im Verspätungs-Element als Ampel-Schema (Nutzerentscheidung
 * 12.08.2026): Verzug warnfarben (Handlungsbedarf), „pünktlich" grün (alles gut — die
 * Bestätigung, für die der Bildschirm hängt), Verfrühung Info-Blau. Die Verfrühung stand
 * zuvor bewusst grau (kein Alarm, kein drittes Signalblau neben den primary-Chips) —
 * wirkte auf der Anzeigetafel aber wie ungestylt; jede Lage soll auf einen Blick als
 * Zustand erkennbar sein, dem ist der frühere Einwand untergeordnet.
 */
export const delayColor = (kind: DelayKind): string =>
    kind === 'late' ? 'warning.main' : kind === 'onTime' ? 'success.main' : 'info.main'

/**
 * Dieselbe Ampel als MUI-Chip-Farbe für den Verspätungs-Chip am Zeitplan-Kopf — eine
 * Quelle für beide Anzeigen; der Chip hatte zwischenzeitlich seine eigene Zuordnung
 * (Verzug warnfarben, alles andere grau) und lief damit vom Board-Element weg.
 */
export const delayChipColor = (kind: DelayKind): 'warning' | 'success' | 'info' =>
    kind === 'late' ? 'warning' : kind === 'onTime' ? 'success' : 'info'

/**
 * `started_at − start_time` des zuletzt (nach Ist-Start) gestarteten Eintrags — dieselbe
 * Auswahlregel wie im Backend: der zuletzt GESTARTETE zählt, nicht der zuletzt geplante.
 * Null, wenn noch nichts gestartet ist oder der zuletzt gestartete Eintrag keine geplante
 * Zeit trägt.
 */
export const latestStartDelaySeconds = (
    entries: {startTime?: string | null; startedAt?: string | null}[],
): number | null => {
    let latest: {startTime?: string | null; startedAt: string} | null = null
    for (const entry of entries) {
        if (entry.startedAt == null) continue
        if (latest == null || entry.startedAt > latest.startedAt) {
            latest = {startTime: entry.startTime, startedAt: entry.startedAt}
        }
    }
    if (latest == null || latest.startTime == null) return null
    return (new Date(latest.startedAt).getTime() - new Date(latest.startTime).getTime()) / 1000
}
