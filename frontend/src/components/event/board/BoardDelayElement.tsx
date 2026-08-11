import {Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {BoardViewDto} from '@api/types.gen'
import {delayParts} from '@utils/scheduleDelay'

interface BoardDelayElementProps {
    view: BoardViewDto
}

/**
 * Verspätungs-Element: die aktuelle Abweichung vom Zeitplan, groß genug für die andere
 * Seite des Stegs — nach dem Vorbild der Uhr (BoardClockElement). Die Zahl kommt fertig
 * vom Server (`currentDelaySeconds`, Stand des zuletzt gestarteten Laufs); hier wird nur
 * gerundet und eingefärbt: Verspätung warnfarben, Verfrühung und „pünktlich" dezent.
 */
const BoardDelayElement = ({view}: BoardDelayElementProps) => {
    const {t} = useTranslation()

    const seconds = view.currentDelaySeconds
    const parts = seconds != null ? delayParts(seconds) : null

    const label =
        parts == null
            ? t('event.boards.delay.noStarts')
            : parts.kind === 'onTime'
              ? t('event.boards.delay.onTime')
              : `${parts.kind === 'late' ? '+' : '−'}${parts.minutes} min`

    return (
        <Stack
            sx={{height: '100%', minHeight: 0}}
            alignItems="center"
            justifyContent="center"
            gap="clamp(0.2rem, 0.5vh, 0.8rem)">
            <Typography
                sx={{
                    fontSize:
                        parts == null
                            ? 'clamp(1.2rem, 3vw, 4.5rem)'
                            : 'clamp(2rem, 6vw, 9rem)',
                    fontWeight: 800,
                    lineHeight: 1,
                    textAlign: 'center',
                }}
                color={parts?.kind === 'late' ? 'warning.main' : 'text.secondary'}>
                {label}
            </Typography>
            <Typography
                sx={{fontSize: 'clamp(0.75rem, 1.4vw, 2rem)', textAlign: 'center'}}
                color="text.secondary">
                {t('event.boards.delay.subtitle')}
            </Typography>
        </Stack>
    )
}

export default BoardDelayElement
