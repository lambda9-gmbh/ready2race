import {Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'

interface AthleteBoardPenaltyNoteProps {
    penaltySeconds?: number | null
    penaltyNote?: string | null
}

/**
 * Hinweis auf eine Zeitstrafe unter der Zeit. Die Strafe wird ausgewiesen, nicht verrechnet:
 * die angezeigte Zeit enthält sie bereits (siehe Migration V202607301500 im Backend). Deshalb
 * steht hier "inkl. …" und keine zweite Zahl.
 */
const AthleteBoardPenaltyNote = ({penaltySeconds, penaltyNote}: AthleteBoardPenaltyNoteProps) => {
    const {t} = useTranslation()

    if (penaltySeconds == null && !penaltyNote) {
        return null
    }

    const label =
        penaltySeconds != null
            ? t('event.info.athleteBoard.penalty', {seconds: penaltySeconds})
            : null

    return (
        <Typography
            sx={{fontSize: 'clamp(0.65rem, 1vw, 0.9rem)', lineHeight: 1.2}}
            color="warning.dark">
            {[label, penaltyNote].filter(Boolean).join(' · ')}
        </Typography>
    )
}

export default AthleteBoardPenaltyNote
