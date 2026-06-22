import {
    Autocomplete,
    Box,
    Card,
    CardActionArea,
    CardContent,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import React, {useMemo} from 'react'
import {useFetch} from '@utils/hooks.ts'
import {getClubNames, getQrAssignmentParticipants} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import PersonIcon from '@mui/icons-material/Person'
import AssignmentSearchField from "@components/qrApp/assign/AssignmentSearchField.tsx";

export interface ParticipantAssignmentContext {
    competitionName: string
    role: string
}

export interface DedupedParticipant {
    participantId: string
    firstname: string
    lastname: string
    qrCodeValue?: string
    contexts: ParticipantAssignmentContext[]
}

interface ParticipantAssignmentProps {
    eventId: string
    onSelectParticipant: (participant: DedupedParticipant) => void
}

const ParticipantAssignment: React.FC<ParticipantAssignmentProps> = ({
    eventId,
    onSelectParticipant,
}) => {
    const {t} = useTranslation()
    const [club, setClub] = React.useState<string>('')
    const [searchQuery, setSearchQuery] = React.useState('')

    const clubs = useFetch(signal => getClubNames({signal, query: {eventId: eventId}})).data

    const groupedParticipants = useFetch(
        signal =>
            getQrAssignmentParticipants({
                signal,
                query: {eventId: eventId, clubId: club || undefined},
            }),
        {deps: [club]},
    ).data

    const handleClubChange = (
        _event: React.SyntheticEvent,
        newValue: { id: string; name: string } | null,
    ) => {
        setClub(newValue?.id || '')
    }

    // The backend returns participants grouped by competition registration (a
    // substitution-aware roster). A qr code is assigned per participant + event,
    // so the same participantId legitimately shows up once per competition. Collapse
    // to one entry per participant and keep the competitions/roles only as context.
    const dedupedParticipants = useMemo<DedupedParticipant[]>(() => {
        const byId = new Map<string, DedupedParticipant>()
        groupedParticipants?.forEach(group => {
            group.participants.forEach(p => {
                const context = {
                    competitionName: group.competitionName,
                    role: p.namedParticipantName,
                }
                const existing = byId.get(p.participantId)
                if (existing) {
                    // qrCodeValue is event-scoped, so it is identical across occurrences
                    existing.qrCodeValue = existing.qrCodeValue ?? p.qrCodeValue ?? undefined
                    if (
                        !existing.contexts.some(
                            c => c.competitionName === context.competitionName && c.role === context.role,
                        )
                    ) {
                        existing.contexts.push(context)
                    }
                } else {
                    byId.set(p.participantId, {
                        participantId: p.participantId,
                        firstname: p.firstname,
                        lastname: p.lastname,
                        qrCodeValue: p.qrCodeValue ?? undefined,
                        contexts: [context],
                    })
                }
            })
        })
        return Array.from(byId.values())
    }, [groupedParticipants])

    const filteredParticipants = useMemo(
        () =>
            dedupedParticipants
                .filter(
                    p =>
                        searchQuery === '' ||
                        `${p.firstname} ${p.lastname}`.toLowerCase().includes(searchQuery.toLowerCase()),
                )
                .sort((a, b) =>
                    `${a.lastname} ${a.firstname}`.localeCompare(`${b.lastname} ${b.firstname}`),
                ),
        [searchQuery, dedupedParticipants],
    )

    return (
        <>
            {clubs && (
                <Stack sx={{width: '100%'}} spacing={2}>
                    <Typography variant="subtitle1" fontWeight="medium">
                        {t('qrAssign.clubs')}
                    </Typography>
                    <Autocomplete
                        value={clubs.data.find(c => c.id === club) || null}
                        onChange={handleClubChange}
                        options={clubs.data}
                        getOptionLabel={option => option.name}
                        renderInput={params => <TextField {...params} label={t('common.all')} />}
                        fullWidth
                        clearOnEscape
                        isOptionEqualToValue={(option, value) => option.id === value?.id}
                    />
                </Stack>
            )}

            {groupedParticipants && (
                <Stack sx={{width: '100%'}} spacing={2}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Typography variant="subtitle1" fontWeight="medium">
                            {t('qrAssign.participants')}
                        </Typography>
                    </Stack>
                    <AssignmentSearchField setSearchQuery={setSearchQuery} />

                    {filteredParticipants.length > 0 ? (
                        <Stack spacing={1}>
                            {filteredParticipants.map(participant => (
                                <Card
                                    key={participant.participantId}
                                    sx={{
                                        opacity: participant.qrCodeValue ? 0.6 : 1,
                                        cursor: participant.qrCodeValue ? 'not-allowed' : 'pointer',
                                    }}>
                                    <CardActionArea
                                        onClick={() =>
                                            !participant.qrCodeValue && onSelectParticipant(participant)
                                        }
                                        disabled={participant.qrCodeValue !== undefined}>
                                        <CardContent sx={{py: 2}}>
                                            <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                                                <PersonIcon sx={{color: 'text.secondary'}} />
                                                <Box sx={{flexGrow: 1}}>
                                                    <Typography variant="body1" fontWeight="medium">
                                                        {participant.firstname} {participant.lastname}
                                                    </Typography>
                                                    {participant.contexts.map((c, i) => (
                                                        <Typography
                                                            key={i}
                                                            variant="body2"
                                                            color="text.secondary">
                                                            {c.competitionName} ({c.role})
                                                        </Typography>
                                                    ))}
                                                </Box>
                                            </Box>
                                        </CardContent>
                                    </CardActionArea>
                                </Card>
                            ))}
                        </Stack>
                    ) : (
                        <Typography align="center" color="text.secondary">
                            {t('qrAssign.noData')}
                        </Typography>
                    )}
                </Stack>
            )}
        </>
    )
}

export default ParticipantAssignment