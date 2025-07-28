import {Button, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import {FormContainer, useForm} from "react-hook-form-mui";
import BaseDialog from "@components/BaseDialog.tsx";
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import {useState} from "react";
import FormInputAutocomplete from "@components/form/input/FormInputAutocomplete.tsx";
import {AutocompleteOption} from "@utils/types.ts";
import {useFetch} from "@utils/hooks.ts";
import {getStartListConfigs} from "@api/sdk.gen.ts";

type Props = {
    open: boolean
    onSuccess: (config: string) => Promise<void>
    onClose: () => void
}

type Form = {
    config: string
}

const StartListConfigPicker = ({open, onSuccess, onClose}: Props) => {

    const formContext = useForm<Form>()
    const [submitting, setSubmitting] = useState(false)

    const {data, pending} = useFetch(
        signal => getStartListConfigs({signal}),
    )

    const configs: AutocompleteOption[] =
        data?.data.map(dto => ({
            id: dto.id,
            label: dto.name
        })) ?? []

    return (
        <BaseDialog
            open={open}
            onClose={onClose}
            maxWidth={'sm'}
        >
            <DialogTitle>[todo] Konfiguration wählen</DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    await onSuccess(data.config)
                    setSubmitting(false)
                    onClose()
                }}
            >
                <DialogContent>
                    <FormInputAutocomplete
                        name={'config'}
                        options={configs}
                        label={'[todo] Konfiguration'}
                        loading={pending}
                        required
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={submitting}>
                        [todo] Abbrechen
                    </Button>
                    <SubmitButton submitting={submitting}>
                        [todo] Laden
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default StartListConfigPicker