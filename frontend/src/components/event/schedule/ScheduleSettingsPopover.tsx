import {FormControlLabel, Stack, Switch, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import SettingsPopover from '@components/SettingsPopover.tsx'

type Props = {
    shortLabels: boolean
    toggleShortLabels: () => void
    openExecutionInNewTab: boolean
    setOpenExecutionInNewTab: (value: boolean) => void
}

/**
 * Das Einstellungs-Popover des Zeitplan-Tabs — ausschließlich Geräte-Schalter, die sofort wirken
 * und lokal bleiben (localStorage): ob der Sprung „Zur Durchführung" ein neues Fenster öffnet,
 * und ob Läufe am Kürzel oder am ausgeschriebenen Namen hängen (die geteilte Wahl mit dem
 * Schiedsrichter-Board, siehe shortLabels.ts).
 *
 * Der frühere „Veranstaltung"-Abschnitt (Laufkette, Folgerunden, Auto-Aktualisierung) ist seit dem
 * 12.08.2026 bewusst NICHT mehr hier: „Lauf beenden & nächsten aktivieren" ändert die
 * Veranstaltung für alle und macht man nicht nebenbei wie „Neues Fenster" — er wohnt jetzt im
 * Einstellungen-Tab der Veranstaltung (EventExecutionSettings). Damit gilt hier dasselbe Muster
 * wie am Dashboard-Popover: keine Abschnitts-Überschrift über nur einem Teil, stattdessen sagt
 * eine Zeile am Fuß einmal für alles, dass es nur dieses Gerät betrifft.
 */
const ScheduleSettingsPopover = ({
    shortLabels,
    toggleShortLabels,
    openExecutionInNewTab,
    setOpenExecutionInNewTab,
}: Props) => {
    const {t} = useTranslation()

    return (
        <SettingsPopover>
            {/* Die Schalter enger beieinander als die Fußzeile — dieselbe Dichte, die am
                Dashboard-Popover die SettingsSection herstellt. */}
            <Stack spacing={1}>
                <FormControlLabel
                    control={
                        <Switch
                            checked={openExecutionInNewTab}
                            onChange={(_, checked) => setOpenExecutionInNewTab(checked)}
                        />
                    }
                    label={t('event.settings.openExecutionInNewTab')}
                />
                <FormControlLabel
                    control={<Switch checked={shortLabels} onChange={toggleShortLabels} />}
                    label={t('event.settings.shortLabels')}
                />
            </Stack>
            {/* Einmal für alles statt einer „Dieses Gerät"-Überschrift (Muster vom 12.08.2026). */}
            <Typography variant={'caption'} sx={{color: 'text.secondary'}}>
                {t('event.settings.deviceHint')}
            </Typography>
        </SettingsPopover>
    )
}

export default ScheduleSettingsPopover
