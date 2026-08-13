import {Typography} from '@mui/material'
import {AthleteBoardMatch} from '@api/types.gen.ts'
import useStreamClockDisplay from './useStreamClockDisplay.ts'

interface StreamClockLabelProps {
    match: AthleteBoardMatch
    clockOffsetMs: number
}

/**
 * Laufuhr im Kopf des laufenden Lower-Thirds: zehntelgenau, rechtsbündig, tabular-nums.
 * Fade-in/-out laufen ausschließlich als TEXT-Opacity auf dem bereits deckenden Panel —
 * die Chroma-Fläche selbst blitzt dabei nie durch (siehe useStreamClockDisplay).
 */
const StreamClockLabel = ({match, clockOffsetMs}: StreamClockLabelProps) => {
    const clock = useStreamClockDisplay(match, clockOffsetMs)
    if (!clock.mounted) return null
    return (
        <Typography
            variant="h3"
            sx={{
                fontWeight: 700,
                fontVariantNumeric: 'tabular-nums',
                textAlign: 'right',
                flexShrink: 0,
                opacity: clock.visible ? 1 : 0,
                transition: 'opacity 400ms ease-out',
            }}>
            {clock.text}
        </Typography>
    )
}

export default StreamClockLabel
