import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getRoles, getUser} from '@api/sdk.gen.ts'
import {userRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import {Box, Paper, Stack, Typography} from '@mui/material'
import {FormContainer, MultiSelectElement, useForm} from 'react-hook-form-mui'
import {useState} from 'react'
import Throbber from '@components/Throbber.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import {useUser} from "@contexts/user/UserContext.ts";
import {updateUserGlobal} from "@authorization/privileges.ts";

type Form = {
    firstname: string
    lastname: string
    roles: string[]
}

const UserPage = () => {

    // TODO: Should this page be available for the admin? Currently there is no view for him so the page can't load

    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const [submitting, setSubmitting] = useState(false)

    const {userId} = userRoute.useParams()

    const defaultValues: Form = {
        firstname: '',
        lastname: '',
        roles: [],
    }

    const formContext = useForm<Form>({values: defaultValues})

    const {data: userData, pending} = useFetch(signal => getUser({signal, path: {userId: userId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('user.page.error.load'))
            } else {
                formContext.reset({firstname: data.firstname, lastname: data.lastname, roles: data.roles.map(r => r.id)})
            }
        },
        deps: [userId],
    })

    const handleSubmit = async () => {
        setSubmitting(true)
        // TODO: updateUser()
        setSubmitting(false)
    }

    const {data} = useFetch(
        signal => getRoles({signal}),
        {
            preCondition: () => user.checkPrivilege(updateUserGlobal)
        }
    )

    return (
        <Box sx={{maxWidth: 600}}>
            <Paper sx={{padding: 4}}>
                {(userData && (
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <FormInputText
                                name={'firstname'}
                                label={t('user.firstname')}
                                required
                            />
                            <FormInputText name={'lastname'} label={t('user.lastname')} required />
                            <Typography>{userData.email}</Typography>
                            {user.checkPrivilege(updateUserGlobal) ? (
                                <MultiSelectElement
                                    name={'roles'}
                                    label={'Rollen'}
                                    options={
                                        data?.data.sort((a, b) => {
                                            if (a.name < b.name) {
                                                return 1
                                            } else if (a.name > b.name) {
                                                return -1
                                            } else {
                                                return 0
                                            }
                                        }).map(r => ({
                                            id: r.id,
                                            label: r.name
                                        })) ?? []
                                    }
                                    itemKey={'id'}
                                    itemValue={'id'}
                                    itemLabel={'label'}
                                    showCheckbox={true}
                                    showChips={true}
                                />
                                ) : (
                                    <Box>
                                        <Typography>Roles:</Typography>
                                        <Typography>
                                            {userData.roles.map(r => r.name).join(', ')}
                                        </Typography>
                                    </Box>
                            )

                            }
                            {/* TODO: Edit Password */}
                            <SubmitButton label={t('common.save')} submitting={submitting} disabled={!formContext.formState.isDirty}/>
                        </Stack>
                    </FormContainer>
                )) ||
                    (pending && <Throbber />)}
            </Paper>
        </Box>
    )
}

export default UserPage
