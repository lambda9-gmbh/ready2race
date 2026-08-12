import {useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormControlLabel,
    FormHelperText,
    FormLabel,
    MenuItem,
    Radio,
    RadioGroup,
    Stack,
    TextField,
} from '@mui/material'
import {Download} from '@mui/icons-material'
import {downloadEventStartlists, getEventTimingConfig, getRaceClockerRaces} from '@api/sdk.gen.ts'
import {EventStartlistFileType} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getFilename} from '@utils/helpers.ts'
import LoadingButton from '@components/form/LoadingButton.tsx'

type Props = {
    eventId: string
}

/** Platzhalter-Wert des Rennen-Filters: kein Filter, alle Wettkämpfe exportieren. */
const ALL_RACES = 'ALL'

/**
 * Der Startlisten-Sammelexport am Zeitplan-Tab: ein Knopf, ein Dialog, ein Download.
 *
 * Bewusst eine eigene, kleine Komponente statt weiterer Zeilen in EventSchedule.tsx - an dessen
 * Kopfzeile arbeitet parallel eine andere Session, und ein einzelner eingefügter Knopf lässt sich
 * konfliktfrei mergen.
 *
 * Die Format-Vorauswahl folgt dem Zeitnahmesystem der Veranstaltung: Webscorer führt je Wettkampf
 * ein eigenes Rennen (→ ZIP, eine CSV je Wettkampf), RaceClocker trägt alle Wellen in einem Rennen
 * (→ eine große CSV). Umschaltbar bleibt beides. Der Delta-Abgleich ("nur in RaceClocker fehlende
 * Läufe") existiert nur für RaceClocker - nur dort gibt es einen Feed, gegen den sich abgleichen
 * ließe.
 */
const ScheduleStartlistExportButton = ({eventId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [open, setOpen] = useState(false)
    const [fileType, setFileType] = useState<EventStartlistFileType>('ZIP')
    const [skipByes, setSkipByes] = useState(true)
    const [onlyMissing, setOnlyMissing] = useState(false)
    // ALL_RACES = alle Rennen, sonst die Id des gewählten RaceClocker-Rennens (Import Rennen für
    // Rennen). Ein benannter Platzhalter statt '' - bei leerem Wert hielte MUI das Select für
    // leer und ließe das Label über der Anzeige „Alle" stehen.
    const [raceFilter, setRaceFilter] = useState(ALL_RACES)
    const [downloading, setDownloading] = useState(false)

    const downloadRef = useRef<HTMLAnchorElement>(null)

    // Nur für die Vorauswahl - der Dialog funktioniert auch, solange die Antwort noch aussteht.
    const {data: timingConfig} = useFetch(
        signal => getEventTimingConfig({signal, path: {eventId}}),
        {
            onResponse: ({data}) => {
                if (data?.timingSystem === 'RACECLOCKER') {
                    setFileType('CSV')
                }
            },
            deps: [eventId],
        },
    )
    const isRaceClocker = timingConfig?.timingSystem === 'RACECLOCKER'

    // Die konfigurierten Rennen der Veranstaltung - Optionen des Rennen-Filters. Nur bei
    // Zeitnahme über RaceClocker geholt, anderswo gibt es weder Rennen noch das Select dazu.
    const {data: races} = useFetch(signal => getRaceClockerRaces({signal, path: {eventId}}), {
        preCondition: () => isRaceClocker,
        deps: [eventId, isRaceClocker],
    })

    const handleDownload = async () => {
        setDownloading(true)
        const {data, error, response} = await downloadEventStartlists({
            path: {eventId},
            query: {
                fileType,
                skipByes,
                onlyMissingInRaceClocker: isRaceClocker && onlyMissing,
                // Wirkt für Voll- UND Delta-Export; „Alle" lässt den Parameter weg.
                raceclockerRaceId:
                    isRaceClocker && raceFilter !== ALL_RACES ? raceFilter : undefined,
            },
        })
        setDownloading(false)
        const anchor = downloadRef.current

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else if (error.errorCode === 'STARTLIST_CONFIG_NOT_CONFIGURED') {
                feedback.error(t('event.competition.execution.startList.error.notConfigured'))
            } else if (
                error.errorCode === 'RACECLOCKER_UNREACHABLE' ||
                error.errorCode === 'RACECLOCKER_URL_INVALID'
            ) {
                // Delta-Abgleich: ein nicht erreichbares Rennen bricht den ganzen Export ab -
                // kein Teilexport ohne Hinweis (Server-KDoc downloadEventStartlists).
                feedback.error(t('event.schedule.startlistExport.error.unreachable'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(new Blob([data]))
            anchor.download =
                getFilename(response) ?? `startLists.${fileType === 'ZIP' ? 'zip' : 'csv'}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
            setOpen(false)
        }
    }

    return (
        <>
            <Button variant={'outlined'} startIcon={<Download />} onClick={() => setOpen(true)}>
                {t('event.schedule.startlistExport.button')}
            </Button>
            <a ref={downloadRef} style={{display: 'none'}} />
            <Dialog open={open} onClose={() => setOpen(false)} maxWidth={'xs'} fullWidth>
                <DialogTitle>{t('event.schedule.startlistExport.title')}</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{mt: 1}}>
                        <FormControl>
                            <FormLabel id={'startlist-export-format-label'}>
                                {t('event.schedule.startlistExport.format.label')}
                            </FormLabel>
                            <RadioGroup
                                aria-labelledby={'startlist-export-format-label'}
                                value={fileType}
                                onChange={(_, value) =>
                                    setFileType(value as EventStartlistFileType)
                                }>
                                <FormControlLabel
                                    value={'ZIP'}
                                    control={<Radio />}
                                    label={t('event.schedule.startlistExport.format.zip')}
                                />
                                <FormControlLabel
                                    value={'CSV'}
                                    control={<Radio />}
                                    label={t('event.schedule.startlistExport.format.csv')}
                                />
                            </RadioGroup>
                        </FormControl>
                        <FormControl>
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={skipByes}
                                        onChange={(_, checked) => setSkipByes(checked)}
                                    />
                                }
                                label={t('event.schedule.startlistExport.skipByes.label')}
                            />
                            <FormHelperText>
                                {t('event.schedule.startlistExport.skipByes.hint')}
                            </FormHelperText>
                        </FormControl>
                        {isRaceClocker && (
                            <FormControl>
                                {/* Import Rennen für Rennen: nur die Wettkämpfe, deren angewähltes
                                    RaceClocker-Rennen das gewählte ist - wirkt für Voll- UND
                                    Delta-Export. */}
                                <TextField
                                    select
                                    size={'small'}
                                    label={t('event.schedule.startlistExport.race.label')}
                                    value={raceFilter}
                                    onChange={e => setRaceFilter(e.target.value)}>
                                    <MenuItem value={ALL_RACES}>
                                        {t('event.schedule.startlistExport.race.all')}
                                    </MenuItem>
                                    {[...(races ?? [])]
                                        .sort((a, b) => a.position - b.position)
                                        .map(race => (
                                            <MenuItem key={race.id} value={race.id}>
                                                {race.name}
                                            </MenuItem>
                                        ))}
                                </TextField>
                                <FormHelperText>
                                    {t('event.schedule.startlistExport.race.hint')}
                                </FormHelperText>
                            </FormControl>
                        )}
                        {isRaceClocker && (
                            <FormControl>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={onlyMissing}
                                            onChange={(_, checked) => setOnlyMissing(checked)}
                                        />
                                    }
                                    label={t('event.schedule.startlistExport.onlyMissing.label')}
                                />
                                <FormHelperText>
                                    {t('event.schedule.startlistExport.onlyMissing.hint')}
                                </FormHelperText>
                            </FormControl>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
                    <LoadingButton
                        pending={downloading}
                        variant={'contained'}
                        onClick={handleDownload}>
                        {t('event.schedule.startlistExport.download')}
                    </LoadingButton>
                </DialogActions>
            </Dialog>
        </>
    )
}

export default ScheduleStartlistExportButton
