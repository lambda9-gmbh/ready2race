import {AthleteBoardMatch} from '@api/types.gen.ts'

/**
 * Zustand der Laufuhr im Stream-Overlay. `hidden` ohne laufenden Lauf oder ohne
 * gestempelten Start, `running` tickt, `frozen` steht auf dem letzten berechneten Wert.
 */
export type StreamClockState =
    | {phase: 'hidden'}
    | {phase: 'running'; elapsedMs: number}
    | {phase: 'frozen'; elapsedMs: number}

/**
 * Reine Zustandsfunktion der Laufuhr: actualStartTime + Serverzeitversatz -> Phase.
 * clockOffsetMs = clientNow - serverTime (beim Eintreffen der Antwort gemessen); die
 * server-korrigierte "jetzt"-Zeit ist also clientNowMs - clockOffsetMs.
 *
 * frozen, sobald alle Boote timeString tragen oder failed sind (und mind. eines
 * existiert) — elapsedMs wird dafür GENAUSO berechnet wie bei running. Das "Einfrieren"
 * ist kein eigener Rechenweg: die Funktion ist rein und kennt keine Historie. Wer sie
 * aufruft, ist dafür verantwortlich, nach dem Wechsel auf 'frozen' aufzuhören, mit
 * fortschreitendem clientNowMs erneut aufzurufen — sonst würde der "eingefrorene" Wert
 * bei jedem weiteren Aufruf weiterlaufen.
 */
export const streamClockState = (
    match: AthleteBoardMatch | null,
    clientNowMs: number,
    clockOffsetMs: number,
): StreamClockState => {
    if (match === null || match.actualStartTime == null) {
        return {phase: 'hidden'}
    }

    const startMs = new Date(match.actualStartTime).getTime()
    const serverNowMs = clientNowMs - clockOffsetMs
    const elapsedMs = Math.max(0, serverNowMs - startMs)

    const allTeamsDone =
        match.teams.length > 0 && match.teams.every(team => team.timeString != null || team.failed)

    return allTeamsDone ? {phase: 'frozen', elapsedMs} : {phase: 'running', elapsedMs}
}

/** 'm:ss.z' bzw. ab einer Stunde 'h:mm:ss.z' - Zehntel, Tabellenziffern-tauglich. */
export const formatElapsed = (elapsedMs: number): string => {
    const clamped = Math.max(0, elapsedMs)
    const totalTenths = Math.floor(clamped / 100)
    const tenths = totalTenths % 10
    const totalSeconds = Math.floor(totalTenths / 10)
    const seconds = totalSeconds % 60
    const totalMinutes = Math.floor(totalSeconds / 60)
    const minutes = totalMinutes % 60
    const hours = Math.floor(totalMinutes / 60)
    const pad2 = (n: number) => n.toString().padStart(2, '0')

    return hours > 0
        ? `${hours}:${pad2(minutes)}:${pad2(seconds)}.${tenths}`
        : `${minutes}:${pad2(seconds)}.${tenths}`
}
