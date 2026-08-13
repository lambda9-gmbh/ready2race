import {
    Checkbox,
    FormControlLabel,
    ListItemText,
    MenuItem,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import SettingsPopover, {SettingsSection} from '@components/SettingsPopover.tsx'
import {
    dashboardCompetitionFilterKey,
    DASHBOARD_SHOW_CHECKS_KEY,
    DASHBOARD_FOLLOW_CURRENT_KEY,
    DASHBOARD_FONT_SCALE_KEY,
    DASHBOARD_HIDE_FINISHED_KEY,
    DASHBOARD_NOTE_PREVIEW_KEY,
    DASHBOARD_ONLY_TODAY_KEY,
    DASHBOARD_SHOW_CREW_KEY,
    useDeviceChoice,
    useDeviceFlag,
    useDeviceList,
} from '@components/event/deviceSettings.ts'
import {
    DASHBOARD_FONT_SCALES,
    DashboardCompetitionOption,
    KEEP_FINISHED_CONTEXT,
    POLL_INTERVAL_OPTIONS_MS,
    POLL_INTERVAL_STORAGE_KEY,
} from './common.ts'

type Props = {
    /** Für den je Veranstaltung eigenen Speicher-Schlüssel des Wettkampf-Filters. */
    eventId: string
    /** Die wählbaren Wettkämpfe, abgeleitet aus den im Dashboard vorhandenen Läufen. */
    competitionOptions: DashboardCompetitionOption[]
    /** Von außen gesteuert, damit auch der Filter-Chip in der Kopfzeile das Popover öffnet. */
    open: boolean
    onOpenChange: (open: boolean) => void
    shortLabels: boolean
    toggleShortLabels: () => void
    pollIntervalMs: number
    onPollIntervalChange: (intervalMs: number) => void
    compact: boolean
    setCompact: (value: boolean) => void
}

/**
 * Das Einstellungs-Popover des Schiedsrichter-Boards — dasselbe Zahnrad-Muster wie am
 * Zeitplan-Tab, hier durchgehend geräte-lokal. Seit dem 12.08.2026 in vier Abschnitten:
 * „Fokus" (Wettkampf-Filter, Tagesfilter, Beendete, Folgen), „Anzeige" (Kurznamen,
 * Notiz-Vorschau, Aufstellung, Prüfungs-Icons), „Lesbarkeit" (Kompaktmodus, Schriftgröße) und
 * „Aktualisierung" (Abruftakt). Der Kompaktmodus steht bewusst bei der Schriftgröße: die beiden
 * verrechnen sich (siehe dashboardTypographySizes) und gehören nebeneinander bedient.
 *
 * Einen Abschnitt „Dieses Gerät" gibt es hier bewusst NICHT (Rückmeldung vom 12.08.2026): Auf
 * diesem Board gilt ausnahmslos alles geräte-lokal, die Überschrift über nur einem Teil
 * suggerierte fälschlich, die übrigen Abschnitte gälten für die Veranstaltung. Stattdessen sagt
 * es eine Zeile am Fuß einmal für alle. Anders am Zeitplan-Popover: Dort gibt es die Trennung
 * Gerät/Veranstaltung wirklich, und dort bleibt sie.
 *
 * Die Schalter der neuen Abschnitte lesen und schreiben ihre Werte selbst über die
 * deviceSettings-Hooks — die Seite liest dieselben Schlüssel, das Fenster-Ereignis hält beide
 * synchron, ohne dass jeder Wert einzeln durchgereicht werden muss.
 */
const DashboardSettingsPopover = ({
    eventId,
    competitionOptions,
    open,
    onOpenChange,
    shortLabels,
    toggleShortLabels,
    pollIntervalMs,
    onPollIntervalChange,
    compact,
    setCompact,
}: Props) => {
    const {t} = useTranslation()

    // --- Fokus: dieselben Schlüssel, die LiveDashboardPage auf die Daten anwendet -------------
    const [competitionFilter, setCompetitionFilter] = useDeviceList(
        dashboardCompetitionFilterKey(eventId),
    )
    // Standard „nur heute": Bis zum 12.08.2026 zeigte die Läufe-Spalte immer alle Tage — an
    // einer Mehrtagesregatta scrollte man damit ständig durch fremde Tage, und der
    // Zeitstrahl-Indikator darüber zeigt ohnehin nur einen. Wer alles sehen will, schaltet um.
    const [onlyToday, setOnlyToday] = useDeviceFlag(DASHBOARD_ONLY_TODAY_KEY, true)
    const [hideFinished, setHideFinished] = useDeviceFlag(DASHBOARD_HIDE_FINISHED_KEY, false)
    const [followCurrent, setFollowCurrent] = useDeviceFlag(DASHBOARD_FOLLOW_CURRENT_KEY, false)

    // --- Anzeige: der Detailgrad der Bootszeilen, alles standardmäßig an -----------------------
    const [notePreview, setNotePreview] = useDeviceFlag(DASHBOARD_NOTE_PREVIEW_KEY, true)
    const [showCrew, setShowCrew] = useDeviceFlag(DASHBOARD_SHOW_CREW_KEY, true)
    const [showChecks, setShowChecks] = useDeviceFlag(DASHBOARD_SHOW_CHECKS_KEY, true)

    // --- Lesbarkeit: die Schriftstufe -----------------------------------------------------------
    const [fontScale, setFontScale] = useDeviceChoice(
        DASHBOARD_FONT_SCALE_KEY,
        'normal',
        DASHBOARD_FONT_SCALES,
    )

    const optionLabelById = new Map(
        competitionOptions.map(option => [option.competitionId, option.label]),
    )

    const handleIntervalChange = (intervalMs: number) => {
        // Die Wahl gilt je Gerät und überlebt den Reload — dieselbe Ablage, aus der
        // `storedPollInterval` den Startwert liest.
        localStorage.setItem(POLL_INTERVAL_STORAGE_KEY, String(intervalMs))
        onPollIntervalChange(intervalMs)
    }

    return (
        <SettingsPopover open={open} onOpenChange={onOpenChange}>
            <SettingsSection title={t('event.liveDashboard.settings.focus')}>
                {/* Mehrfachauswahl; leer heißt „alle". Die Optionen kommen aus den gerade
                    vorhandenen Läufen — eine gespeicherte Wahl, deren Wettkampf hier fehlt,
                    bleibt gespeichert und filtert einfach nichts Zusätzliches weg. */}
                <TextField
                    select
                    size={'small'}
                    label={t('event.liveDashboard.settings.competitionFilter.label')}
                    value={competitionFilter}
                    onChange={e =>
                        // Bei multiple liefert MUI das Array direkt — der generierte
                        // Event-Typ weiß davon nichts, daher die Zusicherung.
                        setCompetitionFilter(e.target.value as unknown as string[])
                    }
                    slotProps={{
                        select: {
                            multiple: true,
                            displayEmpty: true,
                            renderValue: selected => {
                                const ids = selected as string[]
                                return ids.length === 0
                                    ? t('event.liveDashboard.settings.competitionFilter.all')
                                    : ids
                                          .map(id => optionLabelById.get(id) ?? '…')
                                          .join(', ')
                            },
                        },
                        inputLabel: {shrink: true},
                    }}>
                    {competitionOptions.map(option => (
                        <MenuItem key={option.competitionId} value={option.competitionId}>
                            <Checkbox
                                size="small"
                                checked={competitionFilter.includes(option.competitionId)}
                            />
                            <ListItemText primary={option.label} />
                        </MenuItem>
                    ))}
                    {competitionOptions.length === 0 && (
                        <MenuItem disabled>
                            {t('event.liveDashboard.settings.competitionFilter.empty')}
                        </MenuItem>
                    )}
                </TextField>
                <FormControlLabel
                    control={
                        <Switch
                            checked={onlyToday}
                            onChange={(_, checked) => setOnlyToday(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.onlyToday')}
                />
                <FormControlLabel
                    control={
                        <Switch
                            checked={hideFinished}
                            onChange={(_, checked) => setHideFinished(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.hideFinished', {
                        count: KEEP_FINISHED_CONTEXT,
                    })}
                />
                <FormControlLabel
                    control={
                        <Switch
                            checked={followCurrent}
                            onChange={(_, checked) => setFollowCurrent(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.followCurrent')}
                />
            </SettingsSection>
            <SettingsSection title={t('event.liveDashboard.settings.display')}>
                <FormControlLabel
                    control={<Switch checked={shortLabels} onChange={toggleShortLabels} />}
                    label={t('event.settings.shortLabels')}
                />
                <FormControlLabel
                    control={
                        <Switch
                            checked={notePreview}
                            onChange={(_, checked) => setNotePreview(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.notePreview')}
                />
                {/* Radikaler als der Kompaktmodus: aus heißt, die Crew-Zeilen fehlen ganz. */}
                <FormControlLabel
                    control={
                        <Switch
                            checked={showCrew}
                            onChange={(_, checked) => setShowCrew(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.showCrew')}
                />
                {/* Aus räumt die Icon-Spalte komplett aus den Bootszeilen — Platz fürs Telefon. */}
                <FormControlLabel
                    control={
                        <Switch
                            checked={showChecks}
                            onChange={(_, checked) => setShowChecks(checked)}
                        />
                    }
                    label={t('event.liveDashboard.settings.showChecks')}
                />
            </SettingsSection>
            <SettingsSection title={t('event.liveDashboard.settings.readability')}>
                <FormControlLabel
                    control={
                        <Switch checked={compact} onChange={(_, checked) => setCompact(checked)} />
                    }
                    label={t('event.settings.compact')}
                />
                {/* Neben dem Kompaktmodus, nicht statt seiner: Kompakt verdichtet, die Stufe
                    skaliert — „kompakt + groß" ist am Steg die übliche Kombination. */}
                <TextField
                    select
                    size={'small'}
                    label={t('event.liveDashboard.settings.fontSize.label')}
                    value={fontScale}
                    onChange={e =>
                        setFontScale(e.target.value as (typeof DASHBOARD_FONT_SCALES)[number])
                    }
                    sx={{mt: 1}}>
                    {DASHBOARD_FONT_SCALES.map(scale => (
                        <MenuItem key={scale} value={scale}>
                            {t(`event.liveDashboard.settings.fontSize.${scale}`)}
                        </MenuItem>
                    ))}
                </TextField>
            </SettingsSection>
            <SettingsSection title={t('event.liveDashboard.settings.refresh')}>
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
            {/* Einmal für alle Abschnitte statt einer Überschrift über nur einem Teil — der Text
                ist geteilt mit dem Zeitplan-Popover (event.settings.deviceHint). */}
            <Typography variant="caption" sx={{color: 'text.secondary'}}>
                {t('event.settings.deviceHint')}
            </Typography>
        </SettingsPopover>
    )
}

export default DashboardSettingsPopover
