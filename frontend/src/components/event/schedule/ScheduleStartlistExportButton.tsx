import {useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    Alert,
    Button,
    Checkbox,
    CircularProgress,
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
    Typography,
} from '@mui/material'
import {Download} from '@mui/icons-material'
import {format} from 'date-fns'
import {
    downloadEventStartlists,
    getEventTimingConfig,
    getExportBundle,
    getRaceClockerRaces,
    previewEventStartlists,
} from '@api/sdk.gen.ts'
import {EventStartlistPreviewMatchDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getFilename} from '@utils/helpers.ts'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {
    initialSelection,
    isExportable,
    matchIdsParam,
    toggleAll,
    toggleMatch,
} from './startlistPreviewSelection.ts'
import {
    excludedItemsParam,
    initialBundleSelection,
    isPdfName,
    toggleBundleItem,
} from '@components/event/document/exportBundle.ts'

type Props = {
    eventId: string
}

/** Platzhalter-Wert des Rennen-Filters: kein Filter, alle Wettkämpfe exportieren. */
const ALL_RACES = 'ALL'

/** Ein Lauf aus dem Detail-Feld des Startzeit-Fehlers (STARTLIST_MATCHES_WITHOUT_START_TIME). */
type NoStartTimeMatch = {
    competitionIdentifier?: string
    competitionShortName?: string
    competitionName?: string
    roundName?: string
    matchName?: string
}

/**
 * Die betroffenen Läufe aus dem strukturierten Detail-Feld des Fehlers - dasselbe Muster wie in
 * executionError.ts: `details` ist im generierten ApiError-Typ nicht deklariert, kommt aber im
 * JSON mit.
 */
const noStartTimeMatches = (error: unknown): NoStartTimeMatch[] => {
    const details = (error as {details?: {matches?: unknown}}).details
    const raw = details?.matches
    return Array.isArray(raw) ? raw.map(entry => entry as NoStartTimeMatch) : []
}

/** Einheitliche Laufbeschriftung: „11 CF1x Viertelfinale VF2" - Kürzel vor ausgeschriebenem Namen. */
const matchLabel = (match: NoStartTimeMatch): string =>
    [
        match.competitionIdentifier,
        match.competitionShortName ?? match.competitionName,
        match.roundName,
        match.matchName,
    ]
        .filter(Boolean)
        .join(' ')

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
 *
 * Mit gesetztem Delta-Häkchen zeigt der Dialog VOR dem Download die Vorschau (gleiche Plan-Logik
 * wie der Export, Server-seitig dieselbe Funktion): je Lauf eine abwählbare Zeile, Läufe ohne
 * geplante Startzeit gesondert und nicht anwählbar - sie würden den Export blockieren. Der
 * Voll-Export bleibt bewusst ohne Vorschau: sein Inhalt ist vorhersagbar (je Wettkampf die erste
 * Runde).
 *
 * Oberste Verzweigung ist seit der Regatta-Mappe (12.08.2026) PDF gegen CSV/ZIP: CSV/ZIP ist der
 * Import ins Zeitnahme-System (Aufteilung wie gehabt wählbar), PDF der Aushang bzw. die Mappe -
 * dort kommen „Amtspapier mitdrucken" und das Anhängen der Mappen-Dokumente dazu (je Eintrag
 * abwählbar, auch der Startlisten-Platzhalter). Freilose, Rennen-Filter und Delta gelten in
 * beiden Zweigen - sie bestimmen den generierten Startlisten-Teil.
 */
const ScheduleStartlistExportButton = ({eventId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [open, setOpen] = useState(false)
    // Die oberste Verzweigung (PDF | CSV/ZIP) und die Aufteilung des Datenzweigs getrennt: so
    // überlebt die ZIP/CSV-Wahl einen Ausflug in den PDF-Zweig.
    const [pdfMode, setPdfMode] = useState(false)
    const [dataFileType, setDataFileType] = useState<'ZIP' | 'CSV'>('ZIP')
    const [skipByes, setSkipByes] = useState(true)
    const [onlyMissing, setOnlyMissing] = useState(false)
    // PDF-Zweig: Amtspapier (Vorlagen-Design) mitdrucken und Mappen-Dokumente anhängen.
    const [withBackground, setWithBackground] = useState(true)
    const [attachBundle, setAttachBundle] = useState(false)
    const [bundleSelected, setBundleSelected] = useState<Set<string>>(new Set())
    // ALL_RACES = alle Rennen, sonst die Id des gewählten RaceClocker-Rennens (Import Rennen für
    // Rennen). Ein benannter Platzhalter statt '' - bei leerem Wert hielte MUI das Select für
    // leer und ließe das Label über der Anzeige „Alle" stehen.
    const [raceFilter, setRaceFilter] = useState(ALL_RACES)
    const [downloading, setDownloading] = useState(false)
    const [selected, setSelected] = useState<Set<string>>(new Set())
    // Der Startzeit-Fehler des Downloads, als Liste im Dialog statt als Snackbar - die
    // betroffenen Läufe sollen lesbar dastehen, nicht in einer Zeile verschwinden.
    const [downloadBlocked, setDownloadBlocked] = useState<NoStartTimeMatch[] | null>(null)

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const fileType = pdfMode ? 'PDF' : dataFileType

    // Nur für die Vorauswahl - der Dialog funktioniert auch, solange die Antwort noch aussteht.
    const {data: timingConfig} = useFetch(
        signal => getEventTimingConfig({signal, path: {eventId}}),
        {
            onResponse: ({data}) => {
                if (data?.timingSystem === 'RACECLOCKER') {
                    setDataFileType('CSV')
                }
            },
            deps: [eventId],
        },
    )
    const isRaceClocker = timingConfig?.timingSystem === 'RACECLOCKER'
    const previewActive = isRaceClocker && onlyMissing

    // Die Mappe des PDF-Zweigs - schon beim Öffnen des Zweigs geholt (nicht erst beim Anhaken),
    // damit die Checkbox-Liste sofort dasteht. Jede Antwort wählt alles vor.
    const {data: bundleItems} = useFetch(
        signal => getExportBundle({signal, path: {eventId}}),
        {
            preCondition: () => open && pdfMode,
            onResponse: ({data}) => {
                if (data) {
                    setBundleSelected(initialBundleSelection(data))
                }
            },
            deps: [eventId, open, pdfMode],
        },
    )

    // Die konfigurierten Rennen der Veranstaltung - Optionen des Rennen-Filters. Nur bei
    // Zeitnahme über RaceClocker geholt, anderswo gibt es weder Rennen noch das Select dazu.
    const {data: races} = useFetch(signal => getRaceClockerRaces({signal, path: {eventId}}), {
        preCondition: () => isRaceClocker,
        deps: [eventId, isRaceClocker],
    })

    // Die Export-Vorschau - nur im Delta-Modus geholt und bei jeder Änderung von Häkchen oder
    // Rennen-Filter neu (deps): dieselben Parameter wie der Download, dieselbe Plan-Logik im
    // Server. Jede neue Antwort setzt die Auswahl auf „alles Exportierbare".
    const {
        data: preview,
        pending: previewPending,
        error: previewError,
    } = useFetch(
        signal =>
            previewEventStartlists({
                signal,
                path: {eventId},
                query: {
                    skipByes,
                    onlyMissingInRaceClocker: true,
                    raceclockerRaceId: raceFilter !== ALL_RACES ? raceFilter : undefined,
                },
            }),
        {
            preCondition: () => open && previewActive,
            onResponse: ({data}) => {
                setDownloadBlocked(null)
                if (data) {
                    setSelected(initialSelection(data))
                }
            },
            deps: [eventId, open, previewActive, skipByes, raceFilter],
        },
    )

    const exportableRows = (preview ?? []).filter(isExportable)
    const blockedRows = (preview ?? []).filter(row => !isExportable(row))

    const handleDownload = async () => {
        setDownloading(true)
        setDownloadBlocked(null)
        const {data, error, response} = await downloadEventStartlists({
            path: {eventId},
            query: {
                fileType,
                skipByes,
                onlyMissingInRaceClocker: previewActive,
                // Wirkt für Voll- UND Delta-Export; „Alle" lässt den Parameter weg.
                raceclockerRaceId:
                    isRaceClocker && raceFilter !== ALL_RACES ? raceFilter : undefined,
                // Die Abwahl aus der Vorschau. Serverseitig nur Schnittmenge mit dem Plan -
                // mitschicken kann die Auswahl also nur verkleinern, nie erweitern.
                matchIds:
                    previewActive && preview ? matchIdsParam(selected, preview) : undefined,
                // PDF-Zweig: Amtspapier und Mappe. Serverseitig zählt die ABWAHL der
                // Mappen-Einträge - neu hinzugekommene Dokumente fallen so nie still heraus.
                withBackground: pdfMode ? withBackground : undefined,
                includeBundleDocuments: pdfMode && attachBundle ? true : undefined,
                excludedBundleItems:
                    pdfMode && attachBundle && bundleItems
                        ? excludedItemsParam(bundleItems, bundleSelected)
                        : undefined,
            },
        })
        setDownloading(false)
        const anchor = downloadRef.current

        if (error) {
            if (error.errorCode === 'STARTLIST_MATCHES_WITHOUT_START_TIME') {
                // Im Dialog statt als Snackbar: Die Liste der Läufe ohne Startzeit soll lesbar
                // dastehen - am Renntag muss erkennbar sein, WELCHER Lauf klemmt.
                setDownloadBlocked(noStartTimeMatches(error))
            } else if (error.status.value === 409) {
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
            anchor.download = getFilename(response) ?? `startLists.${fileType.toLowerCase()}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
            setOpen(false)
        }
    }

    const previewRowLabel = (row: EventStartlistPreviewMatchDto): string =>
        `${row.startTime ? format(new Date(row.startTime), t('format.time')) : '–'} | ${matchLabel(row)}`

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
                        {/* Oberste Verzweigung: PDF (Aushang/Mappe) gegen CSV/ZIP (Import ins
                            Zeitnahme-System). PDF immer nur von Hand - die Vorauswahl folgt
                            weiterhin dem Zeitnahmesystem (Webscorer → ZIP, RaceClocker → CSV). */}
                        <FormControl>
                            <FormLabel id={'startlist-export-kind-label'}>
                                {t('event.schedule.startlistExport.kind.label')}
                            </FormLabel>
                            <RadioGroup
                                aria-labelledby={'startlist-export-kind-label'}
                                value={pdfMode ? 'PDF' : 'DATA'}
                                onChange={(_, value) => setPdfMode(value === 'PDF')}>
                                <FormControlLabel
                                    value={'DATA'}
                                    control={<Radio />}
                                    label={t('event.schedule.startlistExport.kind.data')}
                                />
                                <FormControlLabel
                                    value={'PDF'}
                                    control={<Radio />}
                                    label={t('event.schedule.startlistExport.kind.pdf')}
                                />
                            </RadioGroup>
                        </FormControl>
                        {!pdfMode && (
                            <FormControl>
                                <FormLabel id={'startlist-export-format-label'}>
                                    {t('event.schedule.startlistExport.format.label')}
                                </FormLabel>
                                <RadioGroup
                                    aria-labelledby={'startlist-export-format-label'}
                                    value={dataFileType}
                                    onChange={(_, value) =>
                                        setDataFileType(value as 'ZIP' | 'CSV')
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
                        )}
                        {pdfMode && (
                            <FormControl>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={withBackground}
                                            onChange={(_, checked) => setWithBackground(checked)}
                                        />
                                    }
                                    label={t('event.schedule.startlistExport.background.label')}
                                />
                                <FormHelperText>
                                    {t('event.schedule.startlistExport.background.hint')}
                                </FormHelperText>
                            </FormControl>
                        )}
                        {pdfMode && (
                            <FormControl>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={attachBundle}
                                            onChange={(_, checked) => setAttachBundle(checked)}
                                        />
                                    }
                                    label={t('event.schedule.startlistExport.bundle.label')}
                                />
                                <FormHelperText>
                                    {t('event.schedule.startlistExport.bundle.hint')}
                                </FormHelperText>
                                {attachBundle && (
                                    <Stack sx={{pl: 1}}>
                                        {(bundleItems ?? []).map(item => {
                                            const isPlaceholder =
                                                item.kind === 'GENERATED_STARTLISTS'
                                            // Ein Nicht-PDF in der Mappe (die Auswahl bietet nur
                                            // .pdf an, Altbestand kann abweichen) überspringt der
                                            // Server tolerant - der Hinweis macht das VOR dem
                                            // Download sichtbar.
                                            const skipped =
                                                !isPlaceholder &&
                                                !isPdfName(item.documentName ?? '')
                                            return (
                                                <FormControlLabel
                                                    key={item.id}
                                                    control={
                                                        <Checkbox
                                                            size={'small'}
                                                            checked={bundleSelected.has(item.id)}
                                                            onChange={() =>
                                                                setBundleSelected(
                                                                    toggleBundleItem(
                                                                        bundleSelected,
                                                                        item.id,
                                                                    ),
                                                                )
                                                            }
                                                        />
                                                    }
                                                    label={
                                                        <Typography
                                                            variant={'body2'}
                                                            fontStyle={
                                                                isPlaceholder
                                                                    ? 'italic'
                                                                    : undefined
                                                            }>
                                                            {isPlaceholder
                                                                ? t(
                                                                      'event.document.exportBundle.startlistsPlaceholder',
                                                                  )
                                                                : (item.documentName ?? '')}
                                                            {skipped &&
                                                                ` – ${t('event.schedule.startlistExport.bundle.skippedNonPdf')}`}
                                                        </Typography>
                                                    }
                                                />
                                            )
                                        })}
                                    </Stack>
                                )}
                            </FormControl>
                        )}
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
                        {previewActive && previewPending && (
                            <Stack direction={'row'} spacing={1} alignItems={'center'}>
                                <CircularProgress size={18} />
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    {t('event.schedule.startlistExport.preview.loading')}
                                </Typography>
                            </Stack>
                        )}
                        {previewActive && !previewPending && previewError && (
                            // Der Fehler steht IM Dialog, nicht erst als Snackbar beim Download -
                            // ein nicht erreichbares Rennen soll auffallen, bevor jemand klickt.
                            <Alert severity={'warning'}>
                                {(previewError.error as {errorCode?: string}).errorCode ===
                                    'RACECLOCKER_UNREACHABLE' ||
                                (previewError.error as {errorCode?: string}).errorCode ===
                                    'RACECLOCKER_URL_INVALID'
                                    ? t('event.schedule.startlistExport.error.unreachable')
                                    : t('common.error.unexpected')}
                            </Alert>
                        )}
                        {previewActive && !previewPending && preview && (
                            <Stack spacing={0}>
                                {exportableRows.length === 0 && blockedRows.length === 0 && (
                                    <Typography variant={'body2'} color={'text.secondary'}>
                                        {t('event.schedule.startlistExport.preview.empty')}
                                    </Typography>
                                )}
                                {exportableRows.length > 0 && (
                                    <>
                                        <FormControlLabel
                                            control={
                                                <Checkbox
                                                    size={'small'}
                                                    checked={
                                                        selected.size === exportableRows.length
                                                    }
                                                    indeterminate={
                                                        selected.size > 0 &&
                                                        selected.size < exportableRows.length
                                                    }
                                                    onChange={() =>
                                                        setSelected(toggleAll(selected, preview))
                                                    }
                                                    inputProps={{
                                                        'aria-label': t(
                                                            'event.schedule.startlistExport.preview.toggleAll',
                                                        ),
                                                    }}
                                                />
                                            }
                                            label={
                                                <Typography variant={'body2'}>
                                                    {t(
                                                        'event.schedule.startlistExport.preview.selectedCount',
                                                        {
                                                            selected: selected.size,
                                                            total: exportableRows.length,
                                                        },
                                                    )}
                                                </Typography>
                                            }
                                        />
                                        <Stack sx={{maxHeight: 240, overflowY: 'auto', pl: 1}}>
                                            {exportableRows.map(row => (
                                                <FormControlLabel
                                                    key={row.matchId}
                                                    control={
                                                        <Checkbox
                                                            size={'small'}
                                                            checked={selected.has(row.matchId)}
                                                            onChange={() =>
                                                                setSelected(
                                                                    toggleMatch(
                                                                        selected,
                                                                        row.matchId,
                                                                    ),
                                                                )
                                                            }
                                                        />
                                                    }
                                                    label={
                                                        <Typography variant={'body2'}>
                                                            {previewRowLabel(row)}
                                                        </Typography>
                                                    }
                                                />
                                            ))}
                                        </Stack>
                                    </>
                                )}
                                {blockedRows.length > 0 && (
                                    // Nicht anwählbar: Diese Läufe würden den Export blockieren
                                    // (Startzeit-Wächter im Server). Sie bleiben sichtbar, damit
                                    // ihr Fehlen im Export eine bewusste Entscheidung ist.
                                    <Stack spacing={0.5} sx={{mt: 1}}>
                                        <Typography variant={'caption'} color={'error'}>
                                            {t(
                                                'event.schedule.startlistExport.preview.noStartTime',
                                            )}
                                        </Typography>
                                        {blockedRows.map(row => (
                                            <Typography
                                                key={row.matchId}
                                                variant={'body2'}
                                                color={'text.secondary'}
                                                sx={{pl: 1}}>
                                                {matchLabel(row)}
                                            </Typography>
                                        ))}
                                    </Stack>
                                )}
                            </Stack>
                        )}
                        {downloadBlocked && (
                            <Alert severity={'error'}>
                                <Typography variant={'body2'}>
                                    {t('event.schedule.startlistExport.error.noStartTime', {
                                        count: downloadBlocked.length,
                                    })}
                                </Typography>
                                {downloadBlocked.map((match, index) => (
                                    <Typography key={index} variant={'body2'} sx={{pl: 1}}>
                                        {matchLabel(match)}
                                    </Typography>
                                ))}
                            </Alert>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
                    <LoadingButton
                        pending={downloading}
                        variant={'contained'}
                        // Solange die Vorschau lädt, wüsste der Export nicht, was abgewählt ist;
                        // mit leerer Auswahl gäbe es nichts zu exportieren - dasselbe gilt für
                        // eine Mappe, in der ALLES abgewählt ist.
                        disabled={
                            (previewActive &&
                                (previewPending ||
                                    (preview !== null && selected.size === 0))) ||
                            (pdfMode && attachBundle && bundleSelected.size === 0)
                        }
                        onClick={handleDownload}>
                        {t('event.schedule.startlistExport.download')}
                    </LoadingButton>
                </DialogActions>
            </Dialog>
        </>
    )
}

export default ScheduleStartlistExportButton
