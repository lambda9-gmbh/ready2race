import {
    Alert,
    Box,
    Button,
    Card,
    CardActions,
    CardContent,
    Checkbox,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Paper,
    Stack,
    Typography
} from "@mui/material";
import {useEffect, useState} from "react";
import {qrEventRoute, router} from "@routes";
import {
    approveParticipantRequirementsForEvent,
    checkInTeam,
    checkOutTeam,
    deleteQrCode,
    getParticipantRequirementsForEvent,
    getParticipantsForEvent,
    getTeamsByParticipantQrCode
} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import {
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal
} from '@authorization/privileges';
import {ParticipantRequirementForEventDto, TeamStatusWithParticipantsDto} from '@api/types.gen.ts';
import {Business, Cancel, EmojiEvents, Login, Logout, Person} from '@mui/icons-material';
import {useFeedback} from '@utils/hooks.ts';

const QrParticipantPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [requirements, setRequirements] = useState<ParticipantRequirementForEventDto[]>([]);
    const [checkedRequirements, setCheckedRequirements] = useState<string[]>([]);
    const [pending, setPending] = useState(false);
    const [teams, setTeams] = useState<TeamStatusWithParticipantsDto[]>([]);
    const [loadingTeams, setLoadingTeams] = useState(false);
    const [teamActionLoading, setTeamActionLoading] = useState<Set<string>>(new Set());
    const navigate = router.navigate
    const feedback = useFeedback();

    useEffect(() => {
        if (!appFunction) {
            navigate({to: "/app/function"})
            return;
        }
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    useEffect(() => {
        const load = async () => {
            if (appFunction === 'APP_EVENT_REQUIREMENT' && qr.response?.id && eventId) {
                setPending(true);
                const [{data: reqData}, {data: partData}] = await Promise.all([
                    getParticipantRequirementsForEvent({
                        path: {eventId, participantId: qr.response?.id},
                        throwOnError: true
                    }),
                    getParticipantsForEvent({
                        path: {eventId},
                        throwOnError: true
                    })
                ]);
                setRequirements((reqData?.data || []).filter((r) => r.checkInApp));
                const participant = (partData?.data || []).find((p) => p.id === qr.response?.id);
                setCheckedRequirements(Array.isArray(participant?.participantRequirementsChecked)
                    ? participant.participantRequirementsChecked.map((r) => r.id).filter((id: string | undefined): id is string => !!id)
                    : []);
                setPending(false);
            }
        };
        load();
    }, [appFunction, qr.response?.id, eventId]);

    useEffect(() => {
        const loadTeams = async () => {
            if (appFunction === 'APP_COMPETITION_CHECK' && qr.qrCodeId && eventId) {
                setLoadingTeams(true);
                try {
                    const {data} = await getTeamsByParticipantQrCode({
                        path: {qrCode: qr.qrCodeId},
                        query: {eventId},
                        throwOnError: true
                    });
                    setTeams(data || []);
                } catch (error) {
                    console.error('Error loading teams:', error);
                    setTeams([]);
                } finally {
                    setLoadingTeams(false);
                }
            }
        };
        loadTeams();
    }, [appFunction, qr.qrCodeId, eventId]);

    const handleRequirementChange = async (requirementId: string, checked: boolean) => {
        if (!qr.response?.id) return;
        setPending(true);
        await approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId,
                approvedParticipants: checked ? [qr.response.id] : [],
            },
            throwOnError: true
        });
        // Nach Änderung neu laden
        const {data: partData} = await getParticipantsForEvent({path: {eventId}});
        const participant = (partData?.data || []).find((p) => p.id === qr.response?.id);
        setCheckedRequirements(Array.isArray(participant?.participantRequirementsChecked)
            ? participant.participantRequirementsChecked.map((r) => r.id).filter((id: string | undefined): id is string => !!id)
            : []);
        setPending(false);
    };

    const allowed = appFunction !== null && [
        updateAppCompetitionCheckGlobal.resource,
        updateAppQrManagementGlobal.resource,
        updateAppEventRequirementGlobal.resource,
        updateAppCatererGlobal.resource
    ].includes(appFunction);
    const canCheck = appFunction === updateAppCompetitionCheckGlobal.resource;
    const canRemove = appFunction === updateAppQrManagementGlobal.resource;
    const canEditRequirements = appFunction === updateAppEventRequirementGlobal.resource;
    const isCaterer = appFunction === updateAppCatererGlobal.resource;

    const handleDelete = async () => {
        setLoading(true);
        setError(null);
        try {
            await deleteQrCode({
                path: {qrCodeId: qr.qrCodeId!},
                throwOnError: true
            });
            setDialogOpen(false);
            qr.reset(eventId);
        } catch (e) {
            setError((e as Error)?.message || t('qrParticipant.deleteError'));
        } finally {
            setLoading(false);
        }
    };

    const handleTeamCheckIn = async (team: TeamStatusWithParticipantsDto) => {
        setTeamActionLoading(prev => new Set(prev).add(team.competitionRegistrationId));
        try {
            const result = await checkInTeam({
                path: {teamId: team.competitionRegistrationId},
                body: {eventId}
            });

            if (result.data) {
                feedback.success(t('team.checkIn.success'));
                // Reload teams to get updated status
                const {data} = await getTeamsByParticipantQrCode({
                    path: {qrCode: qr.qrCodeId!},
                    query: {eventId},
                    throwOnError: true
                });
                setTeams(data || []);
            }
        } catch {
            feedback.error(t('team.checkIn.error'));
        } finally {
            setTeamActionLoading(prev => {
                const newSet = new Set(prev);
                newSet.delete(team.competitionRegistrationId);
                return newSet;
            });
        }
    };

    const handleTeamCheckOut = async (team: TeamStatusWithParticipantsDto) => {
        setTeamActionLoading(prev => new Set(prev).add(team.competitionRegistrationId));
        try {
            const result = await checkOutTeam({
                path: {teamId: team.competitionRegistrationId},
                body: {eventId},
                throwOnError: true
            });

            if (result.data) {
                feedback.success(t('team.checkOut.success'));
                // Reload teams to get updated status
                const {data} = await getTeamsByParticipantQrCode({
                    path: {qrCode: qr.qrCodeId!},
                    query: {eventId}
                });
                setTeams(data || []);
            }
        } catch {
            feedback.error(t('team.checkOut.error'));
        } finally {
            setTeamActionLoading(prev => {
                const newSet = new Set(prev);
                newSet.delete(team.competitionRegistrationId);
                return newSet;
            });
        }
    };

    return (
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{width: '100%', maxWidth: 600}}
        >
            <Typography variant="h4" textAlign="center" gutterBottom>
                {t('qrParticipant.title')}
            </Typography>

            {/* Caterer food restriction message */}
            {isCaterer && qr.response && (
                <Alert severity="error" icon={<Cancel/>} sx={{mb: 2, width: '100%'}}>
                    {t('club.participant.foodNotAllowed')}
                </Alert>
            )}

            {/* QR Code Assignment Info Box */}
            {qr.response && allowed && !isCaterer && (
                <Paper elevation={2} sx={{p: 2, width: '100%', bgcolor: 'background.default'}}>
                    <Stack spacing={1.5}>
                        <Typography variant="h6" color="primary">
                            {t('qrParticipant.assignmentInfo')}
                        </Typography>

                        <Stack direction="row" spacing={1} alignItems="center">
                            <Person color="action"/>
                            <Typography>
                                <strong>{t('common.name')}:</strong> {qr.response.firstname} {qr.response.lastname}
                            </Typography>
                        </Stack>

                        {qr.response.type === 'Participant' && 'clubName' in qr.response && qr.response.clubName && (
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Business color="action"/>
                                <Typography>
                                    <strong>{t('club.club')}:</strong> {qr.response.clubName}
                                </Typography>
                            </Stack>
                        )}

                        {qr.response.type === 'Participant' && 'competitions' in qr.response && qr.response.competitions && qr.response.competitions.length > 0 && (
                            <Stack spacing={1}>
                                <Stack direction="row" spacing={1} alignItems="center">
                                    <EmojiEvents color="action"/>
                                    <Typography>
                                        <strong>{t('event.competition.competitions')}:</strong>
                                    </Typography>
                                </Stack>
                                <Box sx={{pl: 4}}>
                                    {qr.response.competitions.map((comp, index) => (
                                        <Typography key={index} variant="body2">
                                            • {comp}
                                        </Typography>
                                    ))}
                                </Box>
                            </Stack>
                        )}
                    </Stack>
                </Paper>
            )}

            {!allowed && (
                <Alert severity="warning">{t('qrParticipant.noRight')}</Alert>
            )}
            {canCheck && (
                <Stack spacing={2} sx={{width: '100%', maxWidth: 600}}>
                    <Typography variant="h6">{t('team.teams')}</Typography>
                    {loadingTeams ? (
                        <Box display="flex" justifyContent="center" p={2}>
                            <CircularProgress/>
                        </Box>
                    ) : teams.length === 0 ? (
                        <Alert severity="info">{t('team.noTeamsFound')}</Alert>
                    ) : (
                        teams.map((team) => (
                            <Card key={team.competitionRegistrationId} variant="outlined">
                                <CardContent>
                                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                                        <Box>
                                            <Typography variant="h6">{team.teamName}</Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {team.clubName ?? t('common.noClub')}
                                            </Typography>
                                        </Box>
                                        <Chip
                                            label={team.currentStatus === 'ENTRY' ? t('team.status.in') : t('team.status.out')}
                                            color={team.currentStatus === 'ENTRY' ? 'success' : 'default'}
                                            size="small"
                                        />
                                    </Stack>
                                    {team.lastScanAt && (
                                        <Typography variant="caption" color="text.secondary" sx={{mt: 1}}>
                                            {t('team.lastScan')}: {new Date(team.lastScanAt).toLocaleString()}
                                        </Typography>
                                    )}
                                </CardContent>
                                <CardActions>
                                    {team.currentStatus === 'ENTRY' ? (
                                        <Button
                                            startIcon={<Logout/>}
                                            onClick={() => handleTeamCheckOut(team)}
                                            disabled={teamActionLoading.has(team.competitionRegistrationId)}
                                            variant="outlined"
                                            fullWidth
                                        >
                                            {t('team.checkOutText')}
                                        </Button>
                                    ) : (
                                        <Button
                                            startIcon={<Login/>}
                                            onClick={() => handleTeamCheckIn(team)}
                                            disabled={teamActionLoading.has(team.competitionRegistrationId)}
                                            variant="contained"
                                            fullWidth
                                        >
                                            {t('team.checkInText')}
                                        </Button>
                                    )}
                                </CardActions>
                            </Card>
                        ))
                    )}
                </Stack>
            )}
            {canEditRequirements && (
                <Stack spacing={1}>
                    <Typography variant="h6">{t('participantRequirement.participantRequirements')}</Typography>
                    {pending && <Typography>{t('qrParticipant.loading') as string}</Typography>}
                    {requirements.length === 0 && !pending &&
                        <Typography>{t('qrParticipant.noRequirements') as string}</Typography>}
                    {requirements.map(req => (
                        <FormControlLabel
                            key={req.id}
                            control={
                                <Checkbox
                                    checked={checkedRequirements.includes(req.id)}
                                    onChange={e => handleRequirementChange(req.id, e.target.checked)}
                                    disabled={pending}
                                />
                            }
                            label={req.name}
                        />
                    ))}
                </Stack>
            )}
            {canRemove && (
                <Button
                    color="error"
                    variant="contained"
                    fullWidth
                    onClick={() => setDialogOpen(true)}
                >
                    {t('qrParticipant.removeAssignment')}
                </Button>
            )}
            <Button variant={'outlined'} onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>{t('qrParticipant.removeAssignmentTitle')}</DialogTitle>
                <DialogContent>
                    <Typography>{t('qrParticipant.removeAssignmentConfirm')}</Typography>
                    {error && <Alert severity="error" sx={{mt: 2}}>{error}</Alert>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogOpen(false)} disabled={loading}>{t('common.cancel')}</Button>
                    <Button onClick={handleDelete} color="error" variant="contained" disabled={loading}>
                        {t('common.delete')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrParticipantPage