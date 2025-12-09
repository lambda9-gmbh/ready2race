import {ClubDto, getCreateClubOnRegistrationAllowed, updateGlobalConfigurations} from '../../api'
import {Box, Card, CardContent, CardHeader, Stack} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import ClubTable from '../../components/club/ClubTable.tsx'
import ClubDialog from '../../components/club/ClubDialog.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import FormInputSwitch from '../../components/form/input/FormInputSwitch.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'
import {useCallback, useState} from 'react'
import {useSnackbar} from 'notistack'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateAdministrationConfigGlobal} from '@authorization/privileges.ts'

type GlobalConfigForm = {
    allowClubCreationOnRegistration: boolean
}

const ClubsPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {enqueueSnackbar} = useSnackbar()
    const [submitting, setSubmitting] = useState(false)
    const user = useUser()

    const administrationProps = useEntityAdministration<ClubDto>(t('club.club'), {
        entityCreate: false,
    })

    const formContext = useForm<GlobalConfigForm>({
        defaultValues: {
            allowClubCreationOnRegistration: false,
        },
    })

    useFetch(signal => getCreateClubOnRegistrationAllowed({signal}), {
        onResponse: ({error, data}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset({
                    allowClubCreationOnRegistration: data,
                })
            }
        },
        deps: [],
    })

    const onSubmit = useCallback(
        (data: GlobalConfigForm) => {
            setSubmitting(true)
            updateGlobalConfigurations({
                body: {
                    allowClubCreationOnRegistration: data.allowClubCreationOnRegistration,
                },
            })
                .then(() => {
                    enqueueSnackbar(t('club.settings.saved'), {variant: 'success'})
                })
                .catch(error => {
                    console.error('Failed to update global configurations', error)
                    enqueueSnackbar(t('common.error.unexpected'), {variant: 'error'})
                })
                .finally(() => {
                    setSubmitting(false)
                })
        },
        [t, enqueueSnackbar],
    )

    return (
        <Box>
            {user.checkPrivilege(updateAdministrationConfigGlobal) && (
                <Card sx={{mb: 3}}>
                    <CardHeader title={t('club.settings.title')} />
                    <CardContent>
                        <FormContainer formContext={formContext} onSuccess={onSubmit}>
                            <Stack spacing={2}>
                                <FormInputSwitch
                                    name="allowClubCreationOnRegistration"
                                    label={t('club.settings.allowClubCreationOnRegistration')}
                                    reverse
                                    horizontal
                                />
                                <Box>
                                    <SubmitButton submitting={submitting}>
                                        {t('club.settings.save')}
                                    </SubmitButton>
                                </Box>
                            </Stack>
                        </FormContainer>
                    </CardContent>
                </Card>
            )}
            <ClubTable {...administrationProps.table} title={t('club.clubs')} />
            <ClubDialog {...administrationProps.dialog} />
        </Box>
    )
}

export default ClubsPage
