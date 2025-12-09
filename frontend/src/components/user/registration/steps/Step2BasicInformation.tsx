import {Alert, Stack, useMediaQuery, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFormContext} from 'react-hook-form-mui'
import {useMemo} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import {NewPassword} from '@components/form/NewPassword.tsx'
import {AutocompleteClub} from '@components/club/AutocompleteClub.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {RegistrationForm} from '@components/user/registration/common.ts'
import {EventPublicDto} from '@api/types.gen.ts'

interface Step2BasicInformationProps {
    createClubOnRegistrationAllowed: boolean | null
    selectedEvent: EventPublicDto | undefined
}

export const Step2BasicInformation = ({
    createClubOnRegistrationAllowed,
    selectedEvent,
}: Step2BasicInformationProps) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
    const formContext = useFormContext<RegistrationForm>()

    const watchIsParticipant = formContext.watch('isParticipant')
    const watchIsChallengeManager = formContext.watch('isChallengeManager')

    const currentYear = useMemo(() => new Date().getFullYear(), [])

    return (
        <Stack spacing={3}>
            <AutocompleteClub
                name="clubname"
                label={t('club.club')}
                required
                freeSolo={createClubOnRegistrationAllowed === true && watchIsChallengeManager}
            />

            <FormInputText name="firstname" label={t('user.firstname')} required />

            <FormInputText name="lastname" label={t('user.lastname')} required />

            {watchIsChallengeManager ? (
                <FormInputEmail name="emailRequired" label={t('user.email.email')} required />
            ) : (
                <>
                    <FormInputEmail name="emailOptional" label={t('user.email.email')} />
                    {selectedEvent && selectedEvent.allowSelfSubmission && (
                        <Alert severity={'info'}>
                            {t('user.registration.step.emailToSubmitResults')}
                        </Alert>
                    )}
                </>
            )}

            {watchIsChallengeManager && (
                <NewPassword formContext={formContext} horizontal={!isMobile} />
            )}

            {watchIsParticipant && (
                <>
                    <FormInputRadioButtonGroup
                        name="gender"
                        label={t('entity.gender')}
                        required
                        row
                        options={[
                            {label: 'M', id: 'M'},
                            {label: 'F', id: 'F'},
                            {label: 'D', id: 'D'},
                        ]}
                    />

                    <FormInputNumber
                        required
                        name={'birthYear'}
                        label={t('user.birthYear')}
                        integer
                        min={currentYear - 120}
                        max={currentYear}
                    />
                </>
            )}
        </Stack>
    )
}
