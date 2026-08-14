import {useRef} from 'react'
import {Box, Typography, useTheme} from '@mui/material'
import {AthleteBoardMatch} from '@api/types.gen.ts'
import {formatElapsed, streamClockState} from '../streamClock.ts'
import {solidOr} from './streamDisplay.ts'
import useTicker from './useTicker.ts'

interface ClockPlateProps {
    match: AthleteBoardMatch | null
    clockOffsetMs: number
}

/**
 * Modus „Nur Laufuhr": eine kompakte, separat croppbare Uhr-Platte unten links —
 * dieselbe Stelle wie das Lower-Third, damit beide Quellen im Zweifel deckungsgleich
 * liegen.
 *
 * Anders als die Uhr im Lower-Third ist diese Platte DAUERHAFT eingeblendet (Wunsch
 * vom 14.08.): vor dem Start steht 0:00.0, während des Laufs tickt sie, mit dem
 * letzten gewerteten Boot friert sie ein und bleibt stehen, bis der nächste Lauf sie
 * wieder auf 0:00.0 zurücksetzt. Kein Fade — die Regie verlässt sich darauf, dass die
 * Quelle immer da ist.
 */
const ClockPlate = ({match, clockOffsetMs}: ClockPlateProps) => {
    const theme = useTheme()
    // Der 100-ms-Tick läuft nur, solange die Uhr wirklich zählt — eingefroren oder
    // wartend genügt der Datenstand des Polls.
    const running = streamClockState(match, Date.now(), clockOffsetMs).phase === 'running'
    const now = useTicker(100, running)
    const state = streamClockState(match, now, clockOffsetMs)

    // Einfrieren heißt festhalten: streamClockState ist eine reine Funktion, ihr
    // elapsedMs liefe sonst weiter — der erste frozen-Wert bleibt stehen, bis wieder
    // ein Lauf tickt (dann beginnt die nächste Messung bei dessen Startzeit).
    const frozenMs = useRef<number | null>(null)
    if (state.phase === 'frozen') {
        frozenMs.current ??= state.elapsedMs
    } else {
        frozenMs.current = null
    }

    const text = formatElapsed(
        state.phase === 'frozen'
            ? (frozenMs.current ?? state.elapsedMs)
            : state.phase === 'running'
              ? state.elapsedMs
              : 0,
    )

    return (
        <Box sx={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'flex-end'}}>
            <Box
                sx={{
                    m: 3,
                    display: 'flex',
                    borderRadius: 2,
                    overflow: 'hidden',
                    backgroundColor: solidOr(theme.palette.text.primary, '#1d1d1d'),
                    color: solidOr(theme.palette.background.paper, '#ffffff'),
                }}>
                <Box
                    sx={{
                        width: '0.6rem',
                        flexShrink: 0,
                        backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                    }}
                />
                <Box sx={{px: 3, py: 1.5}}>
                    <Typography
                        variant="h2"
                        sx={{fontWeight: 700, fontVariantNumeric: 'tabular-nums'}}>
                        {text}
                    </Typography>
                </Box>
            </Box>
        </Box>
    )
}

export default ClockPlate
