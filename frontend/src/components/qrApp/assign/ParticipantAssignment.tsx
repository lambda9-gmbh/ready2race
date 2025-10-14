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
import {ParticipantQrAssignmentDto} from "@api/types.gen.ts";
import AssignmentSearchField from "@components/qrApp/assign/AssignmentSearchField.tsx";

interface ParticipantAssignmentProps {
    eventId: string
    onSelectParticipant: (
        participant: ParticipantQrAssignmentDto,
        competitionName: string,
    ) => void
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

    const filteredParticipants = useMemo(() =>{
            return groupedParticipants
                ?.map(group => ({
                    ...group,
                    participants: group.participants.filter(
                        p =>
                            searchQuery === '' ||
                            `${p.firstname} ${p.lastname}`
                                .toLowerCase()
                                .includes(searchQuery.toLowerCase()),
                    ),
                }))
                .filter(group => group.participants.length > 0)
                .sort((a, b) => (a.competitionName > b.competitionName ? 1 : -1)) || []
    }, [searchQuery, groupedParticipants])

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
                        <Stack spacing={2}>
                            {filteredParticipants.map(group => (
                                <Box key={group.competitionRegistrationId}>
                                    <Typography
                                        variant="subtitle2"
                                        color="text.secondary"
                                        sx={{mb: 1}}>
                                        {group.competitionName}
                                    </Typography>
                                    <Stack spacing={1}>
                                        {group.participants.map(participant => (
                                            <Card
                                                key={participant.participantId}
                                                sx={{
                                                    opacity: participant.qrCodeValue ? 0.6 : 1,
                                                    cursor: participant.qrCodeValue
                                                        ? 'not-allowed'
                                                        : 'pointer',
                                                }}>
                                                <CardActionArea
                                                    onClick={() =>
                                                        !participant.qrCodeValue &&
                                                        onSelectParticipant(
                                                            participant,
                                                            group.competitionName,
                                                        )
                                                    }
                                                    disabled={participant.qrCodeValue !== undefined}>
                                                    <CardContent sx={{py: 2}}>
                                                        <Box
                                                            sx={{
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                gap: 1,
                                                            }}>
                                                            <PersonIcon
                                                                sx={{color: 'text.secondary'}}
                                                            />
                                                            <Box sx={{flexGrow: 1}}>
                                                                <Typography
                                                                    variant="body1"
                                                                    fontWeight="medium">
                                                                    {participant.firstname}{' '}
                                                                    {participant.lastname}
                                                                </Typography>
                                                                <Typography
                                                                    variant="body2"
                                                                    color="text.secondary">
                                                                    {participant.namedParticipantName}
                                                                </Typography>
                                                            </Box>
                                                        </Box>
                                                    </CardContent>
                                                </CardActionArea>
                                            </Card>
                                        ))}
                                    </Stack>
                                </Box>
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
