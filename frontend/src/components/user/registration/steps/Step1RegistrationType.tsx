import {Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {EventPublicDto} from '@api/types.gen.ts'
import {useFormContext} from 'react-hook-form-mui'
import {RegistrationForm} from '@components/user/registration/common.ts'

interface Step1RegistrationTypeProps {
    availableEvents?: EventPublicDto[]
}

export const Step1RegistrationType = ({availableEvents}: Step1RegistrationTypeProps) => {
    const {t} = useTranslation()

    const formContext = useFormContext<RegistrationForm>()

    const watchIsParticipant = formContext.watch('isParticipant')

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
                    name="isParticipant"
                    label={t('user.registration.asParticipant')}
                    horizontal
                    reverse
                />
                <FormInputCheckbox
                    name="isChallengeManager"
                    label={t('user.registration.asClubRepresentative')}
                    horizontal
                    reverse
                />
            </Stack>
            {availableEvents && availableEvents.length > 0 && watchIsParticipant && (
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
