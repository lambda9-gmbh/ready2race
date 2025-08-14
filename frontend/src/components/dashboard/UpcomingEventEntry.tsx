import {
    Box,
    Button,
    Divider,
    ListItem,
    ListItemText,
    Stack,
    Typography,
    useTheme,
} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {Event, Forward, LocationOn} from '@mui/icons-material'
import {ClockIcon} from '@mui/x-date-pickers'
import {getRegistrationPeriods, getRegistrationState} from '@utils/helpers.ts'
import {useTranslation} from 'react-i18next'
import {EventPublicDto} from '@api/types.gen.ts'

type Props = {
    event: EventPublicDto
    hideRegistration?: boolean
}

const UpcomingEventEntry = ({event, hideRegistration}: Props) => {
    const theme = useTheme()
    const {t} = useTranslation()

    const {registrationPeriod, lateRegistrationPeriod} = getRegistrationPeriods(event, t)

    const registrationState = getRegistrationState(event)

    return (
        <ListItem>
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    width: 1,
                    [theme.breakpoints.down('md')]: {
                        flexDirection: 'column',
                    },
                }}>
                <ListItemText
                    sx={{flex: 1}}
                    disableTypography={true}
                    primary={
                        <Stack>
                            <Stack direction={'row'} spacing={2} alignItems={'center'}>
                                <Link to={'/event/$eventId'} params={{eventId: event.id}}>
                                    <Button>
                                        <Typography color={'primary'} variant={'h6'}>
                                            {event.name}
                                        </Typography>
                                    </Button>
                                </Link>
                                <Stack
                                    direction="row"
                                    divider={<Divider orientation="vertical" flexItem />}
                                    spacing={2}>
                                    {event.eventFrom != null && (
                                        <Stack direction="row" spacing={0.5}>
                                            <Event
                                                fontSize={'small'}
                                                sx={{
                                                    color: 'text.secondary',
                                                }}
                                            />
                                            <Typography color={'text.secondary'}>
                                                {new Date(event.eventFrom).toLocaleDateString()}
                                                {event.eventTo !== event.eventFrom &&
                                                    ` - ${new Date(
                                                        event.eventTo!!,
                                                    ).toLocaleDateString()}`}
                                            </Typography>
                                        </Stack>
                                    )}
                                    {event.location && (
                                        <Stack direction="row" spacing={0.5}>
                                            <LocationOn
                                                fontSize={'small'}
                                                sx={{
                                                    color: 'text.secondary',
                                                }}
                                            />
                                            <Typography color={'text.secondary'}>
                                                {event.location}
                                            </Typography>
                                        </Stack>
                                    )}
                                    <Typography color={'text.secondary'}>
                                        {event.competitionCount}{' '}
                                        {t('event.competition.competitions')}
                                    </Typography>
                                </Stack>
                            </Stack>
                            {event.description && (
                                <Stack p={1}>
                                    <Typography fontWeight={'light'}>
                                        {event.description}
                                    </Typography>
                                </Stack>
                            )}
                        </Stack>
                    }
                    secondary={
                        <Stack
                            fontSize={'1em'}
                            direction={'row'}
                            p={1}
                            spacing={1}
                            divider={<Divider orientation="vertical" flexItem />}>
                            <ClockIcon fontSize={'small'} />
                            <Stack spacing={0.5}>
                                <Typography
                                    variant={'body2'}
                                    color={'text.secondary'}
                                    sx={
                                        registrationState === 'REGULAR'
                                            ? {fontWeight: 'fontWeightMedium'}
                                            : undefined
                                    }>
                                    {t('event.registrationAvailable.timespan') +
                                        ': ' +
                                        registrationPeriod}
                                </Typography>
                                {lateRegistrationPeriod && (
                                    <Typography
                                        variant={'body2'}
                                        color={'text.secondary'}
                                        sx={
                                            registrationState === 'LATE'
                                                ? {fontWeight: 'fontWeightMedium'}
                                                : undefined
                                        }>
                                        {t('event.registrationAvailable.lateTimespan') +
                                            ': ' +
                                            lateRegistrationPeriod}
                                    </Typography>
                                )}
                            </Stack>
                        </Stack>
                    }
                />
                {!hideRegistration && registrationState !== 'CLOSED' && (
                    <Box sx={{alignSelf: 'center'}}>
                        <Link to={'/event/$eventId/register'} params={{eventId: event.id}}>
                            <Button endIcon={<Forward />} variant={'contained'}>
                                {t('event.registerNow')}
                            </Button>
                        </Link>
                    </Box>
                )}
            </Box>
        </ListItem>
    )
}

export default UpcomingEventEntry
