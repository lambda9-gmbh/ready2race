import {Alert, Box, Chip, Divider, Stack, Typography} from '@mui/material'
import {EventRegistrationCompetitionDto} from '../../api'
import {Trans, useTranslation} from 'react-i18next'
import {Euro} from '@mui/icons-material'

export const EventRegistrationCompetitionFeeCard = (props: {
    competition: EventRegistrationCompetitionDto
}) => {
    const {t} = useTranslation()

    const requiredFees = props.competition.fees?.filter(f => f.required) ?? []
    const optionalFees = props.competition.fees?.filter(f => !f.required) ?? []

    return (
        <Alert
            severity="info"
            icon={<Euro />}
            sx={{
                mb: 2,
                '& .MuiAlert-message': {
                    width: '100%',
                },
            }}>
            <Stack spacing={1.5}>
                {/* Competition Header */}
                <Stack
                    direction={{xs: 'column', sm: 'row'}}
                    spacing={1}
                    alignItems={{xs: 'flex-start', sm: 'center'}}
                    flexWrap={'wrap'}>
                    <Typography variant="h6">
                        {props.competition.identifier} - {props.competition.name}
                    </Typography>
                    {props.competition.competitionCategory && (
                        <Chip
                            variant="outlined"
                            label={props.competition.competitionCategory}
                            size="small"
                        />
                    )}
                </Stack>

                {/* Description */}
                {props.competition.description && (
                    <Typography variant="body2" color="text.secondary">
                        {props.competition.description}
                    </Typography>
                )}

                <Divider />

                {/* Required Fees */}
                <Box>
                    <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                        {t('event.competition.fee.fees')}
                    </Typography>
                    {requiredFees.length > 0 ? (
                        <Stack spacing={0.5}>
                            {requiredFees.map(fee => (
                                <Stack
                                    key={fee.id}
                                    direction={{xs: 'column', sm: 'row'}}
                                    spacing={{xs: 0, sm: 1}}
                                    alignItems={{xs: 'flex-start', sm: 'center'}}>
                                    <Typography variant="body2">
                                        {fee.label}:{' '}
                                        <strong>{Number(fee.amount).toFixed(2)}€</strong>
                                    </Typography>
                                    {props.competition.lateRegistrationAllowed &&
                                        fee.lateAmount && (
                                            <Typography variant="caption">
                                                (
                                                <Trans
                                                    i18nKey={'event.competition.fee.asLate'}
                                                    values={{
                                                        amount: Number(fee.lateAmount).toFixed(2),
                                                    }}
                                                />
                                                )
                                            </Typography>
                                        )}
                                </Stack>
                            ))}
                        </Stack>
                    ) : (
                        <Typography variant="body2" color="text.secondary">
                            -
                        </Typography>
                    )}
                </Box>

                {/* Optional Fees */}
                {optionalFees.length > 0 && (
                    <>
                        <Divider />
                        <Box>
                            <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                                {t('event.registration.optionalFee')}
                            </Typography>
                            <Stack spacing={0.5}>
                                {optionalFees.map(fee => (
                                    <Stack
                                        key={fee.id}
                                        direction={{xs: 'column', sm: 'row'}}
                                        spacing={{xs: 0, sm: 1}}
                                        alignItems={{xs: 'flex-start', sm: 'center'}}>
                                        <Typography variant="body2">
                                            {fee.label}:{' '}
                                            <strong>{Number(fee.amount).toFixed(2)}€</strong>
                                        </Typography>
                                        {props.competition.lateRegistrationAllowed &&
                                            fee.lateAmount && (
                                                <Typography variant="caption">
                                                    (
                                                    <Trans
                                                        i18nKey={'event.competition.fee.asLate'}
                                                        values={{
                                                            amount: Number(fee.lateAmount).toFixed(
                                                                2,
                                                            ),
                                                        }}
                                                    />
                                                    )
                                                </Typography>
                                            )}
                                    </Stack>
                                ))}
                            </Stack>
                        </Box>
                    </>
                )}
            </Stack>
        </Alert>
    )
}
