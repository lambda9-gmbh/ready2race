import {Stack, styled, Tooltip, tooltipClasses, TooltipProps, Typography} from '@mui/material'
import {Info} from '@mui/icons-material'
import {EventRegistrationCompetitionDto} from '../../api'

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

    return (
        <HtmlTooltip
            title={
                <Stack>
                    <Typography variant={'h6'} mb={1}>
                        {props.competition.identifier} {props.competition.name} (
                        {props.competition.shortName})
                    </Typography>
                    <Typography variant={'subtitle2'}>{props.competition.description}</Typography>
                    {
                        props.competition.fees?.filter(f => f.required)?.map((fee, index) =>
                            <Stack direction={'row'} justifyContent={'space-between'} key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography fontWeight={600}>{fee.amount}€</Typography>
                            </Stack>,
                        )
                    }
                    {
                        props.competition.fees?.filter(f => !f.required)?.map((fee, index) =>
                            <Stack direction={'row'} justifyContent={'space-between'} key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography fontWeight={600}>{fee.amount}€</Typography>
                            </Stack>,
                        )
                    }

                </Stack>
            }>
            <Info color={'info'} fontSize={'small'} />
        </HtmlTooltip>
    )
}
