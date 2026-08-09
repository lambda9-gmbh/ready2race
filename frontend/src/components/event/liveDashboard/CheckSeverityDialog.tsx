import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import {Close} from '@mui/icons-material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCheckSeverityConfig, updateCheckSeverityConfig} from '@api/sdk.gen.ts'
import {CheckSeverity, CheckSeverityEntryDto, CheckSeverityRowDto} from '@api/types.gen.ts'
import Throbber from '@components/Throbber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {
    applicableCells,
    buildSavePayload,
    isRowApplicable,
    preservedEntries,
    rowSummary,
    severityAt,
} from './checkSeverity.ts'

const SEVERITIES: CheckSeverity[] = ['OK', 'WARNING', 'CRITICAL']

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
}

/**
 * Gegliedert nach Prüfung, nicht nach Wettkampf: der Schiedsrichter-Obmann entscheidet über
 * "offene Rechnungen", nicht über "Wettkampf 7". Gespeichert wird trotzdem je Wettkampf - die
 * Sammelaktion je Zeile ist der Regattatag-Fall in einem Klick.
 */
const CheckSeverityDialog = ({open, onClose, eventId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [entries, setEntries] = useState<CheckSeverityEntryDto[]>([])
    // Gespeicherte Einträge, deren Kombination aus Wettkampf, Prüfungsart und Bedingung die
    // Matrix unten gerade nicht abdeckt - gleich aus welchem Grund die Kombination fehlt. Sie
    // werden nicht angezeigt und nicht bearbeitet, müssen aber beim Speichern unverändert
    // erhalten bleiben - sonst ersetzt replaceForEvent sie durch den Standard und der Wert ist
    // unwiederbringlich weg.
    const [preserved, setPreserved] = useState<CheckSeverityEntryDto[]>([])
    // Erst wenn die Matrix mit echten Werten gefüllt ist, darf sie gezeichnet werden - sonst zeigt
    // jede Zeile für einen Renderdurchlauf lang "gemischt" und jedes Feld den Ersatzwert.
    const [entriesReady, setEntriesReady] = useState(false)
    const [saving, setSaving] = useState(false)

    const {data: config, error: configError} = useFetch(
        signal => getCheckSeverityConfig({signal, path: {eventId}}),
        {
            deps: [eventId, open],
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('event.liveDashboard.checkSeverity.title'),
                        }),
                    )
                }
            },
        },
    )

    // Die Matrix wird beim Öffnen vollständig aufgefüllt - auch mit den Standardwerten. Damit ist
    // jedes Feld ein bearbeitbarer Wert; welche davon gespeichert werden, entscheidet der Server.
    // Wettkämpfe, für die eine Zeile nicht anwendbar ist (z.B. "Nicht in der Arena" ohne
    // checkInOutRequired), bekommen dort bewusst gar keinen Eintrag - sie sind nicht einstellbar.
    useEffect(() => {
        if (!config) return
        setEntries(
            applicableCells(config).map(({competition, row}) => ({
                competitionId: competition.competitionId,
                checkType: row.checkType,
                requirementId: row.requirementId,
                severity: severityAt(
                    config,
                    config.entries,
                    competition.competitionId,
                    row.checkType,
                    row.requirementId ?? null,
                ),
            })),
        )
        setPreserved(preservedEntries(config))
        setEntriesReady(true)
    }, [config])

    const rowKey = (row: CheckSeverityRowDto) => `${row.checkType}:${row.requirementId ?? ''}`

    const matches = (entry: CheckSeverityEntryDto, row: CheckSeverityRowDto) =>
        entry.checkType === row.checkType &&
        (entry.requirementId ?? null) === (row.requirementId ?? null)

    const setSeverity = (row: CheckSeverityRowDto, competitionId: string | null, value: CheckSeverity) =>
        setEntries(current =>
            current.map(entry =>
                matches(entry, row) && (competitionId === null || entry.competitionId === competitionId)
                    ? {...entry, severity: value}
                    : entry,
            ),
        )

    const rowLabel = (row: CheckSeverityRowDto) =>
        row.checkType === 'REQUIREMENT'
            ? (row.name ?? '')
            : row.checkType === 'REQUIREMENT_TIME_WINDOW'
              ? t('event.liveDashboard.checkSeverity.check.REQUIREMENT_TIME_WINDOW')
              : t(`event.liveDashboard.checkSeverity.check.${row.checkType}`)

    const summaryLabel = (row: CheckSeverityRowDto) => {
        // Nur was tatsächlich einstellbar ist, geht in die Verdichtung ein - bewahrte Einträge
        // nicht (mehr) anwendbarer Kombinationen zählen hier nicht mit.
        const severities = entries.filter(e => matches(e, row)).map(e => e.severity)
        const summary = rowSummary(severities)
        switch (summary.kind) {
            // Keinem Wettkampf anwendbar (z.B. "Nicht in der Arena", wenn das kein Wettkampf
            // verlangt) - dann gibt es nichts zu verdichten und keine Sammelaktion zu beschriften.
            case 'empty':
                return ''
            case 'uniform':
                return t('event.liveDashboard.checkSeverity.uniform', {
                    severity: t(`event.liveDashboard.checkSeverity.severity.${summary.severity}`),
                })
            case 'mixed':
                return t('event.liveDashboard.checkSeverity.mixed')
        }
    }

    const handleSave = async () => {
        setSaving(true)
        const {error} = await updateCheckSeverityConfig({
            path: {eventId},
            body: {entries: buildSavePayload(entries, preserved)},
        })
        setSaving(false)
        if (error) {
            feedback.error(t('event.liveDashboard.checkSeverity.saveError'))
        } else {
            feedback.success(t('event.liveDashboard.checkSeverity.saved'))
            onClose()
        }
    }

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
            <DialogTitle sx={{pr: 6}}>
                {t('event.liveDashboard.checkSeverity.title')}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Typography variant="body2" color="text.secondary" sx={{mb: 2}}>
                    {t('event.liveDashboard.checkSeverity.description')}
                </Typography>
                {configError ? (
                    <Typography color="error">
                        {t('common.load.error.single', {
                            entity: t('event.liveDashboard.checkSeverity.title'),
                        })}
                    </Typography>
                ) : !entriesReady || !config ? (
                    <Throbber />
                ) : config.competitions.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                        {t('event.liveDashboard.checkSeverity.noCompetitions')}
                    </Typography>
                ) : (
                    config.rows.map(row => (
                        <Accordion
                            key={rowKey(row)}
                            // Zeitfenster gehört sichtbar unter seine Bedingung
                            sx={{ml: row.checkType === 'REQUIREMENT_TIME_WINDOW' ? 3 : 0}}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        width: 1,
                                        pr: 1,
                                    }}>
                                    <Typography>{rowLabel(row)}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {summaryLabel(row)}
                                    </Typography>
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails>
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    flexWrap="wrap"
                                    sx={{mb: 1}}>
                                    <Typography variant="body2">
                                        {t('event.liveDashboard.checkSeverity.setAll')}
                                    </Typography>
                                    {SEVERITIES.map(severity => (
                                        <Button
                                            key={severity}
                                            size="small"
                                            onClick={() => setSeverity(row, null, severity)}>
                                            {t(
                                                `event.liveDashboard.checkSeverity.severity.${severity}`,
                                            )}
                                        </Button>
                                    ))}
                                </Stack>
                                {config?.competitions.map(competition => {
                                    // Ohne An-/Abmeldung gibt es beim Beachsprint nichts zu bewerten
                                    const notApplicable = !isRowApplicable(row, competition)
                                    const entry = entries.find(
                                        e =>
                                            matches(e, row) &&
                                            e.competitionId === competition.competitionId,
                                    )
                                    return (
                                        <Stack
                                            key={competition.competitionId}
                                            direction="row"
                                            spacing={1}
                                            alignItems="center"
                                            sx={{py: 0.5, opacity: notApplicable ? 0.5 : 1}}>
                                            <Typography sx={{flex: 1}}>
                                                {competition.identifier} | {competition.name}
                                            </Typography>
                                            {notApplicable ? (
                                                <Typography variant="body2" color="text.secondary">
                                                    {t(
                                                        'event.liveDashboard.checkSeverity.noCheckInOut',
                                                    )}
                                                </Typography>
                                            ) : (
                                                <Select
                                                    size="small"
                                                    value={entry?.severity ?? 'CRITICAL'}
                                                    onChange={event =>
                                                        setSeverity(
                                                            row,
                                                            competition.competitionId,
                                                            event.target.value as CheckSeverity,
                                                        )
                                                    }>
                                                    {SEVERITIES.map(severity => (
                                                        <MenuItem key={severity} value={severity}>
                                                            {t(
                                                                `event.liveDashboard.checkSeverity.severity.${severity}`,
                                                            )}
                                                        </MenuItem>
                                                    ))}
                                                </Select>
                                            )}
                                        </Stack>
                                    )
                                })}
                            </AccordionDetails>
                        </Accordion>
                    ))
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>{t('common.cancel')}</Button>
                <LoadingButton
                    pending={saving}
                    disabled={!entriesReady || !!configError}
                    variant="contained"
                    onClick={handleSave}>
                    {t('common.save')}
                </LoadingButton>
            </DialogActions>
        </Dialog>
    )
}

export default CheckSeverityDialog
