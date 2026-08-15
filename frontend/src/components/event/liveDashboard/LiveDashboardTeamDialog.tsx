import {
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    List,
    ListItem,
    ListItemIcon,
    ListItemText,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import EditIcon from '@mui/icons-material/Edit'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardRequirementStatusDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {getLiveDashboardTeamDetail} from '@api/sdk.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {updateEventGlobal, updateLiveDashboardGlobal} from '@authorization/privileges.ts'
import ParticipantTrackingDialog from '@components/event/participantTracking/ParticipantTrackingDialog.tsx'
import {canSubmitNote, formatMinutes, severityChipColor} from './common.ts'
import SeverityIcon from './SeverityIcon.tsx'

type Props = {
    team: LiveDashboardTeamDto | null
    /** Der Lauf, aus dem die Mannschaft angetippt wurde — die Aufstellung gilt je Runde. */
    matchId: string | null
    eventId: string
    onClose: () => void
    /**
     * Nur gesetzt, wenn geschrieben werden darf UND der Stand aktuell ist — dasselbe
     * `actionsLocked`-Muster wie bei den fünf Schreibaktionen der Karten (siehe
     * LiveDashboardPage): ohne Handler verschwinden Eingabefeld und Lösch-Knöpfe, die Liste
     * bleibt lesbar.
     */
    onAddNote?: (matchId: string, teamId: string, note: string) => Promise<void>
    onDeleteNote?: (matchId: string, teamId: string, noteId: string) => Promise<void>
}

/**
 * Der Dialog trägt die Personendaten selbst nach: sie sind der größte Posten im Poll und werden
 * erst hier gebraucht. Geladen wird einmal beim Öffnen — Teilnahmebedingungen werden am Zelt
 * abgehakt und ändern sich während eines Laufs praktisch nicht.
 */
const LiveDashboardTeamDialog = ({team, matchId, eventId, onClose, onAddNote, onDeleteNote}: Props) =>
    team === null || matchId === null ? null : (
        <TeamDialog
            team={team}
            matchId={matchId}
            eventId={eventId}
            onClose={onClose}
            onAddNote={onAddNote}
            onDeleteNote={onDeleteNote}
        />
    )

const TeamDialog = ({
    team,
    matchId,
    eventId,
    onClose,
    onAddNote,
    onDeleteNote,
}: {
    team: LiveDashboardTeamDto
    matchId: string
    eventId: string
    onClose: () => void
    onAddNote?: (matchId: string, teamId: string, note: string) => Promise<void>
    onDeleteNote?: (matchId: string, teamId: string, noteId: string) => Promise<void>
}) => {
    const {t} = useTranslation()
    const user = useUser()
    // Dieselben zwei Rechte wie im Backend (siehe participantForEvent.kt): der manuelle Nachtrag
    // steht Schiedsrichtern und Admins offen.
    const mayEditTracking =
        user.checkPrivilege(updateLiveDashboardGlobal) || user.checkPrivilege(updateEventGlobal)
    const [tracked, setTracked] = useState<{id: string; name: string} | null>(null)
    const {confirmAction} = useConfirmation()
    const [noteText, setNoteText] = useState('')
    // Sperrt Feld und Knopf, solange die Notiz unterwegs ist - ein doppelter Klick würde sonst
    // zwei gleiche Einträge anlegen (append-only: der Server fasst nichts zusammen).
    const [noteSubmitting, setNoteSubmitting] = useState(false)

    const notes = team.notes ?? []

    const submitNote = async () => {
        if (!onAddNote || !canSubmitNote(noteText) || noteSubmitting) {
            return
        }
        setNoteSubmitting(true)
        try {
            await onAddNote(matchId, team.teamId, noteText.trim())
            setNoteText('')
        } finally {
            setNoteSubmitting(false)
        }
    }

    const {data: detail, pending, reload} = useFetch(
        signal =>
            getLiveDashboardTeamDetail({
                signal,
                path: {eventId, matchId, teamId: team.teamId},
            }),
        {deps: [eventId, matchId, team.teamId]},
    )

    const requirementSecondary = (r: LiveDashboardRequirementStatusDto): string => {
        const parts: string[] = []
        if (r.checked && r.checkedAt) {
            parts.push(
                t('event.liveDashboard.requirement.checkedAt', {
                    time: format(new Date(r.checkedAt), t('format.datetime')),
                }),
            )
        } else if (!r.checked) {
            parts.push(
                r.optional
                    ? t('event.liveDashboard.requirement.notCheckedOptional')
                    : t('event.liveDashboard.requirement.notChecked'),
            )
        }
        if (r.timeCheck?.deltaMinutes != null) {
            parts.push(
                r.timeCheck.deltaMinutes >= 0
                    ? t('event.liveDashboard.timeCheck.beforeStart', {
                          delta: formatMinutes(r.timeCheck.deltaMinutes),
                      })
                    : t('event.liveDashboard.timeCheck.afterStart', {
                          delta: formatMinutes(r.timeCheck.deltaMinutes),
                      }),
            )
            // Ohne diesen Zusatz liest sich die Abweichung als Aussage über DIESEN Lauf. Sie
            // gehört aber zum ersten Lauf des Rahmens: Die Bedingung gilt je Tag und Wettkampf,
            // wer einmal gewogen ist, ist es für alle Runden - der Vergleich muss deshalb auch
            // einen festen Punkt haben und nicht bei jeder Runde weiterwandern.
            if (r.timeCheck.referenceIsFrameStart && r.timeCheck.referenceStartTime) {
                parts.push(
                    t('event.liveDashboard.timeCheck.frameReference', {
                        time: format(new Date(r.timeCheck.referenceStartTime), t('format.time')),
                    }),
                )
            }
        }
        if (r.note) {
            parts.push(t('event.liveDashboard.requirement.note', {note: r.note}))
        }
        return parts.join(' · ')
    }

    return (
        <>
        {tracked !== null && (
            <ParticipantTrackingDialog
                open
                onClose={() => setTracked(null)}
                eventId={eventId}
                participantId={tracked.id}
                participantName={tracked.name}
                onChanged={reload}
            />
        )}
        <Dialog open onClose={onClose} fullWidth maxWidth="sm">
            {/*
                Startnummer und Mannschaftsname, nie die Kette: die stand hier in vollen Namen und
                war auf dem Telefon fünf Zeilen Titeltext — unmittelbar darüber, wo sie gleich
                darunter noch einmal steht.
            */}
            <DialogTitle sx={{pr: 6}}>
                {/*
                    Der Mannschaftsname ist bei Vereinen mit mehreren Booten nur ein Zähler der
                    Form „#2" (Platzhalter aus CompetitionRegistrationService). Zusammen mit der
                    Startnummer davor stand dann „#1 — #2" im Titel und las sich wie „Lauf 1 bis 2"
                    (beobachtet am 10.08.2026). Ist der Name so ein Zähler, wird er zu „Boot 2"
                    ausgeschrieben; ein echter Name bleibt, wie er ist. Die Startnummer bekommt ihr
                    Wort, damit die beiden Zahlen nicht mehr gleich aussehen.
                */}
                {[
                    team.startNumber != null
                        ? t('event.liveDashboard.team.startNumberLabel', {
                              number: team.startNumber,
                          })
                        : null,
                    team.teamName != null && /^#\d+$/.test(team.teamName)
                        ? t('event.liveDashboard.team.boatLabel', {
                              number: team.teamName.slice(1),
                          })
                        : team.teamName,
                ]
                    .filter(Boolean)
                    .join(' · ')}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {/*
                        Die volle Kette, nie die Kurzform: der Dialog ist die Stelle, an der
                        nachgesehen wird, welcher Verein genau gemeint ist. Sie steht in einer
                        eigenen Zeile und nicht mehr zwischen den Schildern — dort schob sie am
                        Telefon jedes Schild in eine eigene Zeile.
                    */}
                    {team.clubsFull !== '' && (
                        <Typography
                            variant="body2"
                            color="text.secondary"
                            aria-label={t('event.liveDashboard.team.clubs')}>
                            {team.clubsFull}
                        </Typography>
                    )}
                    <Stack
                        direction="row"
                        spacing={1}
                        alignItems="center"
                        flexWrap="wrap"
                        useFlexGap>
                        {/*
                            Die Team-Ampel hält Grün den Teilnahmebedingungen vor: Rechnung und
                            Arena können sie nur verschlechtern, nie bestätigen (siehe
                            `LiveDashboardLogic.invoiceSeverity`/`inArenaSeverity`), deshalb liefert
                            das Backend hier `NEUTRAL` statt `OK`. Dieses Schild sagt aber nichts
                            über die Mannschaft insgesamt, sondern genau eine Tatsache ("bezahlt" /
                            "abgelegt um ...") - und die darf grün sein, wenn sie zutrifft. Nur wenn
                            sie NICHT zutrifft, zählt der eingestellte Schweregrad.
                        */}
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.invoice.${team.invoiceState}`)}
                            color={
                                team.invoiceState === 'PAID'
                                    ? 'success'
                                    : severityChipColor[team.invoiceSeverity]
                            }
                        />
                        {team.inArenaRequired && (
                            <Chip
                                size="small"
                                color={
                                    team.inArenaAt
                                        ? 'success'
                                        : severityChipColor[team.inArenaSeverity]
                                }
                                label={
                                    team.inArenaAt
                                        ? t('event.liveDashboard.team.inArenaAt', {
                                              time: format(
                                                  new Date(team.inArenaAt),
                                                  t('format.time'),
                                              ),
                                          })
                                        : t('event.liveDashboard.team.notInArena')
                                }
                            />
                        )}
                        {team.penaltySeconds != null && (
                            <Chip
                                size="small"
                                color="warning"
                                label={
                                    t('event.competition.execution.results.penalty', {
                                        seconds: team.penaltySeconds,
                                    }) + (team.penaltyNote ? ` · ${team.penaltyNote}` : '')
                                }
                            />
                        )}
                        {team.deregistered && (
                            <Chip
                                size="small"
                                color="warning"
                                label={t('event.liveDashboard.team.deregistered')}
                            />
                        )}
                    </Stack>
                    {/*
                        Notizen zwischen Schiedsrichtern ("Boje berührt") - Kommunikation, keine
                        Wertung, deshalb ein eigener Abschnitt und kein Chip bei den Wertungen
                        oben. Die Liste kommt aus dem Sekunden-Poll (team.notes) und zieht nach
                        dem Anlegen/Löschen über den Dashboard-Reload nach; lesbar für alle mit
                        Dashboard-Zugriff, schreibbar nur mit Handler (actionsLocked-Muster).
                    */}
                    <Box>
                        <Typography variant="subtitle1" fontWeight={700}>
                            {t('event.liveDashboard.notes.title')}
                        </Typography>
                        {notes.length === 0 ? (
                            <Typography variant="body2" color="text.secondary">
                                {t('event.liveDashboard.notes.empty')}
                            </Typography>
                        ) : (
                            <List dense disablePadding>
                                {notes.map(note => (
                                    <ListItem
                                        key={note.id}
                                        disableGutters
                                        secondaryAction={
                                            onDeleteNote && (
                                                <IconButton
                                                    edge="end"
                                                    size="small"
                                                    aria-label={t(
                                                        'event.liveDashboard.notes.delete',
                                                    )}
                                                    onClick={() =>
                                                        confirmAction(
                                                            async () =>
                                                                onDeleteNote(
                                                                    matchId,
                                                                    team.teamId,
                                                                    note.id,
                                                                ),
                                                            {
                                                                content: t(
                                                                    'event.liveDashboard.notes.deleteConfirm',
                                                                    {note: note.note},
                                                                ),
                                                                okText: t('common.delete'),
                                                            },
                                                        )
                                                    }>
                                                    <DeleteOutlineIcon fontSize="small" />
                                                </IconButton>
                                            )
                                        }>
                                        <ListItemText
                                            primary={note.note}
                                            secondary={t('event.liveDashboard.notes.byAt', {
                                                author:
                                                    note.author ??
                                                    t('event.liveDashboard.notes.unknownAuthor'),
                                                time: format(
                                                    new Date(note.createdAt),
                                                    t('format.datetime'),
                                                ),
                                            })}
                                        />
                                    </ListItem>
                                ))}
                            </List>
                        )}
                        {onAddNote && (
                            <Stack
                                direction="row"
                                spacing={1}
                                alignItems="flex-start"
                                sx={{mt: 1}}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    multiline
                                    maxRows={3}
                                    label={t('event.liveDashboard.notes.inputLabel')}
                                    placeholder={t('event.liveDashboard.notes.placeholder')}
                                    value={noteText}
                                    onChange={e => setNoteText(e.target.value)}
                                    disabled={noteSubmitting}
                                />
                                <Button
                                    variant="outlined"
                                    onClick={submitNote}
                                    disabled={!canSubmitNote(noteText) || noteSubmitting}>
                                    {t('event.liveDashboard.notes.add')}
                                </Button>
                            </Stack>
                        )}
                        <Divider sx={{mt: 1.5}} />
                    </Box>
                    {pending && detail === null && (
                        <Box display="flex" justifyContent="center" py={2}>
                            <CircularProgress />
                        </Box>
                    )}
                    {detail?.participants.map(p => (
                        <Box key={p.participantId}>
                            <Stack
                                direction="row"
                                spacing={1}
                                alignItems="center"
                                flexWrap="wrap"
                                useFlexGap>
                                <Typography variant="subtitle1">
                                    {p.firstName} {p.lastName}
                                    {/* Jahrgang und Rolle hinter dem Namen - der Jahrgang gehört
                                        zur Ansage (Wunsch von Lea, 10.08.2026). */}
                                    {(p.year != null || p.namedRole) && (
                                        <Typography component="span" variant="body2" color="text.secondary">
                                            {' '}
                                            ({[p.year, p.namedRole].filter(Boolean).join(', ')})
                                        </Typography>
                                    )}
                                </Typography>
                                {/*
                                    Der Steg-Scan dieser Person. Der Chip oben sagt nur, ob das
                                    ganze Boot draußen ist - an wem es hängt, steht erst hier, und
                                    genau das braucht, wer den fehlenden Eintrag nachträgt.
                                */}
                                <Chip
                                    size="small"
                                    color={p.trackingStatus === 'ENTRY' ? 'success' : 'default'}
                                    label={
                                        p.trackingStatus == null
                                            ? t('club.participant.tracking.manual.notTracked')
                                            : t(
                                                  `club.participant.tracking.${p.trackingStatus === 'ENTRY' ? 'in' : 'out'}`,
                                              ) +
                                              (p.trackingAt
                                                  ? ` ${format(new Date(p.trackingAt), t('format.time'))}`
                                                  : '')
                                    }
                                />
                                {mayEditTracking && (
                                    <IconButton
                                        size="small"
                                        aria-label={t('club.participant.tracking.manual.open')}
                                        onClick={() =>
                                            setTracked({
                                                id: p.participantId,
                                                name: `${p.firstName} ${p.lastName}`,
                                            })
                                        }>
                                        <EditIcon fontSize="small" />
                                    </IconButton>
                                )}
                            </Stack>
                            {/*
                                Der Verein JEDER Person, nicht der der Meldung: bei einem
                                vereinsgemischten Boot ist genau das die Angabe, die der
                                Schiedsrichter hier sucht.
                            */}
                            {p.clubName && (
                                <Typography variant="body2" color="text.secondary">
                                    {p.clubName}
                                </Typography>
                            )}
                            {p.substitutedFor && (
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                    <SwapHorizIcon sx={{fontSize: 20, color: 'info.dark'}} />
                                    <Typography variant="body2" color="info.dark">
                                        {t('event.liveDashboard.substitution.for', {
                                            name: p.substitutedFor,
                                        })}
                                        {p.substitutionReason && ` · ${p.substitutionReason}`}
                                    </Typography>
                                </Stack>
                            )}
                            {p.requirements.length === 0 ? (
                                <Typography variant="body2" color="text.secondary">
                                    {t('event.liveDashboard.requirement.none')}
                                </Typography>
                            ) : (
                                <List dense disablePadding>
                                    {p.requirements.map(r => (
                                        <ListItem key={r.requirementId} disableGutters>
                                            <ListItemIcon sx={{minWidth: 36}}>
                                                <SeverityIcon severity={r.severity} size={24} />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={
                                                    <Stack
                                                        direction="row"
                                                        spacing={1}
                                                        alignItems="center">
                                                        <span>{r.name}</span>
                                                        {r.timeCheck &&
                                                            r.timeCheck.status !== 'OK' && (
                                                                <Chip
                                                                    size="small"
                                                                    color={
                                                                        severityChipColor[
                                                                            r.severity
                                                                        ]
                                                                    }
                                                                    label={t(
                                                                        `event.liveDashboard.timeCheck.${r.timeCheck.status}`,
                                                                    )}
                                                                />
                                                            )}
                                                    </Stack>
                                                }
                                                secondary={requirementSecondary(r)}
                                            />
                                        </ListItem>
                                    ))}
                                </List>
                            )}
                            <Divider sx={{mt: 1}} />
                        </Box>
                    ))}
                </Stack>
            </DialogContent>
        </Dialog>
        </>
    )
}

export default LiveDashboardTeamDialog
