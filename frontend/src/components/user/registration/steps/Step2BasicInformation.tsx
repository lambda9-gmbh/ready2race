import {Alert, Stack, useMediaQuery, useTheme} from '@mui/material'
import {Info} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFormContext} from 'react-hook-form-mui'
import {useMemo} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import {NewPassword} from '@components/form/NewPassword.tsx'
import {AutocompleteClub} from '@components/club/AutocompleteClub.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {RegistrationForm} from '@components/user/registration/common.ts'

interface Step2BasicInformationProps {
    createClubOnRegistrationAllowed: boolean | null
}

export const Step2BasicInformation = ({createClubOnRegistrationAllowed}: Step2BasicInformationProps) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
    const formContext = useFormContext<RegistrationForm>()

    const registrationType = formContext.watch('registrationType')
    const isClub = registrationType === 'CLUB'
    const isParticipantOnly = registrationType === 'PARTICIPANT'
    const clubname = formContext.watch('clubname')
    const clubId = formContext.watch('clubId')
    const participateSelf = formContext.watch('participateSelf')

    const canCreateClub = createClubOnRegistrationAllowed === true && isClub

    // Participant data (gender/birth year) is only collected when the registrant participates:
    // always for the participant flow, and for the club flow only when they opt in.
    const showParticipantFields = isParticipantOnly || (isClub && participateSelf)

    const currentYear = useMemo(() => new Date().getFullYear(), [])

    return (
        <Stack spacing={3}>
            <Stack spacing={1}>
                <AutocompleteClub
                    name="clubname"
                    label={t('club.club')}
                    required
                    freeSolo={canCreateClub}
                    allowCreate={canCreateClub}
                />
                {clubId ? (
                    // A club admin registering against an existing club only creates a pending
                    // join request that a club administrator (a current representative, or a
                    // platform admin if the club has none yet) must approve. Participants are
                    // simply filed under the club, so they get the plain confirmation.
                    isClub ? (
                        <Alert severity="info" icon={<Info />} sx={{py: 0}}>
                            {t('club.create.joinRequest', {name: clubname?.trim() ?? ''})}
                        </Alert>
                    ) : (
                        <Alert severity="success" icon={<Info />} sx={{py: 0}}>
                            {t('club.create.existingSelected')}
                        </Alert>
                    )
                ) : (
                    canCreateClub &&
                    clubname?.trim() && (
                        <Alert severity="info" icon={<Info />} sx={{py: 0}}>
                            {t('club.create.willCreate', {name: clubname.trim()})}
                        </Alert>
                    )
                )}
            </Stack>

            <FormInputText name="firstname" label={t('user.firstname')} required />

            <FormInputText name="lastname" label={t('user.lastname')} required />

            {isClub ? (
                <FormInputEmail name="emailRequired" label={t('user.email.email')} required />
            ) : (
                <FormInputEmail name="emailOptional" label={t('user.email.email')} />
            )}

            {isClub && <NewPassword formContext={formContext} horizontal={!isMobile} />}

            {isClub && (
                <FormInputCheckbox
                    name="participateSelf"
                    label={t('user.registration.participateSelf')}
                    horizontal
                    reverse
                />
            )}

            {/* gender and birth year are only required when the registrant participates */}
            {showParticipantFields && (
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
