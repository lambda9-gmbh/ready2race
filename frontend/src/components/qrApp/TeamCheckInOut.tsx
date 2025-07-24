import {
    Stack,
    Typography,
    Card,
    CardContent,
    CardActions,
    Button,
    Box,
    Chip,
    CircularProgress,
    Alert
} from "@mui/material";
import { Login, Logout } from '@mui/icons-material';
import { useTranslation } from "react-i18next";
import { TeamStatusWithParticipantsDto } from '@api/types.gen.ts';

interface TeamCheckInOutProps {
    teams: TeamStatusWithParticipantsDto[];
    loading: boolean;
    teamActionLoading: Set<string>;
    onCheckIn: (team: TeamStatusWithParticipantsDto) => void;
    onCheckOut: (team: TeamStatusWithParticipantsDto) => void;
}

export const TeamCheckInOut = ({
    teams,
    loading,
    teamActionLoading,
    onCheckIn,
    onCheckOut
}: TeamCheckInOutProps) => {
    const { t } = useTranslation();

    if (loading) {
        return (
            <Box display="flex" justifyContent="center" p={2}>
                <CircularProgress />
            </Box>
        );
    }

    if (teams.length === 0) {
        return <Alert severity="info">{t('team.noTeamsFound')}</Alert>;
    }

    return (
        <Stack spacing={2} sx={{ width: '100%' }}>
            <Typography variant="h6">{t('team.teams')}</Typography>
            {teams.map((team) => (
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
                            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
                                {t('team.lastScan')}: {new Date(team.lastScanAt).toLocaleString()}
                            </Typography>
                        )}
                    </CardContent>
                    <CardActions>
                        {team.currentStatus === 'ENTRY' ? (
                            <Button
                                startIcon={<Logout />}
                                onClick={() => onCheckOut(team)}
                                disabled={teamActionLoading.has(team.competitionRegistrationId)}
                                variant="outlined"
                                fullWidth
                            >
                                {t('team.checkOutText')}
                            </Button>
                        ) : (
                            <Button
                                startIcon={<Login />}
                                onClick={() => onCheckIn(team)}
                                disabled={teamActionLoading.has(team.competitionRegistrationId)}
                                variant="contained"
                                fullWidth
                            >
                                {t('team.checkInText')}
                            </Button>
                        )}
                    </CardActions>
                </Card>
            ))}
        </Stack>
    );
};