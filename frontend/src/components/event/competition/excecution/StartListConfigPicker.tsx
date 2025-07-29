import {Button, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import {FormContainer, useForm} from "react-hook-form-mui";
import BaseDialog from "@components/BaseDialog.tsx";
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import {useEffect, useState} from "react";
import FormInputAutocomplete from "@components/form/input/FormInputAutocomplete.tsx";
import {AutocompleteOption} from "@utils/types.ts";
import {useFetch} from "@utils/hooks.ts";
import {getStartListConfigs} from "@api/sdk.gen.ts";
import {Trans, useTranslation} from "react-i18next";

type Props = {
    open: boolean
    onSuccess: (config: string) => Promise<void>
    onClose: () => void
}

type Form = {
    config: AutocompleteOption
}

const defaultValues: Form = {
    config: null
}

const StartListConfigPicker = ({open, onSuccess, onClose}: Props) => {
    const {t} = useTranslation()
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

    useEffect(() => {
        if (open) {
            formContext.reset(defaultValues)
        }
    }, [open])

    return (
        <BaseDialog
            open={open}
            onClose={onClose}
            maxWidth={'sm'}
        >
            <DialogTitle>
                <Trans i18nKey={'event.competition.execution.startList.dialog.title'} />
            </DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    await onSuccess(data.config!.id)
                    setSubmitting(false)
                    onClose()
                }}
            >
                <DialogContent>
                    <FormInputAutocomplete
                        name={'config'}
                        options={configs}
                        label={t('event.competition.execution.startList.dialog.config')}
                        loading={pending}
                        required
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={submitting}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <SubmitButton submitting={submitting}>
                        <Trans i18nKey={'event.competition.execution.startList.download'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default StartListConfigPicker