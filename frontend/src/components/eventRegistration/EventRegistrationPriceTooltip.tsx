import {Divider, Stack, Typography} from '@mui/material'
import {EventRegistrationCompetitionDto} from '../../api'
import {useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'

export const EventRegistrationPriceTooltip = (props: {
    competition: EventRegistrationCompetitionDto
}) => {
    const {t} = useTranslation()

    return (
        <HtmlTooltip
            title={
                <Stack p={1}>
                    <Typography variant={'h6'} mb={1}>
                        {props.competition.identifier} {props.competition.name}
                    </Typography>
                    <Typography variant={'subtitle2'}>{props.competition.description}</Typography>
                    <Typography fontWeight={500}>{t('event.competition.fee.fees')}</Typography>
                    {props.competition.fees
                        ?.filter(f => f.required)
                        ?.map((fee, index) => (
                            <Stack
                                direction={'row'}
                                justifyContent={'space-between'}
                                spacing={2}
                                key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography>{Number(fee.amount).toFixed(2)}€</Typography>
                            </Stack>
                        ))}
                    <Divider />
                    <Typography fontWeight={500} mt={1}>
                        {t('event.registration.optionalFee')}
                    </Typography>
                    {props.competition.fees
                        ?.filter(f => !f.required)
                        ?.map((fee, index) => (
                            <Stack
                                direction={'row'}
                                justifyContent={'space-between'}
                                spacing={2}
                                key={`${fee}-${index}`}>
                                <Typography>{fee.label}:</Typography>
                                <Typography>{Number(fee.amount).toFixed(2)}€</Typography>
                            </Stack>
                        ))}
                </Stack>
            }>
            <Info color={'info'} fontSize={'small'} />
        </HtmlTooltip>
    )
}
