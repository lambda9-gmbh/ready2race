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
