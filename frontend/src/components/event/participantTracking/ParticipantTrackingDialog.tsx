import {useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    List,
    ListItem,
    ListItemText,
    Stack,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {Close, Edit} from '@mui/icons-material'
import {DateTimePicker} from '@mui/x-date-pickers'
import {useTranslation} from 'react-i18next'
import {format, formatISO} from 'date-fns'
import {
    addManualParticipantTracking,
    correctParticipantTracking,
    getParticipantTrackingHistory,
} from '@api/sdk.gen.ts'
import {
    ParticipantScanType,
    ParticipantTrackingChangeDto,
    ParticipantTrackingEntryDto,
} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {participantTrackingErrorKey} from '@components/event/liveDashboard/liveDashboardError.ts'

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
    participantId: string
    /** Wird im Titel gezeigt; der Dialog wird immer aus einer Zeile geöffnet, die den Namen kennt. */
    participantName: string
    /** Ruft die aufrufende Liste neu ab, sobald hier etwas geschrieben wurde. */
    onChanged?: () => void
}

/**
 * Der Ausnahmeweg neben dem Scanner - für den Fall, dass ein Boot ohne Scan abgelegt hat und die
 * Crew trotzdem auf dem Wasser ist.
 *
 * Der Dialog ist bewusst zurückhaltend gestaltet: Hinweis oben, kein Primärknopf zum Eintragen,
 * jede Herkunft angeschrieben. Er soll benutzbar sein, aber nie wie der Scanner wirken - sonst
 * wird aus dem dokumentierten Notbehelf der bequemere Normalweg.
 */
const ParticipantTrackingDialog = ({
    open,
    onClose,
    eventId,
    participantId,
    participantName,
    onChanged,
}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()
    // Am Telefon Vollbild: der Warnkasten und das Formular teilten sich sonst eine halbe Höhe,
    // und das Datumsfeld rutschte unter die Faltkante (beobachtet am 10.08.2026).
    const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))

    const [formOpen, setFormOpen] = useState(false)
    const [editing, setEditing] = useState<ParticipantTrackingEntryDto | null>(null)
    const [scanType, setScanType] = useState<ParticipantScanType>('ENTRY')
    const [scannedAt, setScannedAt] = useState<Date | null>(null)
    const [reason, setReason] = useState('')
    const [submitting, setSubmitting] = useState(false)

    const {data, pending, error, reload} = useFetch(
        signal => getParticipantTrackingHistory({signal, path: {eventId, participantId}}),
        {
            preCondition: () => open,
            deps: [eventId, participantId, open],
        },
    )

    const closeForm = () => {
        setFormOpen(false)
        setEditing(null)
        setReason('')
        setScannedAt(null)
    }

    const openAdd = () => {
        setEditing(null)
        // Die Gegenrichtung zum letzten Eintrag ist fast immer die gesuchte.
        const last = data?.entries[data.entries.length - 1]
        setScanType(last?.scanType === 'ENTRY' ? 'EXIT' : 'ENTRY')
        // Mit „jetzt" vorbelegt: Der häufigste Fall ist „die Person steht gerade vor mir"
        // (Wunsch vom 10.08.2026). Korrigierbar bleibt es; das leere Feld erzwang vorher
        // unnötiges Datumstippen - und der Platzhalter kam in US-Form (MM/DD/YYYY).
        setScannedAt(new Date())
        setReason('')
        setFormOpen(true)
    }

    const openEdit = (entry: ParticipantTrackingEntryDto) => {
        setEditing(entry)
        setScanType(entry.scanType)
        setScannedAt(new Date(entry.scannedAt))
        setReason('')
        setFormOpen(true)
    }

    const submit = async () => {
        if (scannedAt === null || reason.trim() === '') return

        setSubmitting(true)
        const body = {
            scanType,
            // Dasselbe Format wie FormInputDateTime: der Server erwartet eine lokale Zeit ohne Zone.
            scannedAt: formatISO(scannedAt).slice(0, 19),
            reason: reason.trim(),
        }

        const {error: writeError} =
            editing !== null
                ? await correctParticipantTracking({
                      path: {eventId, participantId, trackingId: editing.id},
                      body,
                  })
                : await addManualParticipantTracking({
                      path: {eventId, participantId},
                      body,
                  })

        setSubmitting(false)

        if (writeError) {
            const key = participantTrackingErrorKey(writeError)
            feedback.error(
                key !== undefined ? t(key) : t('club.participant.tracking.manual.saveError'),
            )
            return
        }

        feedback.success(t('club.participant.tracking.manual.saved'))
        closeForm()
        reload()
        onChanged?.()
    }

    /** Woher der Eintrag stammt - die Unterscheidung, um die es hier geht. */
    const sourceChip = (entry: ParticipantTrackingEntryDto) => {
        if (entry.source === 'MANUAL') {
            return (
                <Chip
                    size="small"
                    color="warning"
                    label={t('club.participant.tracking.manual.sourceManual')}
                />
            )
        }
        return entry.editCount > 0 ? (
            <Chip
                size="small"
                color="warning"
                variant="outlined"
                label={t('club.participant.tracking.manual.sourceQrCorrected')}
            />
        ) : (
            <Chip size="small" label={t('club.participant.tracking.manual.sourceQr')} />
        )
    }

    const changeLine = (change: ParticipantTrackingChangeDto): string => {
        const scan = (type: ParticipantScanType, at: string) =>
            `${t(`club.participant.tracking.${type === 'ENTRY' ? 'in' : 'out'}`)} ${format(
                new Date(at),
                t('format.datetime'),
            )}`

        return change.changeType === 'UPDATE' && change.previousScanType && change.previousScannedAt
            ? `${scan(change.previousScanType, change.previousScannedAt)} → ${scan(
                  change.newScanType,
                  change.newScannedAt,
              )}`
            : t('club.participant.tracking.manual.changeCreated', {
                  entry: scan(change.newScanType, change.newScannedAt),
              })
    }

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
            <DialogTitle sx={{pr: 6}}>
                {t('club.participant.tracking.manual.title', {name: participantName})}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{pt: 1}}>
                    {/*
                        Der Hinweis war auf dem beigen Grund schwer zu lesen und drückte das
                        Formular nach unten. Jetzt kompakt und einklappbar: die Regel steht einmal
                        da, nimmt aber nicht die halbe Höhe (Rückmeldung vom 10.08.2026). `filled`
                        trägt sichtbar mehr Kontrast als die getönte Standardvariante.
                    */}
                    <Alert severity="warning" variant="filled" sx={{'& a': {color: 'inherit'}}}>
                        {t('club.participant.tracking.manual.hint')}
                    </Alert>

                    {error ? (
                        <Typography color="error">
                            {t('common.load.error.single', {
                                entity: t('club.participant.tracking.log'),
                            })}
                        </Typography>
                    ) : pending && data === null ? (
                        <Throbber />
                    ) : (
                        <>
                            <Box>
                                <Typography variant="subtitle2" gutterBottom>
                                    {t('club.participant.tracking.manual.entries')}
                                </Typography>
                                {data?.entries.length === 0 ? (
                                    <Typography variant="body2" color="text.secondary">
                                        {t('club.participant.tracking.manual.noEntries')}
                                    </Typography>
                                ) : (
                                    <List dense disablePadding>
                                        {data?.entries.map(entry => (
                                            <ListItem
                                                key={entry.id}
                                                disableGutters
                                                secondaryAction={
                                                    <IconButton
                                                        edge="end"
                                                        aria-label={t(
                                                            'club.participant.tracking.manual.correct',
                                                        )}
                                                        onClick={() => openEdit(entry)}>
                                                        <Edit fontSize="small" />
                                                    </IconButton>
                                                }>
                                                <ListItemText
                                                    primary={
                                                        <Stack
                                                            direction="row"
                                                            spacing={1}
                                                            alignItems="center"
                                                            flexWrap="wrap"
                                                            useFlexGap>
                                                            <Chip
                                                                size="small"
                                                                color={
                                                                    entry.scanType === 'ENTRY'
                                                                        ? 'success'
                                                                        : 'default'
                                                                }
                                                                label={t(
                                                                    `club.participant.tracking.${
                                                                        entry.scanType === 'ENTRY'
                                                                            ? 'in'
                                                                            : 'out'
                                                                    }`,
                                                                )}
                                                            />
                                                            <span>
                                                                {format(
                                                                    new Date(entry.scannedAt),
                                                                    t('format.datetime'),
                                                                )}
                                                            </span>
                                                            {sourceChip(entry)}
                                                        </Stack>
                                                    }
                                                    secondary={
                                                        entry.recordedBy
                                                            ? `${entry.recordedBy.firstname} ${entry.recordedBy.lastname}`
                                                            : undefined
                                                    }
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                )}
                            </Box>

                            {formOpen ? (
                                <Box>
                                    <Divider sx={{mb: 2}} />
                                    <Typography variant="subtitle2" gutterBottom>
                                        {editing !== null
                                            ? t('club.participant.tracking.manual.correct')
                                            : t('club.participant.tracking.manual.add')}
                                    </Typography>
                                    <Stack spacing={2}>
                                        <ToggleButtonGroup
                                            exclusive
                                            size="small"
                                            value={scanType}
                                            onChange={(_, value) => value && setScanType(value)}>
                                            <ToggleButton value="ENTRY">
                                                {t('club.participant.tracking.in')}
                                            </ToggleButton>
                                            <ToggleButton value="EXIT">
                                                {t('club.participant.tracking.out')}
                                            </ToggleButton>
                                        </ToggleButtonGroup>
                                        <DateTimePicker
                                            ampm={false}
                                            label={t('club.participant.tracking.lastScan.at')}
                                            value={scannedAt}
                                            onChange={setScannedAt}
                                        />
                                        <TextField
                                            required
                                            multiline
                                            minRows={2}
                                            label={t('club.participant.tracking.manual.reason')}
                                            helperText={t(
                                                'club.participant.tracking.manual.reasonHint',
                                            )}
                                            value={reason}
                                            onChange={e => setReason(e.target.value)}
                                        />
                                    </Stack>
                                </Box>
                            ) : (
                                <Box>
                                    {/* Kontrast: der zurückhaltende Umriss-Knopf war auf hellem
                                        Grund kaum vom Text zu unterscheiden. Gefüllt bleibt er der
                                        bewusst nicht als Primärweg gestaltete Notbehelf, ist aber
                                        klar als Knopf erkennbar. */}
                                    <Button
                                        variant="contained"
                                        color="warning"
                                        onClick={openAdd}
                                        disabled={pending}>
                                        {t('club.participant.tracking.manual.add')}
                                    </Button>
                                </Box>
                            )}

                            {data !== null && data.changes.length > 0 && (
                                <Box>
                                    <Divider sx={{mb: 2}} />
                                    <Typography variant="subtitle2" gutterBottom>
                                        {t('club.participant.tracking.manual.trail')}
                                    </Typography>
                                    <List dense disablePadding>
                                        {data.changes.map(change => (
                                            <ListItem key={change.id} disableGutters>
                                                <ListItemText
                                                    primary={changeLine(change)}
                                                    secondary={[
                                                        format(
                                                            new Date(change.createdAt),
                                                            t('format.datetime'),
                                                        ),
                                                        change.createdBy
                                                            ? `${change.createdBy.firstname} ${change.createdBy.lastname}`
                                                            : null,
                                                        change.reason,
                                                    ]
                                                        .filter(Boolean)
                                                        .join(' · ')}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                </Box>
                            )}
                        </>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                {formOpen ? (
                    <>
                        <Button onClick={closeForm}>{t('common.cancel')}</Button>
                        <LoadingButton
                            pending={submitting}
                            variant="contained"
                            color="warning"
                            disabled={scannedAt === null || reason.trim() === ''}
                            onClick={submit}>
                            {t('common.save')}
                        </LoadingButton>
                    </>
                ) : (
                    <Button onClick={onClose}>{t('common.close')}</Button>
                )}
            </DialogActions>
        </Dialog>
    )
}

export default ParticipantTrackingDialog
