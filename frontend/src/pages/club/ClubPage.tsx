import {
    Box,
    Button,
    CardActions,
    CardContent,
    IconButton,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {clubRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '../../components/Throbber.tsx'
import {
    ClubDto,
    getClub,
    getClubUsers,
    getPendingClubRepresentativeApprovals,
    ParticipantDto,
    updateClubRepresentativeApproval,
} from '../../api'
import ParticipantTable from '../../components/participant/ParticipantTable.tsx'
import ParticipantDialog from '../../components/participant/ParticipantDialog.tsx'
import Card from '@mui/material/Card'
import {AccountCircle, Cancel, CheckCircle, Edit, Email} from '@mui/icons-material'
import ClubDialog from '@components/club/ClubDialog.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateClubOwn} from '@authorization/privileges.ts'
import {useState} from 'react'
import {format} from 'date-fns'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'

const ClubPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const {confirmAction} = useConfirmation()
    const theme = useTheme()

    const {clubId} = clubRoute.useParams()
    const [pendingApprovalsRefresh, setPendingApprovalsRefresh] = useState(0)

    const isMobile = useMediaQuery(theme.breakpoints.down('md'))

    const clubProps = useEntityAdministration<ClubDto>(t('club.club'), {entityCreate: false})

    const {data: clubData, pending: clubDataPending} = useFetch(
        signal => getClub({signal, path: {clubId: clubId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('club.club')}))
                }
            },
            deps: [clubId, clubProps.table.lastRequested],
        },
    )

    const {data: userData} = useFetch(signal => getClubUsers({signal, path: {clubId: clubId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('club.club')}))
            }
        },
        deps: [clubId, pendingApprovalsRefresh],
    })

    const {data: pendingApprovalsData} = useFetch(
        signal => getPendingClubRepresentativeApprovals({signal, path: {clubId: clubId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('club.club')}))
                }
            },
            preCondition: () => user.loggedIn && user.checkPrivilege(updateClubOwn),
            deps: [clubId, pendingApprovalsRefresh],
        },
    )

    const participantProps = useEntityAdministration<ParticipantDto>(t('club.participant.title'))

    const handleApprove = async (userId: string, username: string) => {
        confirmAction(
            async () => {
                const {error} = await updateClubRepresentativeApproval({
                    path: {userId},
                    query: {approve: true},
                })
                if (error) {
                    feedback.error(t('club.representative.approve.error'))
                } else {
                    feedback.success(t('club.representative.approve.success'))
                    setPendingApprovalsRefresh(prev => prev + 1)
                }
            },
            {
                title: t('club.representative.approve.confirmation.title', {username: username}),
                content: t('club.representative.approve.confirmation.content', {
                    username: username,
                }),
                okText: t('club.representative.approve.approve'),
            },
        )
    }

    const handleDeny = async (userId: string, username: string) => {
        confirmAction(
            async () => {
                const {error} = await updateClubRepresentativeApproval({
                    path: {userId},
                    query: {approve: false},
                })
                if (error) {
                    feedback.error(t('club.representative.deny.error'))
                } else {
                    feedback.success(t('club.representative.deny.success'))
                    setPendingApprovalsRefresh(prev => prev + 1)
                }
            },
            {
                title: t('club.representative.deny.confirmation.title', {username: username}),
                content: t('club.representative.deny.confirmation.content', {username: username}),
                okText: t('club.representative.deny.deny'),
            },
        )
    }

    return (
        <Stack spacing={4}>
            {clubData ? (
                <>
                    <Stack direction={'row'} spacing={1}>
                        <Typography variant="h2">{clubData.name}</Typography>
                        <IconButton
                            onClick={() => clubProps.table.openDialog(clubData)}
                            className="cursor-pointer">
                            <Edit />
                        </IconButton>
                    </Stack>
                    {pendingApprovalsData && pendingApprovalsData.length > 0 && (
                        <Stack spacing={1}>
                            <Typography variant="h6">
                                {t('club.representative.pendingApprovals')}
                            </Typography>
                            <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                                {pendingApprovalsData.map(approval => (
                                    <Card
                                        key={approval.userId}
                                        sx={{
                                            flex: 1,
                                            minWidth: isMobile ? 1 : 350,
                                            maxWidth: isMobile ? 1 : 350,
                                        }}>
                                        <CardContent>
                                            <Stack direction={'row'} spacing={1}>
                                                <Typography
                                                    variant={'h5'}
                                                    sx={{color: 'text.secondary'}}>
                                                    <AccountCircle fontSize={'large'} />
                                                </Typography>
                                                <Stack>
                                                    <Typography variant="h5">
                                                        {approval.firstName} {approval.lastName}
                                                    </Typography>
                                                    <Typography variant={'body2'}>
                                                        {approval.email}
                                                    </Typography>
                                                    <Typography variant="body2">
                                                        {format(
                                                            new Date(approval.createdAt),
                                                            t('format.datetime'),
                                                        )}
                                                    </Typography>
                                                </Stack>
                                            </Stack>
                                        </CardContent>
                                        <CardActions sx={{justifyContent: 'end', gap: 1}}>
                                            <Button
                                                startIcon={<Cancel />}
                                                size="small"
                                                variant="outlined"
                                                color="error"
                                                onClick={() =>
                                                    handleDeny(
                                                        approval.userId,
                                                        `${approval.firstName} ${approval.lastName}`,
                                                    )
                                                }
                                                className="cursor-pointer">
                                                {t('club.representative.deny.deny')}
                                            </Button>
                                            <Button
                                                startIcon={<CheckCircle />}
                                                size="small"
                                                variant="contained"
                                                color="primary"
                                                onClick={() =>
                                                    handleApprove(
                                                        approval.userId,
                                                        `${approval.firstName} ${approval.lastName}`,
                                                    )
                                                }
                                                className="cursor-pointer">
                                                {t('club.representative.approve.approve')}
                                            </Button>
                                        </CardActions>
                                    </Card>
                                ))}
                            </Box>
                        </Stack>
                    )}
                    <Stack spacing={1}>
                        <Typography variant="h6">
                            {t('club.representative.representatives')}
                        </Typography>{' '}
                        <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                            {userData?.map(u => (
                                <Card
                                    key={u.id}
                                    sx={{
                                        flex: 1,
                                        minWidth: isMobile ? 1 : 350,
                                        maxWidth: isMobile ? 1 : 350,
                                    }}>
                                    <CardContent>
                                        <Stack direction={'row'} spacing={1}>
                                            <Typography
                                                variant={'h5'}
                                                sx={{color: 'text.secondary'}}>
                                                <AccountCircle fontSize={'large'} />
                                            </Typography>
                                            <Stack>
                                                <Typography variant="h5">
                                                    {u.firstname} {u.lastname}
                                                </Typography>
                                                <Typography sx={{color: 'text.secondary'}}>
                                                    {u.roles.map(r => r.name).join(', ')}
                                                </Typography>
                                            </Stack>
                                        </Stack>
                                    </CardContent>
                                    <CardActions sx={{justifyContent: 'end'}}>
                                        <Button
                                            startIcon={<Email />}
                                            size="small"
                                            onClick={e => {
                                                window.location.href = `mailto:${u.email}`
                                                e.preventDefault()
                                            }}
                                            className="cursor-pointer">
                                            {u.email}
                                        </Button>
                                    </CardActions>
                                </Card>
                            ))}
                        </Box>
                    </Stack>
                    <Box>
                        <ParticipantTable
                            {...participantProps.table}
                            title={t('club.participants')}
                        />
                        <ParticipantDialog {...participantProps.dialog} />
                        <ClubDialog {...clubProps.dialog} />
                    </Box>
                </>
            ) : (
                clubDataPending && <Throbber />
            )}
        </Stack>
    )
}

export default ClubPage
