import {useEffect, useState} from 'react'
import {
    Box,
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
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import {eventDtoToUpdateRequest} from '@components/event/eventUpdateRequest.ts'
import {
    AUTO_REFRESH_MAX_SECONDS,
    AUTO_REFRESH_MIN_SECONDS,
    clampRefreshSeconds,
} from '@components/event/competition/excecution/autoRefresh.ts'

type Props = {
    /** Die geladene Veranstaltung — hier werden die Durchführungs-Einstellungen festgeschrieben. */
    event: EventDto
    /** Lädt die Event-Daten der Seite neu, nachdem gespeichert wurde. */
    reloadEvent: () => void
}

/**
 * Die Durchführungs-Einstellungen der Veranstaltung im Einstellungen-Tab: wie Läufe beendet und
 * Folgeläufe aktiviert werden (chainProgressionMode), ob Folgerunden automatisch entstehen und in
 * welchem Takt sich die Durchführungsseite selbst aktualisiert.
 *
 * Der Weg hierher war zweistufig: Bis zum 11.08.2026 gingen die Felder im allgemeinen EventDialog
 * zwischen Meldefristen und Rechnungsdaten unter, danach standen sie einen Tag lang im
 * Einstellungs-Popover des Zeitplan-Tabs — dort wirkten sie wie Geräte-Schalter, dabei ändern sie
 * die Veranstaltung für alle (Rückmeldung vom 12.08.2026). Jetzt wohnen sie bei den übrigen
 * Veranstaltungs-Einstellungen, neben der Zeitnahme.
 *
 * Gespeichert wird gesammelt über den Knopf: Die Felder gehören zusammen, und ein Takt-Feld, das
 * je Tastendruck einen Update-Request schickt, wäre keine Hilfe. Der Endpunkt kennt kein
 * Teil-Update — alles Übrige geht über eventDtoToUpdateRequest unverändert mit.
 */
const EventExecutionSettings = ({event, reloadEvent}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

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

    // Der Abschnitt bleibt gemountet, während die Seite die Event-Daten neu lädt (nach dem eigenen
    // Speichern oder einer Aktion anderswo auf der Seite) — dann soll hier der Server-Stand
    // stehen, nicht der von vorhin. Die Seite lädt nur auf Aktionen neu, nicht im Takt; der
    // Abgleich überschreibt also keine laufende Eingabe.
    useEffect(() => {
        setAutoRefresh(event.executionAutoRefresh)
        setRefreshSeconds(String(clampRefreshSeconds(event.executionAutoRefreshSeconds)))
        setChainProgressionMode(event.chainProgressionMode ?? 'DEAKTIVIERT')
        setAutoCreateFollowingRounds(event.autoCreateFollowingRounds ?? false)
    }, [event])

    if (!user.checkPrivilege(updateEventGlobal)) {
        return null
    }

    const chainProgressionModes: {id: ChainProgressionMode; label: string}[] = [
        {id: 'SCHIEDSRICHTER', label: t('event.chainProgressionMode.SCHIEDSRICHTER')},
        {id: 'REGATTABUERO', label: t('event.chainProgressionMode.REGATTABUERO')},
        {id: 'DEAKTIVIERT', label: t('event.chainProgressionMode.DEAKTIVIERT')},
    ]

    const handleSave = async () => {
        setSubmitting(true)
        const {error} = await updateEvent({
            path: {eventId: event.id},
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
        // Kein Card-Rahmen: die Nachbarn im Einstellungen-Tab (Zeitnahme, Dokumente,
        // Teilnahmebedingungen) sind blanke Abschnitte mit h2-Überschrift und Hinweistext —
        // siehe die gleiche Begründung in EventTimingConfig.
        <Box id={'execution-settings'}>
            <Typography variant={'h2'}>{t('event.settings.execution.title')}</Typography>
            <Box sx={{color: 'text.secondary'}}>{t('event.settings.execution.hint')}</Box>
            <Stack spacing={3} sx={{maxWidth: 720, pt: 2}}>
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
                <Typography variant={'caption'} color={'text.secondary'} sx={{mt: -2}}>
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
                <Typography variant={'caption'} color={'text.secondary'} sx={{mt: -2}}>
                    {t('event.autoCreateFollowingRounds.hint')}
                </Typography>
                <FormControlLabel
                    control={
                        <Switch
                            checked={autoRefresh}
                            onChange={(_, checked) => setAutoRefresh(checked)}
                        />
                    }
                    label={t('event.executionAutoRefresh.label')}
                />
                <Typography variant={'caption'} color={'text.secondary'} sx={{mt: -2}}>
                    {t('event.executionAutoRefresh.hint')}
                </Typography>
                <TextField
                    size={'small'}
                    type={'number'}
                    label={t('event.executionAutoRefresh.seconds')}
                    value={refreshSeconds}
                    onChange={e => setRefreshSeconds(e.target.value)}
                    // Das Sekundenfeld hat nichts zu sagen, solange die Automatik aus ist.
                    disabled={!autoRefresh}
                    sx={{maxWidth: 240}}
                    slotProps={{
                        htmlInput: {
                            min: AUTO_REFRESH_MIN_SECONDS,
                            max: AUTO_REFRESH_MAX_SECONDS,
                            step: 1,
                        },
                    }}
                />
                <Stack direction={'row'} justifyContent={'flex-start'}>
                    <Button variant={'contained'} onClick={handleSave} disabled={submitting}>
                        {t('common.save')}
                    </Button>
                </Stack>
            </Stack>
        </Box>
    )
}

export default EventExecutionSettings
