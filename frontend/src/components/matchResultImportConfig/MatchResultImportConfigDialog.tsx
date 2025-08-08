import {BaseEntityDialogProps} from "@utils/types.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import {Stack} from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {
    addMatchResultImportConfig,
    updateMatchResultImportConfig,
} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {MatchResultImportConfigDto, MatchResultImportConfigRequest} from "@api/types.gen.ts";

type Form = {
    name: string
    colTeamStartNumber: string
    colTeamPlace: string
}

const defaultValues: Form = {
    name: '',
    colTeamStartNumber: '',
    colTeamPlace: '',
}

const addAction = (formData: Form) =>
    addMatchResultImportConfig({
        body: mapFormToRequest(formData)
    })

const editAction = (formData: Form, entity: MatchResultImportConfigDto) =>
    updateMatchResultImportConfig({
        path: {matchResultImportConfigId: entity.id},
        body: mapFormToRequest(formData)
    })

const MatchResultImportConfigDialog = (props: BaseEntityDialogProps<MatchResultImportConfigDto>) => {
    const {t} = useTranslation()
    const formContext = useForm<Form>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}
        >
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('configuration.import.matchResult.name')} required />
                <FormInputText name={'colTeamStartNumber'} label={t('configuration.import.matchResult.col.team.startNumber')} required />
                <FormInputText name={'colTeamPlace'} label={t('configuration.import.matchResult.col.team.place')} required />
            </Stack>
        </EntityDialog>
    )
}

const mapFormToRequest = (formData: Form): MatchResultImportConfigRequest => formData

const mapDtoToForm = (dto: MatchResultImportConfigDto): Form => ({
    name: dto.name,
    colTeamStartNumber: dto.colTeamStartNumber,
    colTeamPlace: dto.colTeamPlace,
})

export default MatchResultImportConfigDialog