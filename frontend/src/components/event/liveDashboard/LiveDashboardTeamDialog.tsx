import {
    Box,
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
    Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardRequirementStatusDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {getLiveDashboardTeamDetail} from '@api/sdk.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import {formatMinutes, severityChipColor} from './common.ts'
import SeverityIcon from './SeverityIcon.tsx'

type Props = {
    team: LiveDashboardTeamDto | null
    /** Der Lauf, aus dem die Mannschaft angetippt wurde — die Aufstellung gilt je Runde. */
    matchId: string | null
    eventId: string
    onClose: () => void
}

/**
 * Der Dialog trägt die Personendaten selbst nach: sie sind der größte Posten im Poll und werden
 * erst hier gebraucht. Geladen wird einmal beim Öffnen — Teilnahmebedingungen werden am Zelt
 * abgehakt und ändern sich während eines Laufs praktisch nicht.
 */
const LiveDashboardTeamDialog = ({team, matchId, eventId, onClose}: Props) =>
    team === null || matchId === null ? null : (
        <TeamDialog team={team} matchId={matchId} eventId={eventId} onClose={onClose} />
    )

const TeamDialog = ({
    team,
    matchId,
    eventId,
    onClose,
}: {
    team: LiveDashboardTeamDto
    matchId: string
    eventId: string
    onClose: () => void
}) => {
    const {t} = useTranslation()

    const {data: detail, pending} = useFetch(
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
        }
        if (r.note) {
            parts.push(t('event.liveDashboard.requirement.note', {note: r.note}))
        }
        return parts.join(' · ')
    }

    return (
        <Dialog open onClose={onClose} fullWidth maxWidth="sm">
            <DialogTitle sx={{pr: 6}}>
                {team.startNumber != null && `#${team.startNumber} — `}
                {team.teamName ?? team.clubsFull}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        {/*
                            Die volle Kette, nie die Kurzform: der Dialog ist die Stelle, an der
                            nachgesehen wird, welcher Verein genau gemeint ist.
                        */}
                        {team.clubsFull !== '' && (
                            <Typography variant="body2">{team.clubsFull}</Typography>
                        )}
                        {/*
                            Die Team-Ampel hält Grün den Teilnahmebedingungen vor: Rechnung und
                            Wasser können sie nur verschlechtern, nie bestätigen (siehe
                            `LiveDashboardLogic.invoiceSeverity`/`onWaterSeverity`), deshalb liefert
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
                        {team.onWaterRequired && (
                            <Chip
                                size="small"
                                color={
                                    team.onWaterAt
                                        ? 'success'
                                        : severityChipColor[team.onWaterSeverity]
                                }
                                label={
                                    team.onWaterAt
                                        ? t('event.liveDashboard.team.onWaterAt', {
                                              time: format(
                                                  new Date(team.onWaterAt),
                                                  t('format.time'),
                                              ),
                                          })
                                        : t('event.liveDashboard.team.notOnWater')
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
                    {pending && detail === null && (
                        <Box display="flex" justifyContent="center" py={2}>
                            <CircularProgress />
                        </Box>
                    )}
                    {detail?.participants.map(p => (
                        <Box key={p.participantId}>
                            <Typography variant="subtitle1">
                                {p.firstName} {p.lastName}
                                {p.namedRole && (
                                    <Typography component="span" variant="body2" color="text.secondary">
                                        {' '}
                                        ({p.namedRole})
                                    </Typography>
                                )}
                            </Typography>
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
    )
}

export default LiveDashboardTeamDialog
