import {useEffect, useState} from 'react'
import {Box, CircularProgress, IconButton, Menu, MenuItem, Tooltip} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {POLL_INTERVAL_OPTIONS_MS, POLL_INTERVAL_STORAGE_KEY} from './common.ts'

type Props = {
    intervalMs: number
    /** Timestamp of the last successful poll; the ring empties from here. */
    lastUpdated: Date | null
    onIntervalChange: (intervalMs: number) => void
}

/**
 * Ring that empties until the next poll, so it is obvious how fresh the data is.
 * Tapping it lets the referee pick the refresh rate; the choice is kept per device.
 */
const RefreshCountdown = ({intervalMs, lastUpdated, onIntervalChange}: Props) => {
    const {t} = useTranslation()
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
    const [now, setNow] = useState(() => Date.now())

    useEffect(() => {
        const id = setInterval(() => setNow(Date.now()), 250)
        return () => clearInterval(id)
    }, [])

    const elapsed = lastUpdated ? now - lastUpdated.getTime() : 0
    const remainingRatio = lastUpdated
        ? Math.max(0, Math.min(1, 1 - elapsed / intervalMs))
        : 1
    const secondsLeft = Math.max(0, Math.ceil((intervalMs - elapsed) / 1000))

    const handleSelect = (option: number) => {
        onIntervalChange(option)
        localStorage.setItem(POLL_INTERVAL_STORAGE_KEY, String(option))
        setAnchorEl(null)
    }

    return (
        <>
            <Tooltip
                title={t('event.liveDashboard.refresh.tooltip', {
                    seconds: secondsLeft,
                    interval: intervalMs / 1000,
                })}>
                <IconButton
                    size="small"
                    onClick={e => setAnchorEl(e.currentTarget)}
                    aria-label={t('event.liveDashboard.refresh.label')}
                    sx={{p: 0.5}}>
                    <Box position="relative" display="flex">
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
                            sx={{position: 'absolute', left: 0, color: 'primary.main'}}
                        />
                    </Box>
                </IconButton>
            </Tooltip>
            <Menu anchorEl={anchorEl} open={anchorEl !== null} onClose={() => setAnchorEl(null)}>
                {POLL_INTERVAL_OPTIONS_MS.map(option => (
                    <MenuItem
                        key={option}
                        selected={option === intervalMs}
                        onClick={() => handleSelect(option)}>
                        {t('event.liveDashboard.refresh.everySeconds', {seconds: option / 1000})}
                    </MenuItem>
                ))}
            </Menu>
        </>
    )
}

export default RefreshCountdown
