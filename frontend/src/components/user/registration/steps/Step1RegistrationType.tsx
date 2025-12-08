import {Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {EventPublicDto} from '@api/types.gen.ts'

interface Step1RegistrationTypeProps {
    availableEvents?: EventPublicDto[]
}

export const Step1RegistrationType = ({availableEvents}: Step1RegistrationTypeProps) => {
    const {t} = useTranslation()

    return (
        <Stack spacing={2}>
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
            {availableEvents && availableEvents.length > 0 && (
                <FormInputAutocomplete
                    name="event"
                    label={t('event.event')}
                    required
                    options={availableEvents.map(event => ({
                        id: event.id,
                        label: event.name,
                    }))}
                />
            )}
        </Stack>
    )
}
