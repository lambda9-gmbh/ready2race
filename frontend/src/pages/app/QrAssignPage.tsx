import {
    Alert,
    Autocomplete,
    Box,
    Button,
    Card,
    CardActionArea,
    CardContent,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
    InputAdornment
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {useFetch} from "@utils/hooks.ts";
import {
    getClubNames,
    getQrAssignmentParticipants,
    getUsers,
    updateQrCodeAppuser,
    updateQrCodeParticipant
} from "@api/sdk.gen.ts";
import {qrEventRoute} from "@routes";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import QrNimiqScanner from "@components/qrApp/QrNimiqScanner.tsx";
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';

type UserTyp = "Participant" | "User" | "SystemUser"

interface ConfirmationData {
    id: string;
    name: string;
    type: 'participant' | 'user';
    additionalInfo?: string[];
}

const QrAssignPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const [userTyp, setUserTyp] = useState<UserTyp>("Participant")
    const [club, setClub] = useState<string>('')
    const [scanningSystemUser, setScanningSystemUser] = useState(false)
    const [searchQuery, setSearchQuery] = useState('')
    const [confirmationOpen, setConfirmationOpen] = useState(false)
    const [selectedPerson, setSelectedPerson] = useState<ConfirmationData | null>(null)
    const {eventId} = qrEventRoute.useParams()

    useEffect(() => {
        if (!appFunction) {
            window.location.href = '/app/function';
            return;
        }
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    const handleUserTypChange = (_event: React.MouseEvent<HTMLElement>, newUserTyp: UserTyp | null) => {
        if (newUserTyp !== null && newUserTyp !== "SystemUser") {
            setUserTyp(newUserTyp);
            setSearchQuery('');
            setScanningSystemUser(false);
        }
    };

    const handleScanSystemUser = () => {
        setScanningSystemUser(true);
    };

    const handleClubChange = (_event: React.SyntheticEvent, newValue: {id: string, name: string} | null) => {
        setClub(newValue?.id || '');
    };

    const clubs = useFetch(signal => getClubNames({signal, query: {eventId: eventId}}), {
        deps: [userTyp],
        preCondition: () => userTyp === "Participant"
    }).data

    const groupedParticipants = useFetch(
        signal => getQrAssignmentParticipants({signal, query: {eventId: eventId, clubId: club || undefined}}), 
        {
            deps: [club, userTyp],
            preCondition: () => userTyp === "Participant"
        }
    ).data;

    const users = useFetch(signal => getUsers({signal}), {
        deps: [userTyp],
        preCondition: () => userTyp === "User"
    }).data

    const handleParticipantClick = (participant: {
        participantId: string;
        firstname: string;
        lastname: string;
        namedParticipant: string;
        qrCodeValue?: string | null;
    }, competitionName: string) => {
        setSelectedPerson({
            id: participant.participantId,
            name: `${participant.firstname} ${participant.lastname}`,
            type: 'participant',
            additionalInfo: [
                clubs?.data.find(c => c.id === club)?.name || '',
                competitionName,
                participant.namedParticipant
            ]
        });
        setConfirmationOpen(true);
    };

    const handleUserClick = (user: {
        id: string;
        firstname: string;
        lastname: string;
    }) => {
        setSelectedPerson({
            id: user.id,
            name: `${user.firstname} ${user.lastname}`,
            type: 'user'
        });
        setConfirmationOpen(true);
    };

    const handleConfirmSelection = async () => {
        if (!selectedPerson) return;
        
        if (selectedPerson.type === 'participant') {
            await updateQrCodeParticipant({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id
                }
            });
        } else {
            await updateQrCodeAppuser({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id
                }
            });
        }
        
        setConfirmationOpen(false);
        qr.reset(eventId);
    };

    const handleCloseConfirmation = () => {
        setConfirmationOpen(false);
        setSelectedPerson(null);
    };

    const handleSystemUserScan = async (qrCodeContent: string) => {
        try {
            const data = JSON.parse(qrCodeContent)
            if (data.appUserId) {
                setScanningSystemUser(false)
                await updateQrCodeAppuser({
                    body: {
                        qrCodeId: qr.qrCodeId!,
                        eventId: eventId,
                        id: data.appUserId
                    }
                });
                qr.reset(eventId);
            } else if (data.participantId) {
                setScanningSystemUser(false)
                await updateQrCodeParticipant({
                    body: {
                        qrCodeId: qr.qrCodeId!,
                        eventId: eventId,
                        id: data.participantId
                    }
                });
                qr.reset(eventId);
            } else {
                alert(t('qrAssign.invalidUserQr'))
            }
        } catch {
            alert(t('qrAssign.invalidQrFormat'))
        }
    }

    const canAssign = appFunction === 'APP_QR_MANAGEMENT';

    const filteredParticipants = groupedParticipants?.map(group => ({
        ...group,
        participants: group.participants.filter(p => 
            searchQuery === '' || 
            `${p.firstname} ${p.lastname}`.toLowerCase().includes(searchQuery.toLowerCase())
        )
    })).filter(group => group.participants.length > 0) || [];

    const filteredUsers = users?.data.filter(user => 
        searchQuery === '' || 
        `${user.firstname} ${user.lastname}`.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <Stack
            spacing={3}
            alignItems="center"
            justifyContent="flex-start"
            sx={{width: '100%', maxWidth: 600, px: 2, py: 3}}
        >
            <Box sx={{ width: '100%', textAlign: 'center' }}>
                <Typography variant="h4" gutterBottom>
                    {t('qrAssign.title')}
                </Typography>
                <Typography variant="body2" color="text.secondary">{qr.qrCodeId}</Typography>
            </Box>
            
            {!canAssign && (
                <Alert severity="warning" sx={{ width: '100%' }}>{t('qrAssign.noPermission')}</Alert>
            )}
            
            {canAssign && (
                <>
                    <Stack spacing={2} sx={{ width: '100%' }}>
                        <ToggleButtonGroup
                            value={userTyp}
                            exclusive
                            onChange={handleUserTypChange}
                            aria-label="user type selection"
                            sx={{ width: '100%' }}
                        >
                            <ToggleButton value="Participant" sx={{ flex: 1 }}>
                                {t('qrAssign.participant')}
                            </ToggleButton>
                            <ToggleButton value="User" sx={{ flex: 1 }}>
                                {t('qrAssign.user')}
                            </ToggleButton>
                        </ToggleButtonGroup>
                        
                        <Button
                            variant="outlined"
                            fullWidth
                            onClick={handleScanSystemUser}
                            sx={{ py: 1.5 }}
                        >
                            {t('qrAssign.scanSystemUser')}
                        </Button>
                    </Stack>

                    {clubs && userTyp === "Participant" && !scanningSystemUser && (
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

                    {groupedParticipants && userTyp === "Participant" && !scanningSystemUser && (
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
                                                            onClick={() => !participant.qrCodeValue && handleParticipantClick(participant, group.competitionName)}
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

                    {users && userTyp === "User" && !scanningSystemUser && (
                        <Stack sx={{ width: '100%' }} spacing={2}>
                            <Typography variant="subtitle1" fontWeight="medium">
                                {t('qrAssign.users')}
                            </Typography>
                            
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
                                sx={{ mb: 1 }}
                            />
                            
                            <Stack spacing={1}>
                                {filteredUsers && filteredUsers.length > 0 ? (
                                    filteredUsers.map(user => (
                                        <Card key={user.id}>
                                            <CardActionArea onClick={() => handleUserClick(user)}>
                                                <CardContent sx={{ py: 2 }}>
                                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                        <PersonIcon sx={{ color: 'text.secondary' }} />
                                                        <Typography variant="body1" fontWeight="medium">
                                                            {user.firstname} {user.lastname}
                                                        </Typography>
                                                    </Box>
                                                </CardContent>
                                            </CardActionArea>
                                        </Card>
                                    ))
                                ) : (
                                    <Typography align="center" color="text.secondary">
                                        {t('qrAssign.noData')}
                                    </Typography>
                                )}
                            </Stack>
                        </Stack>
                    )}

                    {!scanningSystemUser && (
                        <Button variant="outlined" onClick={() => qr.reset(eventId)} fullWidth sx={{ mt: 2 }}>
                            {t('common.back')}
                        </Button>
                    )}
                </>
            )}
            
            {canAssign && scanningSystemUser && (
                <Stack spacing={2} sx={{ width: '100%' }}>
                    <Typography variant="h6" align="center">
                        {t('qrAssign.scanUserQr')}
                    </Typography>
                    <QrNimiqScanner callback={handleSystemUserScan}/>
                    <Button variant="outlined" onClick={() => setScanningSystemUser(false)} fullWidth>
                        {t('common.back')}
                    </Button>
                </Stack>
            )}
            
            <Dialog open={confirmationOpen} onClose={handleCloseConfirmation} maxWidth="sm" fullWidth>
                <DialogTitle>{t('common.confirmation.title')}</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ pt: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <PersonIcon sx={{ fontSize: 48, color: 'text.secondary' }} />
                            <Box>
                                <Typography variant="h6">{selectedPerson?.name}</Typography>
                                {selectedPerson?.additionalInfo && selectedPerson.additionalInfo.map((info, index) => (
                                    <Typography key={index} variant="body2" color="text.secondary">
                                        {info}
                                    </Typography>
                                ))}
                            </Box>
                        </Box>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseConfirmation} variant="outlined">
                        {t('common.back')}
                    </Button>
                    <Button onClick={handleConfirmSelection} variant="contained" color="primary">
                        {t('common.ok')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrAssignPage