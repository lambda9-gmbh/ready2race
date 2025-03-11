import {BaseEntityDialogProps} from "@utils/types.ts";
import {FeeDto, FeeRequest} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import {addFee, updateFee} from "@api/sdk.gen.ts";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import EntityDialog from "@components/EntityDialog.tsx";
import {Stack} from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {takeIfNotEmpty} from "@utils/ApiUtils.ts";

type FeeForm = {
    name: string
    description: string
}

const FeeDialog = (props: BaseEntityDialogProps<FeeDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: FeeForm) => {
        return addFee({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: FeeForm, entity: FeeDto) => {
        return updateFee({
            path: {feeId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: FeeForm = {
        name: '',
        description: '',
    }

    const formContext = useForm<FeeForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])


    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name="name" label={t('event.competition.fee.name')} required />
                <FormInputText name="description" label={t('event.competition.fee.description')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: FeeForm): FeeRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
    }
}

function mapDtoToForm(dto: FeeDto): FeeForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
    }
}

export default FeeDialog
