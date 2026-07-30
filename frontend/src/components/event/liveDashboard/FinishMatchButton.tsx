import {useEffect, useRef, useState} from 'react'
import {Box, Button, CircularProgress} from '@mui/material'
import CheckIcon from '@mui/icons-material/Check'
import UndoIcon from '@mui/icons-material/Undo'
import {useTranslation} from 'react-i18next'

const UNDO_SECONDS = 5

type Props = {
    onFinish: () => Promise<void>
    disabled?: boolean
}

/**
 * "Lauf beenden" mit Bedenkzeit: der Aufruf geht erst nach {@link UNDO_SECONDS} Sekunden raus,
 * solange lässt er sich zurücknehmen. Ein versehentlicher Tipper am Steg zieht damit nicht sofort
 * die Aktivierung der nächsten Läufe nach sich.
 */
const FinishMatchButton = ({onFinish, disabled}: Props) => {
    const {t} = useTranslation()
    const [secondsLeft, setSecondsLeft] = useState<number | null>(null)
    const [submitting, setSubmitting] = useState(false)
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

    const clearTimer = () => {
        if (timerRef.current !== null) {
            clearInterval(timerRef.current)
            timerRef.current = null
        }
    }

    useEffect(() => clearTimer, [])

    const start = () => {
        setSecondsLeft(UNDO_SECONDS)
        clearTimer()
        timerRef.current = setInterval(() => {
            setSecondsLeft(prev => {
                if (prev === null) return null
                if (prev > 1) return prev - 1
                clearTimer()
                void submit()
                return null
            })
        }, 1000)
    }

    const submit = async () => {
        setSubmitting(true)
        await onFinish()
        setSubmitting(false)
    }

    const cancel = () => {
        clearTimer()
        setSecondsLeft(null)
    }

    if (secondsLeft !== null) {
        return (
            <Button
                variant="outlined"
                color="warning"
                size="small"
                startIcon={<UndoIcon />}
                onClick={cancel}>
                {`${t('event.liveDashboard.control.undo')} (${secondsLeft})`}
            </Button>
        )
    }

    return (
        <Button
            variant="contained"
            size="small"
            // dunkles Grün statt des hellen Theme-Tons: der Button muss auch bei Sonne stehen
            sx={{
                backgroundColor: 'success.dark',
                '&:hover': {backgroundColor: 'success.dark'},
            }}
            disabled={disabled || submitting}
            startIcon={
                submitting ? (
                    <Box display="flex">
                        <CircularProgress size={16} color="inherit" />
                    </Box>
                ) : (
                    <CheckIcon />
                )
            }
            onClick={start}>
            {submitting
                ? t('event.liveDashboard.control.finishing')
                : t('event.liveDashboard.control.finish')}
        </Button>
    )
}

export default FinishMatchButton
