import {useEffect, useState} from 'react'
import {Box, CircularProgress, Tooltip} from '@mui/material'
import {useTranslation} from 'react-i18next'

type Props = {
    intervalMs: number
    /** Timestamp of the last successful poll; the ring empties from here. */
    lastUpdated: Date | null
}

/**
 * Ring that empties until the next poll, so it is obvious how fresh the data is.
 *
 * Reine Anzeige: Der Abruftakt wird seit dem 11.08.2026 im Einstellungs-Popover der Kopfzeile
 * gewählt (siehe DashboardSettingsPopover) — vorher öffnete ein Klick auf den Ring ein Menü, das
 * dort niemand vermutete.
 */
const RefreshCountdown = ({intervalMs, lastUpdated}: Props) => {
    const {t} = useTranslation()
    const [now, setNow] = useState(() => Date.now())

    useEffect(() => {
        const id = setInterval(() => setNow(Date.now()), 250)
        return () => clearInterval(id)
    }, [])

    const elapsed = lastUpdated ? now - lastUpdated.getTime() : 0
    const remainingRatio = lastUpdated ? Math.max(0, Math.min(1, 1 - elapsed / intervalMs)) : 1
    const secondsLeft = Math.max(0, Math.ceil((intervalMs - elapsed) / 1000))

    return (
        <Tooltip
            title={t('event.liveDashboard.refresh.tooltip', {
                seconds: secondsLeft,
                interval: intervalMs / 1000,
            })}>
            <Box
                position="relative"
                display="flex"
                sx={{p: 0.5}}
                aria-label={t('event.liveDashboard.refresh.label')}>
                {/* Track behind the countdown so the remaining share stays readable */}
                <CircularProgress
                    variant="determinate"
                    value={100}
                    size={18}
                    thickness={5}
                    sx={{color: 'action.disabledBackground'}}
                />
                <CircularProgress
                    variant="determinate"
                    value={remainingRatio * 100}
                    size={18}
                    thickness={5}
                    sx={{position: 'absolute', left: 4, top: 4, color: 'primary.main'}}
                />
            </Box>
        </Tooltip>
    )
}

export default RefreshCountdown
