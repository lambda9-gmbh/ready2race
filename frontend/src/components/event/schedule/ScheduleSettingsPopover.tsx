import {useState} from 'react'
import {
    Button,
    FormControlLabel,
    MenuItem,
    Stack,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {ChainProgressionMode, EventDto} from '@api/types.gen.ts'
import {updateEvent} from '@api/sdk.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import SettingsPopover, {SettingsSection} from '@components/SettingsPopover.tsx'
import {eventDtoToUpdateRequest} from '@components/event/eventUpdateRequest.ts'
import {
    AUTO_REFRESH_MAX_SECONDS,
    AUTO_REFRESH_MIN_SECONDS,
    clampRefreshSeconds,
} from '@components/event/competition/excecution/autoRefresh.ts'

type Props = {
    event: EventDto
    /** Lädt die Event-Daten neu, nachdem die Durchführungs-Einstellungen gespeichert wurden. */
    reloadEvent: () => void
    /** Nur wer die Veranstaltung bearbeiten darf, sieht den Veranstaltungs-Abschnitt. */
    canEdit: boolean
    shortLabels: boolean
    toggleShortLabels: () => void
    openExecutionInNewTab: boolean
    setOpenExecutionInNewTab: (value: boolean) => void
}

/**
 * Das Einstellungs-Popover des Zeitplan-Tabs — zwei Abschnitte:
 *
 * „Dieses Gerät" wirkt sofort und bleibt lokal (localStorage): ob der Sprung „Zur Durchführung"
 * ein neues Fenster öffnet, und ob Läufe am Kürzel oder am ausgeschriebenen Namen hängen (die
 * geteilte Wahl mit dem Schiedsrichter-Board, siehe shortLabels.ts).
 *
 * „Veranstaltung" schreibt an die Veranstaltung selbst und gilt damit für alle: die vier
 * Durchführungs-Einstellungen, die bis zum 11.08.2026 im allgemeinen EventDialog untergingen —
 * am Renntag werden sie am Zeitplan gebraucht, nicht zwischen Meldefristen und Rechnungsdaten.
 */
const ScheduleSettingsPopover = ({
    event,
    reloadEvent,
    canEdit,
    shortLabels,
    toggleShortLabels,
    openExecutionInNewTab,
    setOpenExecutionInNewTab,
}: Props) => {
    const {t} = useTranslation()

    return (
        <SettingsPopover>
            <SettingsSection title={t('event.settings.device')}>
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
            </SettingsSection>
            {canEdit && <EventSettingsSection event={event} reloadEvent={reloadEvent} />}
        </SettingsPopover>
    )
}

/**
 * Der Veranstaltungs-Abschnitt als eigene Komponente: Das Popover baut seinen Inhalt erst beim
 * Öffnen, der Formularzustand startet also bei jedem Öffnen frisch vom geladenen Event.
 * Gespeichert wird gesammelt über den Knopf — die Felder gehören zusammen, und ein Takt-Feld,
 * das je Tastendruck einen Update-Request schickt, wäre keine Hilfe.
 */
const EventSettingsSection = ({event, reloadEvent}: {event: EventDto; reloadEvent: () => void}) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [autoRefresh, setAutoRefresh] = useState(event.executionAutoRefresh)
    const [refreshSeconds, setRefreshSeconds] = useState(
        String(clampRefreshSeconds(event.executionAutoRefreshSeconds)),
    )
    const [chainProgressionMode, setChainProgressionMode] = useState<ChainProgressionMode>(
        event.chainProgressionMode ?? 'DEAKTIVIERT',
    )
    const [autoCreateFollowingRounds, setAutoCreateFollowingRounds] = useState(
        event.autoCreateFollowingRounds ?? false,
    )
    const [submitting, setSubmitting] = useState(false)

    const chainProgressionModes: {id: ChainProgressionMode; label: string}[] = [
        {id: 'SCHIEDSRICHTER', label: t('event.chainProgressionMode.SCHIEDSRICHTER')},
        {id: 'REGATTABUERO', label: t('event.chainProgressionMode.REGATTABUERO')},
        {id: 'DEAKTIVIERT', label: t('event.chainProgressionMode.DEAKTIVIERT')},
    ]

    const handleSave = async () => {
        setSubmitting(true)
        const {error} = await updateEvent({
            path: {eventId: event.id},
            // Der Endpunkt kennt kein Teil-Update: alles Übrige geht unverändert mit.
            body: {
                ...eventDtoToUpdateRequest(event),
                chainProgressionMode,
                autoCreateFollowingRounds,
                executionAutoRefresh: autoRefresh,
                // Begrenzt statt roh übernommen, damit ein leeres oder krummes Feld nicht als 0
                // beim Server ankommt, wo es nur abgelehnt würde.
                executionAutoRefreshSeconds: clampRefreshSeconds(Number(refreshSeconds)),
            },
        })
        setSubmitting(false)
        if (error) {
            feedback.error(t('entity.edit.error', {entity: t('event.event')}))
        } else {
            feedback.success(t('entity.edit.success', {entity: t('event.event')}))
            reloadEvent()
        }
    }

    return (
        <SettingsSection title={t('event.settings.event')}>
            <TextField
                select
                size={'small'}
                label={t('event.chainProgressionMode.label')}
                value={chainProgressionMode}
                onChange={e => setChainProgressionMode(e.target.value as ChainProgressionMode)}>
                {chainProgressionModes.map(mode => (
                    <MenuItem key={mode.id} value={mode.id}>
                        {mode.label}
                    </MenuItem>
                ))}
            </TextField>
            <Typography variant={'caption'} color={'text.secondary'}>
                {t('event.chainProgressionMode.hint')}
            </Typography>
            <FormControlLabel
                control={
                    <Switch
                        checked={autoCreateFollowingRounds}
                        onChange={(_, checked) => setAutoCreateFollowingRounds(checked)}
                    />
                }
                label={t('event.autoCreateFollowingRounds.label')}
            />
            <FormControlLabel
                control={
                    <Switch
                        checked={autoRefresh}
                        onChange={(_, checked) => setAutoRefresh(checked)}
                    />
                }
                label={t('event.executionAutoRefresh.label')}
            />
            <TextField
                size={'small'}
                type={'number'}
                label={t('event.executionAutoRefresh.seconds')}
                value={refreshSeconds}
                onChange={e => setRefreshSeconds(e.target.value)}
                // Das Sekundenfeld hat nichts zu sagen, solange die Automatik aus ist.
                disabled={!autoRefresh}
                slotProps={{
                    htmlInput: {
                        min: AUTO_REFRESH_MIN_SECONDS,
                        max: AUTO_REFRESH_MAX_SECONDS,
                        step: 1,
                    },
                }}
            />
            <Stack direction={'row'} justifyContent={'flex-end'}>
                <Button
                    variant={'contained'}
                    size={'small'}
                    onClick={handleSave}
                    disabled={submitting}>
                    {t('common.save')}
                </Button>
            </Stack>
        </SettingsSection>
    )
}

export default ScheduleSettingsPopover
