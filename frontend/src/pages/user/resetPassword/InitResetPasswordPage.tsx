import {FormContainer, useForm} from "react-hook-form-mui";
import {Stack} from "@mui/material";
import {FormInputText} from "../../../components/form/input/FormInputText.tsx";
import {useTranslation} from "react-i18next";
import {useState} from "react";
import {userLogin} from "../../../api";

type Form = {
    email: string,
    path: string
}

const InitResetPasswordPage = () => {
    const {t} = useTranslation()

/*    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {data, error} = await initPasswordReset({
            body: formData,
        })

        setSubmitting(false)
        if (data !== undefined) {
            login(data)
        } else if (error) {
            if (error.status.value === 500) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.error(t('user.login.error'))
            }
        }
    }*/

    return (
        <FormContainer>
            <Stack>
                <FormInputText name={'email'} label={'TODO'}/>

            </Stack>
        </FormContainer>
    )
}

export default InitResetPasswordPage