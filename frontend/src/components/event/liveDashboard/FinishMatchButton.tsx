import {useEffect, useRef, useState} from 'react'
import {
    Box,
    Button,
    CircularProgress,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Stack,
} from '@mui/material'
import CheckIcon from '@mui/icons-material/Check'
import UndoIcon from '@mui/icons-material/Undo'
import {useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'
import {MatchResultStatus, matchResultStatuses} from '@utils/matchResultStatus.ts'

const UNDO_SECONDS = 5

type Props = {
    /** Der gewählte Status gilt für alle Boote ohne Ergebnis; null lässt sie offen. */
    onFinish: (openResults: MatchResultStatus | null) => Promise<void>
    /** Anzahl der Boote ohne Ergebnis; nur dann fragt der Button nach. */
    openTeamCount: number
    disabled?: boolean
}

const statusLabelKeys = {
    DNS: 'event.liveDashboard.control.openResults.DNS',
    DNF: 'event.liveDashboard.control.openResults.DNF',
    DSQ: 'event.liveDashboard.control.openResults.DSQ',
} as const satisfies Record<MatchResultStatus, string>

/**
 * "Lauf beenden" mit Bedenkzeit: der Aufruf geht erst nach {@link UNDO_SECONDS} Sekunden raus,
 * solange lässt er sich zurücknehmen. Ein versehentlicher Tipper am Steg zieht damit nicht sofort
 * die Aktivierung der nächsten Läufe nach sich.
 *
 * Fehlen noch Ergebnisse, wird vorher gefragt, was mit diesen Booten geschieht. Abmelden gehört
 * nicht dazu — das bleibt dem Regattabüro vorbehalten.
 */
const FinishMatchButton = ({onFinish, openTeamCount, disabled}: Props) => {
    const {t} = useTranslation()
    const [secondsLeft, setSecondsLeft] = useState<number | null>(null)
    const [submitting, setSubmitting] = useState(false)
    const [askOpenResults, setAskOpenResults] = useState(false)
    const openResultsRef = useRef<MatchResultStatus | null>(null)
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

    const clearTimer = () => {
        if (timerRef.current !== null) {
            clearInterval(timerRef.current)
            timerRef.current = null
        }
    }

    useEffect(() => clearTimer, [])

    const start = (openResults: MatchResultStatus | null) => {
        openResultsRef.current = openResults
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

    const handleClick = () => {
        if (openTeamCount > 0) {
            setAskOpenResults(true)
        } else {
            start(null)
        }
    }

    const chooseOpenResults = (openResults: MatchResultStatus | null) => {
        setAskOpenResults(false)
        start(openResults)
    }

    const submit = async () => {
        setSubmitting(true)
        await onFinish(openResultsRef.current)
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
        <>
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
                onClick={handleClick}>
                {submitting
                    ? t('event.liveDashboard.control.finishing')
                    : t('event.liveDashboard.control.finish')}
            </Button>
            <BaseDialog
                open={askOpenResults}
                maxWidth="xs"
                onClose={() => setAskOpenResults(false)}>
                <DialogTitle>{t('event.liveDashboard.control.openResults.title')}</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        {t('event.liveDashboard.control.openResults.question', {
                            count: openTeamCount,
                        })}
                    </DialogContentText>
                    {/* Ein Knopf je Status, untereinander: am Steg wird mit dem Daumen getroffen. */}
                    <Stack spacing={1} sx={{mt: 2}}>
                        {matchResultStatuses.map(status => (
                            <Button
                                key={status}
                                variant="outlined"
                                onClick={() => chooseOpenResults(status)}>
                                {`${status} — ${t(statusLabelKeys[status])}`}
                            </Button>
                        ))}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAskOpenResults(false)}>
                        {t('common.cancel')}
                    </Button>
                    <Button onClick={() => chooseOpenResults(null)}>
                        {t('event.liveDashboard.control.openResults.keepOpen')}
                    </Button>
                </DialogActions>
            </BaseDialog>
        </>
    )
}

export default FinishMatchButton
