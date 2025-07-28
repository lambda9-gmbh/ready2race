import {
    Autocomplete,
    Box,
    Card,
    CardActionArea,
    CardContent,
    InputAdornment,
    Stack,
    TextField,
    Typography
} from "@mui/material";
import React from "react";
import { useFetch } from "@utils/hooks.ts";
import { getClubNames, getQrAssignmentParticipants } from "@api/sdk.gen.ts";
import { useTranslation } from "react-i18next";
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';

interface ParticipantAssignmentProps {
    eventId: string;
    onSelectParticipant: (participant: {
        participantId: string;
        firstname: string;
        lastname: string;
        namedParticipant: string;
        qrCodeValue?: string | null;
    }, competitionName: string) => void;
}

const ParticipantAssignment: React.FC<ParticipantAssignmentProps> = ({ eventId, onSelectParticipant }) => {
    const { t } = useTranslation();
    const [club, setClub] = React.useState<string>('');
    const [searchQuery, setSearchQuery] = React.useState('');

    const clubs = useFetch(signal => getClubNames({ signal, query: { eventId: eventId } })).data;

    const groupedParticipants = useFetch(
        signal => getQrAssignmentParticipants({ signal, query: { eventId: eventId, clubId: club || undefined } }),
        { deps: [club] }
    ).data;

    const handleClubChange = (_event: React.SyntheticEvent, newValue: { id: string, name: string } | null) => {
        setClub(newValue?.id || '');
    };

    const filteredParticipants = groupedParticipants?.map(group => ({
        ...group,
        participants: group.participants.filter(p =>
            searchQuery === '' ||
            `${p.firstname} ${p.lastname}`.toLowerCase().includes(searchQuery.toLowerCase())
        )
    })).filter(group => group.participants.length > 0) || [];

    return (
        <>
            {clubs && (
                <Stack sx={{ width: '100%' }} spacing={2}>
                    <Typography variant="subtitle1" fontWeight="medium">
                        {t('qrAssign.clubs')}
                    </Typography>
                    <Autocomplete
                        value={clubs.data.find(c => c.id === club) || null}
                        onChange={handleClubChange}
                        options={clubs.data}
                        getOptionLabel={(option) => option.name}
                        renderInput={(params) => (
                            <TextField {...params} label={t('common.all')} />
                        )}
                        fullWidth
                        clearOnEscape
                        isOptionEqualToValue={(option, value) => option.id === value?.id}
                    />
                </Stack>
            )}

            {groupedParticipants && (
                <Stack sx={{ width: '100%' }} spacing={2}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Typography variant="subtitle1" fontWeight="medium">
                            {t('qrAssign.participants')}
                        </Typography>
                    </Stack>

                    <TextField
                        fullWidth
                        placeholder={t('common.search')}
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        slotProps={{
                            input: {
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <SearchIcon />
                                    </InputAdornment>
                                ),
                            }
                        }}
                        sx={{ mb: 2 }}
                    />

                    {filteredParticipants.length > 0 ? (
                        <Stack spacing={2}>
                            {filteredParticipants.map((group) => (
                                <Box key={group.competitionRegistration}>
                                    <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                                        {group.competitionName}
                                    </Typography>
                                    <Stack spacing={1}>
                                        {group.participants.map(participant => (
                                            <Card
                                                key={participant.participantId}
                                                sx={{
                                                    opacity: participant.qrCodeValue ? 0.6 : 1,
                                                    cursor: participant.qrCodeValue ? 'not-allowed' : 'pointer'
                                                }}
                                            >
                                                <CardActionArea
                                                    onClick={() => !participant.qrCodeValue && onSelectParticipant(participant, group.competitionName)}
                                                    disabled={!!participant.qrCodeValue}
                                                >
                                                    <CardContent sx={{ py: 2 }}>
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            <PersonIcon sx={{ color: 'text.secondary' }} />
                                                            <Box sx={{ flexGrow: 1 }}>
                                                                <Typography variant="body1" fontWeight="medium">
                                                                    {participant.firstname} {participant.lastname}
                                                                </Typography>
                                                                <Typography variant="body2" color="text.secondary">
                                                                    {participant.namedParticipant}
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
    );
};

export default ParticipantAssignment;