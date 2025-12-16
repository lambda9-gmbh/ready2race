import {ClubDto, getCreateClubOnRegistrationAllowed, updateGlobalConfigurations} from '../../api'
import {Box, Card, CardContent, CardHeader, Stack} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import ClubTable from '@components/club/ClubTable.tsx'
import ClubDialog from '@components/club/ClubDialog.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateAdministrationConfigGlobal} from '@authorization/privileges.ts'

type GlobalConfigForm = {
    allowClubCreationOnRegistration: boolean
}

const ClubsPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
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

    const onSubmit = async (data: GlobalConfigForm) => {
        setSubmitting(true)
        const {error} = await updateGlobalConfigurations({
            body: {
                allowClubCreationOnRegistration: data.allowClubCreationOnRegistration,
            },
        })
        setSubmitting(false)
        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('club.settings.saved'))
        }
    }

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
