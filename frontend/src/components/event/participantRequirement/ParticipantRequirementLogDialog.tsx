import {
    Alert,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {getParticipantRequirementLog} from '@api/sdk.gen.ts'
import {ParticipantRequirementLogEntryDto} from '@api/types.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber.tsx'
import {format} from 'date-fns'

/**
 * Die Revisionsspur einer Bedingung: wer hat wann welche Bestätigung gesetzt oder zurückgenommen,
 * und auf welchem Weg (Waage-Scan, Abgleich im Büro, Datei-Import).
 *
 * Der Anlass steht in der Migration V202608152000: Als am Regattatag Bestätigungen verschwanden,
 * ließ sich nicht sagen, wer sie entfernt hat - die Erfüllungstabelle kennt nur den aktuellen
 * Stand. Genau deshalb ist die Rücknahme hier die wichtigere Zeile: Sie ist der einzige Ort, an
 * dem eine gelöschte Bestätigung überhaupt noch auftaucht.
 */
type Props = {
    open: boolean
    onClose: () => void
    eventId: string
    requirementId?: string
    requirementName?: string
}

const ParticipantRequirementLogDialog = ({
    open,
    onClose,
    eventId,
    requirementId,
    requirementName,
}: Props) => {
    const {t} = useTranslation()

    const {data, pending} = useFetch(
        signal =>
            getParticipantRequirementLog({
                signal,
                path: {eventId},
                query: requirementId ? {requirementId} : {},
            }),
        {
            preCondition: () => open,
            deps: [open, eventId, requirementId],
        },
    )

    const entries: ParticipantRequirementLogEntryDto[] = data?.data ?? []

    const scopeOf = (entry: ParticipantRequirementLogEntryDto): string =>
        [
            entry.competitionName,
            entry.eventDayDate ? format(new Date(entry.eventDayDate), 'dd.MM.yyyy') : null,
        ]
            .filter(Boolean)
            .join(' · ')

    return (
        <Dialog open={open} onClose={onClose} maxWidth={'lg'} fullWidth>
            <DialogTitle>
                {t('event.participantRequirement.log.title')}
                {requirementName ? ` — ${requirementName}` : ''}
            </DialogTitle>
            <DialogContent>
                {pending ? (
                    <Throbber />
                ) : entries.length === 0 ? (
                    <Alert severity="info">{t('event.participantRequirement.log.empty')}</Alert>
                ) : (
                    <TableContainer>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>{t('event.participantRequirement.log.when')}</TableCell>
                                    <TableCell>{t('club.participant.title')}</TableCell>
                                    <TableCell>{t('event.participantRequirement.log.action')}</TableCell>
                                    <TableCell>{t('event.participantRequirement.log.scope')}</TableCell>
                                    <TableCell>{t('event.participantRequirement.log.source')}</TableCell>
                                    <TableCell>{t('event.participantRequirement.log.by')}</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {entries.map(entry => (
                                    <TableRow key={entry.id}>
                                        <TableCell>
                                            {format(new Date(entry.createdAt), 'dd.MM. HH:mm:ss')}
                                        </TableCell>
                                        <TableCell>
                                            <Stack>
                                                <Typography variant="body2">
                                                    {entry.participantName}
                                                </Typography>
                                                {entry.clubName && (
                                                    <Typography
                                                        variant="caption"
                                                        color="text.secondary">
                                                        {entry.clubName}
                                                    </Typography>
                                                )}
                                            </Stack>
                                        </TableCell>
                                        <TableCell>
                                            {/* Die Rücknahme ist die Zeile, wegen der es die Spur
                                                gibt - sie wird auch so hervorgehoben. */}
                                            <Chip
                                                size="small"
                                                color={
                                                    entry.action === 'REVOKED' ? 'error' : 'success'
                                                }
                                                label={t(
                                                    `event.participantRequirement.log.actions.${entry.action}`,
                                                )}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" color="text.secondary">
                                                {scopeOf(entry) ||
                                                    t('event.participantRequirement.log.wholeEvent')}
                                            </Typography>
                                            {entry.note && (
                                                <Typography variant="caption" color="text.secondary">
                                                    {entry.note}
                                                </Typography>
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            {t(
                                                `event.participantRequirement.log.sources.${entry.source}`,
                                            )}
                                        </TableCell>
                                        <TableCell>{entry.createdBy ?? '-'}</TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </DialogContent>
        </Dialog>
    )
}

export default ParticipantRequirementLogDialog
