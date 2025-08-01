import {EventRegistrationNamedParticipantDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {Stack, Typography} from '@mui/material'
import {Group} from '@mui/icons-material'
import {grey} from '@mui/material/colors'

export const TeamNamedParticipantLabel = (props: {
    namedParticipant: EventRegistrationNamedParticipantDto
}) => {
    const {t} = useTranslation()

    return (
        <Stack direction={'row'} alignItems={'center'} spacing={2}>
            <Typography>{props.namedParticipant.name}</Typography>
            <Stack direction={'row'} spacing={1}>
                <Group sx={{color: grey['500']}} />
                <Typography color={grey['500']}>
                    {props.namedParticipant.countMales +
                        props.namedParticipant.countFemales +
                        props.namedParticipant.countMixed +
                        props.namedParticipant.countNonBinary}
                </Typography>
            </Stack>
            <Stack direction={'row'} alignItems={'center'} spacing={1}>
                <Typography color={grey['500']}>(</Typography>
                {props.namedParticipant.countFemales > 0 && (
                    <Typography color={grey['500']}>
                        {' '}
                        {props.namedParticipant.countFemales}x {t('event.registration.females')}
                    </Typography>
                )}
                {props.namedParticipant.countMales > 0 && (
                    <Typography color={grey['500']}>
                        {' '}
                        {props.namedParticipant.countMales}x {t('event.registration.males')}
                    </Typography>
                )}
                {props.namedParticipant.countNonBinary > 0 && (
                    <Typography color={grey['500']}>
                        {' '}
                        {props.namedParticipant.countNonBinary}x {t('event.registration.nonBinary')}
                    </Typography>
                )}
                {props.namedParticipant.countMixed > 0 && (
                    <Typography color={grey['500']}>
                        {' '}
                        {props.namedParticipant.countMixed}x {t('event.registration.mixed')}
                    </Typography>
                )}
                <Typography color={grey['500']}>)</Typography>
            </Stack>
        </Stack>
    )
}
