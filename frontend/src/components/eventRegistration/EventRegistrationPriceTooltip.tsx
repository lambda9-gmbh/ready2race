import {Divider, Stack, styled, Tooltip, tooltipClasses, TooltipProps, Typography} from '@mui/material'
import {EventRegistrationCompetitionDto} from '../../api'
import {useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'

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

export const EventRegistrationPriceTooltip = (props: {competition: EventRegistrationCompetitionDto}) => {

    const {t} = useTranslation()

    return (
        <HtmlTooltip
            title={
                <Stack p={1}>
                    <Typography variant={'h6'} mb={1}>
                        {props.competition.identifier} {props.competition.name}
                    </Typography>
                    <Typography variant={'subtitle2'}>{props.competition.description}</Typography>
                    <Typography>{t('event.competition.fee.fees')}</Typography>
                    {
                        props.competition.fees?.filter(f => f.required)?.map((fee, index) =>
                            <Stack direction={'row'} justifyContent={'space-between'} spacing={2}
                                   key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography>{Number(fee.amount).toFixed(2)}€</Typography>
                            </Stack>,
                        )
                    }
                    <Divider />
                    <Typography mt={1}>{t('event.registration.optionalFee')}</Typography>
                    {
                        props.competition.fees?.filter(f => !f.required)?.map((fee, index) =>
                            <Stack direction={'row'} justifyContent={'space-between'} spacing={2}
                                   key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography>{Number(fee.amount).toFixed(2)}€</Typography>
                            </Stack>,
                        )
                    }

                </Stack>
            }>
            <Info color={'info'} fontSize={'small'} />
        </HtmlTooltip>
    )
}
