import {useEffect, useRef, useState} from 'react'
import {AthleteBoardMatch} from '@api/types.gen.ts'
import {formatElapsed, streamClockState} from '../streamClock.ts'
import useTicker from './useTicker.ts'

const CLOCK_TICK_MS = 100
const FREEZE_HOLD_MS = 5000
const CLOCK_FADE_MS = 400

export interface StreamClockDisplay {
    /** Ob die Uhr überhaupt im DOM stehen soll — erst nach Ende des Fade-outs `false`. */
    mounted: boolean
    /** Opacity-Ziel für die Text-Fade-Transition (CSS-Transition macht die Bewegung). */
    visible: boolean
    text: string | null
}

/**
 * Zustandsautomat der Laufuhr überm laufenden Lower-Third: Fade-in sobald der Lauf
 * einen gestempelten Start trägt (`actualStartTime`), 100-ms-Tick über die reine
 * [streamClockState], Einfrieren beim ersten „alle Boote gewertet/ausgeschieden",
 * danach 5 s halten und ausfaden — erst danach verschwindet die Uhr ganz aus dem DOM.
 *
 * Der eingefrorene Wert wird EINMAL übernommen und danach nie wieder aus
 * `streamClockState` gelesen: die Funktion ist rein und kennt keine Historie — ein
 * weiterer Aufruf mit fortschreitender Zeit würde den „eingefrorenen" Wert einfach
 * weiterlaufen lassen (siehe deren KDoc). Deshalb stoppt der 100-ms-Ticker selbst,
 * sobald ein Freeze-Wert feststeht.
 *
 * Ruhezustände (zwischen denen ausschließlich die Effekte unten wandern):
 * - UNMOUNTED  mounted=false, visible=false — kein Start gestempelt, oder ein
 *              vorheriger Fade-out ist fertig durchgelaufen.
 * - MOUNTING   mounted=true,  visible=false — genau einen Frame lang, bevor das
 *              Fade-in per rAF startet (die CSS-Transition braucht einen Ausgangswert).
 * - VISIBLE    mounted=true,  visible=true  — Uhr läuft oder zeigt den eingefrorenen
 *              Wert, noch innerhalb der 5-s-Haltezeit.
 * - FADING_OUT mounted=true,  visible=false, wasVisible.current=true — Haltezeit
 *              vorbei, Opacity läuft auf 0, der 400-ms-Timer räumt danach `mounted`.
 *
 * Übergang VISIBLE → UNMOUNTED ohne FADING_OUT: springt der Lauf auf einen NEUEN Match
 * OHNE eigenen Start (Kette schaltet weiter, RaceClocker hat den Start noch nicht
 * bestätigt), gibt es nichts zum Ausfaden — die alte Uhr gehörte zum alten Lauf. Der
 * matchId/hasStart-Reset-Effekt unten räumt `mounted`/`visible` in diesem Fall deshalb
 * SOFORT, statt sich auf den Fade-out-Timer zu verlassen: der armt nur, wenn
 * `wasVisible.current` beim Wechsel auf `visible=false` noch `true` ist — genau das
 * verhindert der Reset aber, sonst bliebe ein opacity:0-Knoten für immer im DOM stehen.
 */
const useStreamClockDisplay = (match: AthleteBoardMatch, clockOffsetMs: number): StreamClockDisplay => {
    const matchId = match.matchId
    const hasStart = match.actualStartTime != null
    const [frozenElapsedMs, setFrozenElapsedMs] = useState<number | null>(null)
    const [visible, setVisible] = useState(false)
    const [mounted, setMounted] = useState(false)
    // War die Uhr schon einmal sichtbar (fürs AKTUELLE Mounten)? Ohne diese Unter-
    // scheidung würde der Unmount-Timer schon direkt beim ersten Render (mounted=true,
    // visible=false, weil das Fade-in noch aussteht) anspringen — er darf aber nur nach
    // einem echten Fade-OUT feuern, nicht vor dem allerersten Fade-in.
    const wasVisible = useRef(false)

    // Ein anderer (oder gar kein) Lauf verwirft einen alten Freeze-Wert und die
    // "war schon sichtbar"-Markierung — der nächste echte Start fängt wieder bei einem
    // sauberen Fade-in von null an.
    useEffect(() => {
        setFrozenElapsedMs(null)
        wasVisible.current = false
        if (!hasStart) {
            // Sofortiger Sprung UNMOUNTED (siehe KDoc oben) — kein Fade-out für eine Uhr,
            // die zum neuen, startlosen Lauf nie sichtbar war. `hasStart` bewusst mit in
            // den Deps: die frühere Fassung reagierte nur auf `matchId` und ließ
            // `mounted`/`visible` unangetastet, wodurch der Fade-out-Timer unten nie
            // anspringen konnte (der braucht `wasVisible.current===true`, das der Reset
            // gerade eben auf `false` gesetzt hat) — ein opacity:0-Knoten blieb stehen.
            setVisible(false)
            setMounted(false)
        }
    }, [matchId, hasStart])

    const now = useTicker(CLOCK_TICK_MS, hasStart && frozenElapsedMs === null)
    const liveState = hasStart ? streamClockState(match, now, clockOffsetMs) : null

    useEffect(() => {
        if (liveState?.phase === 'frozen' && frozenElapsedMs === null) {
            setFrozenElapsedMs(liveState.elapsedMs)
        }
    }, [liveState, frozenElapsedMs])

    // Montieren, sobald ein Start gestempelt ist — bewusst OHNE `visible` im selben
    // Schritt: das Element muss erst einmal MIT opacity 0 gerendert werden, sonst hat
    // die CSS-Transition nichts, von dem aus sie starten könnte.
    useEffect(() => {
        if (!hasStart) {
            setVisible(false)
            return
        }
        setMounted(true)
    }, [hasStart, matchId])

    // Fade-in: erst NACH dem ersten Paint mit opacity 0 sichtbar schalten (ein Frame
    // später), damit der Browser tatsächlich von 0 auf 1 transitioniert. `hasStart`
    // zusätzlich geprüft: sonst würde ein Wechsel auf einen ANDEREN Lauf ohne eigenen
    // Start (Kette springt weiter, bevor RaceClocker den Start bestätigt) die Uhr aus
    // dem "gerade erst versteckt"-Zustand von effect oben sofort wieder sichtbar machen.
    useEffect(() => {
        if (!mounted || !hasStart) return
        const id = requestAnimationFrame(() => setVisible(true))
        return () => cancelAnimationFrame(id)
    }, [mounted, matchId, hasStart])

    useEffect(() => {
        if (frozenElapsedMs === null) return
        const holdTimer = setTimeout(() => setVisible(false), FREEZE_HOLD_MS)
        return () => clearTimeout(holdTimer)
    }, [frozenElapsedMs, matchId])

    // Erst nach Ende eines ECHTEN Fade-outs (war schon sichtbar, ist es jetzt nicht
    // mehr) ganz aus dem DOM nehmen — nicht schon während des allerersten Fade-ins.
    useEffect(() => {
        if (visible) {
            wasVisible.current = true
            return
        }
        if (!mounted || !wasVisible.current) return
        const unmountTimer = setTimeout(() => setMounted(false), CLOCK_FADE_MS)
        return () => clearTimeout(unmountTimer)
    }, [visible, mounted])

    if (!mounted) return {mounted: false, visible: false, text: null}
    const liveElapsedMs = liveState && liveState.phase !== 'hidden' ? liveState.elapsedMs : null
    const elapsedMs = frozenElapsedMs ?? liveElapsedMs ?? 0
    return {mounted: true, visible, text: formatElapsed(elapsedMs)}
}

export default useStreamClockDisplay
