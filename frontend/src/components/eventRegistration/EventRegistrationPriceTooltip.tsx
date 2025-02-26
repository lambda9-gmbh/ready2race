import {Stack, styled, Tooltip, tooltipClasses, TooltipProps, Typography} from '@mui/material'
import {Info} from '@mui/icons-material'
import {EventRegistrationRaceDto} from '../../api'
import {useTranslation} from 'react-i18next'

const HtmlTooltip = styled(({className, ...props}: TooltipProps) => (
    <Tooltip {...props} classes={{popper: className}} />
))(({theme}) => ({
    [`& .${tooltipClasses.tooltip}`]: {
        backgroundColor: '#f5f5f9',
        color: 'rgba(0, 0, 0, 0.87)',
        maxWidth: 220,
        fontSize: theme.typography.pxToRem(12),
        border: '1px solid #dadde9',
    },
}))

export const EventRegistrationPriceTooltip = (props: {race: EventRegistrationRaceDto}) => {
    const {t} = useTranslation()

    return (
        <HtmlTooltip
            title={
                <Stack>
                    <Typography variant={'h6'} mb={1}>
                        {props.race.identifier} {props.race.name} (
                        {props.race.shortName})
                    </Typography>
                    <Typography variant={'subtitle2'}>{props.race.description}</Typography>
                    <Stack direction={'row'} justifyContent={'space-between'}>
                        <Typography>{t('event.race.participationFee')}:</Typography>
                        <Typography fontWeight={600}>{props.race.participationFee}€</Typography>
                    </Stack>
                    <Stack direction={'row'} justifyContent={'space-between'}>
                        <Typography>{t('event.race.rentalFee')}:</Typography>
                        <Typography fontWeight={600}>{props.race.rentalFee}€</Typography>
                    </Stack>
                </Stack>
            }>
            <Info color={'info'} fontSize={'small'} />
        </HtmlTooltip>
    )
}
