import {Divider, List, ListItem, ListItemText, Stack, Typography} from '@mui/material'
import {EventRegistrationCompetitionDto} from '../../api'
import {Trans, useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {useCallback} from 'react'

export const EventRegistrationPriceTooltip = (props: {
    competition: EventRegistrationCompetitionDto
}) => {
    const {t} = useTranslation()

    const getFees = useCallback(
        (requiredFees: boolean) => {
            const fees = props.competition.fees?.filter(f => f.required === requiredFees) ?? []

            if (fees.length > 0) {
                return (
                    <List>
                        {fees.map((fee) => (
                            <ListItem key={fee.id}>
                                <ListItemText
                                    primary={
                                        <>{fee.label}: {Number(fee.amount).toFixed(2)}€</>
                                    }
                                    secondary={
                                        props.competition.lateRegistrationAllowed &&
                                        <Trans i18nKey={'event.competition.fee.asLate'} values={{amount: Number(fee.lateAmount ?? fee.amount).toFixed(2)}} />
                                    }
                                />
                            </ListItem>
                        ))}
                    </List>
                )
            } else {
                return <Typography>-</Typography>
            }
        },
        [props.competition.fees],
    )

    return (
        <HtmlTooltip
            title={
                <Stack p={1}>
                    <Typography variant={'h6'} mb={1}>
                        {props.competition.identifier} - {props.competition.name}
                    </Typography>
                    <Typography variant={'subtitle2'}>{props.competition.description}</Typography>
                    <Typography fontWeight={500}>{t('event.competition.fee.fees')}</Typography>
                    {getFees(true)}
                    <Divider />
                    <Typography fontWeight={500} mt={1}>
                        {t('event.registration.optionalFee')}
                    </Typography>
                    {getFees(false)}
                </Stack>
            }>
            <Info color={'info'} fontSize={'small'} />
        </HtmlTooltip>
    )
}
