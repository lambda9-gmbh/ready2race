import {Box, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'

export const Step1RegistrationType = () => {
    const {t} = useTranslation()

    return (
        <Box>
            <Typography variant="body2" sx={{mb: 2}}>
                {t('user.registration.registrationType')}
            </Typography>
            <Stack
                spacing={2}
                direction={{xs: 'column', sm: 'row'}}
                sx={{justifyContent: 'space-between'}}>
                <FormInputCheckbox
                    name="isChallengeManager"
                    label={t('user.registration.asChallengeManager')}
                    horizontal
                    reverse
                />
                <FormInputCheckbox
                    name="isParticipant"
                    label={t('user.registration.asParticipant')}
                    horizontal
                    reverse
                />
            </Stack>
        </Box>
    )
}