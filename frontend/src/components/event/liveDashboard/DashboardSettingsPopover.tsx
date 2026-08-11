import {FormControlLabel, MenuItem, Switch, TextField} from '@mui/material'
import {useTranslation} from 'react-i18next'
import SettingsPopover, {SettingsSection} from '@components/SettingsPopover.tsx'
import {POLL_INTERVAL_OPTIONS_MS, POLL_INTERVAL_STORAGE_KEY} from './common.ts'

type Props = {
    shortLabels: boolean
    toggleShortLabels: () => void
    pollIntervalMs: number
    onPollIntervalChange: (intervalMs: number) => void
    compact: boolean
    setCompact: (value: boolean) => void
}

/**
 * Das Einstellungs-Popover des Schiedsrichter-Boards — dasselbe Zahnrad-Muster wie am
 * Zeitplan-Tab, hier durchgehend geräte-lokal: die Kurz-/Langform der Rennen (die geteilte Wahl
 * mit dem Zeitplan, siehe shortLabels.ts), der Abruftakt (bis zum 11.08.2026 im Countdown-Ring
 * versteckt — der bleibt in der Kopfzeile, aber nur noch als Anzeige) und die kompakte
 * Darstellung für kleine Bildschirme am Steg.
 */
const DashboardSettingsPopover = ({
    shortLabels,
    toggleShortLabels,
    pollIntervalMs,
    onPollIntervalChange,
    compact,
    setCompact,
}: Props) => {
    const {t} = useTranslation()

    const handleIntervalChange = (intervalMs: number) => {
        // Die Wahl gilt je Gerät und überlebt den Reload — dieselbe Ablage, aus der
        // `storedPollInterval` den Startwert liest.
        localStorage.setItem(POLL_INTERVAL_STORAGE_KEY, String(intervalMs))
        onPollIntervalChange(intervalMs)
    }

    return (
        <SettingsPopover>
            <SettingsSection title={t('event.settings.device')}>
                <FormControlLabel
                    control={<Switch checked={shortLabels} onChange={toggleShortLabels} />}
                    label={t('event.settings.shortLabels')}
                />
                <FormControlLabel
                    control={
                        <Switch checked={compact} onChange={(_, checked) => setCompact(checked)} />
                    }
                    label={t('event.settings.compact')}
                />
                <TextField
                    select
                    size={'small'}
                    label={t('event.liveDashboard.refresh.label')}
                    value={pollIntervalMs}
                    onChange={e => handleIntervalChange(Number(e.target.value))}
                    sx={{mt: 1}}>
                    {POLL_INTERVAL_OPTIONS_MS.map(option => (
                        <MenuItem key={option} value={option}>
                            {t('event.liveDashboard.refresh.everySeconds', {
                                seconds: option / 1000,
                            })}
                        </MenuItem>
                    ))}
                </TextField>
            </SettingsSection>
        </SettingsPopover>
    )
}

export default DashboardSettingsPopover
