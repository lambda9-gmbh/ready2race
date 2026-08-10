import {Chip} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {MatchChip} from '@components/event/match/matchStatusChip.ts'

/**
 * Ein [MatchChip] als MUI-Chip. Welcher Chip es ist, entscheidet ausschließlich
 * `matchStatusChip.ts` — hier wird nur noch übersetzt und gemalt.
 *
 * Bewusst geteilt zwischen Durchführungsseite und öffentlicher Ergebnisanzeige: derselbe Zustand
 * soll nicht nur dasselbe Wort, sondern auch dieselbe Farbe und dieselbe Form haben.
 */
const StatusChip = ({chip}: {chip: MatchChip | null}) => {
    const {t} = useTranslation()
    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die gelockerte Signatur - dasselbe
    // Muster wie `stateChipProps` in EventSchedule.tsx.
    const translate = t as (key: string, values?: Record<string, string | number>) => string
    // null heißt "dieser Chip sagt hier nichts aus" (z.B. der Arena-Chip ohne erhobene
    // Check-in-Daten) - dann gar nichts zeigen, statt eine leere Hülle.
    if (!chip) return null
    return (
        <Chip
            size={'small'}
            label={translate(chip.labelKey, chip.values)}
            color={chip.color}
            sx={chip.strikeThrough ? {textDecoration: 'line-through'} : undefined}
        />
    )
}

export default StatusChip
