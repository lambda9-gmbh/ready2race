import {BaseEntityDialogProps} from '../../../../utils/types.ts'
import {
    addRaceCategory,
    RaceCategoryDto,
    RaceCategoryRequest,
    updateRaceCategory,
} from '../../../../api'
import {useTranslation} from 'react-i18next'
import {takeIfNotEmpty} from '../../../../utils/ApiUtils.ts'
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import EntityDialog from "../../../EntityDialog.tsx";
import {Stack} from "@mui/material";
import {FormInputText} from "../../../form/input/FormInputText.tsx";

type RaceCategoryForm = {
    name: string
    description: string
}

const RaceCategoryDialog = (props: BaseEntityDialogProps<RaceCategoryDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: RaceCategoryForm) => {
        return addRaceCategory({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: RaceCategoryForm, entity: RaceCategoryDto) => {
        return updateRaceCategory({
            path: {raceCategoryId: entity.id},
            body: mapFormToRequest(formData),
        })
    }


    const defaultValues: RaceCategoryForm = {
        name: '',
        description: '',
    }

    const formContext = useForm<RaceCategoryForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])


    const entityKeyName = {entity: t('event.race.category.category')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            title={action =>
                action === 'add' ? t('entity.add.action', entityKeyName) : t('entity.edit.action')
            }
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => {}}>
            <Stack spacing={2}>
                <FormInputText name="name" label={t('entity.name')} required/>
                <FormInputText name="description" label={t('entity.description')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: RaceCategoryForm): RaceCategoryRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
    }
}

function mapDtoToForm(dto: RaceCategoryDto): RaceCategoryForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
    }
}

export default RaceCategoryDialog
