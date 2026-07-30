import {
    Box,
    Chip,
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardRequirementStatusDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {formatMinutes, requirementSeverity, severityChipColor, Severity} from './common.ts'

type Props = {
    team: LiveDashboardTeamDto | null
    onClose: () => void
}

const severityIcon = (severity: Severity) => {
    switch (severity) {
        case 'ok':
            return <CheckCircleIcon sx={{color: 'success.dark'}} />
        case 'warning':
            return <WarningAmberIcon sx={{color: 'warning.dark'}} />
        case 'error':
            return <CancelIcon sx={{color: 'error.dark'}} />
        case 'neutral':
            return <RadioButtonUncheckedIcon sx={{color: 'text.disabled'}} />
    }
}

const LiveDashboardTeamDialog = ({team, onClose}: Props) => {
    const {t} = useTranslation()

    if (team === null) {
        return null
    }

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
                {team.teamName ?? team.clubName ?? ''}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        {(team.actualClubName ?? team.clubName) && (
                            <Typography variant="body2">
                                {team.actualClubName ?? team.clubName}
                            </Typography>
                        )}
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.invoice.${team.invoiceState}`)}
                            color={
                                team.invoiceState === 'PAID'
                                    ? 'success'
                                    : team.invoiceState === 'OPEN'
                                      ? 'error'
                                      : 'default'
                            }
                        />
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
                    {team.participants.map(p => (
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
                                    {p.requirements.map(r => {
                                        const severity = requirementSeverity(r)
                                        return (
                                            <ListItem key={r.requirementId} disableGutters>
                                                <ListItemIcon sx={{minWidth: 36}}>
                                                    {severityIcon(severity)}
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
                                                                            severityChipColor[severity]
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
                                        )
                                    })}
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
