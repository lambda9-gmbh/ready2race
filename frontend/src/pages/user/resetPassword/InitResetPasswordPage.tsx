import {FormContainer} from "react-hook-form-mui";
import {Stack} from "@mui/material";
import {FormInputText} from "../../../components/form/input/FormInputText.tsx";

type Form = {
    email: string,
    path: string
}

const InitResetPasswordPage = () => {
    return (
        <FormContainer>
            <Stack>
                <FormInputText name={'email'} label={'TODO'}/>

            </Stack>
        </FormContainer>
    )
}

export default InitResetPasswordPage