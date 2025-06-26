import {BaseEntityDialogProps} from '@utils/types.ts'
import {WorkTypeDto, WorkTypeUpsertDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {addWorkType, updateWorkType} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {FormInputColor} from '@components/form/input/FormInputColor.tsx'

type WorkTypeForm = {
    name: string
    description?: string
    color?: string
    minUser: number
    maxUser?: number
}

const WorkTypeDialog = (props: BaseEntityDialogProps<WorkTypeDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: WorkTypeForm) => {
        return addWorkType({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: WorkTypeForm, entity: WorkTypeDto) => {
        return updateWorkType({
            path: {workTypeId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: WorkTypeForm = {
        name: '',
        description: '',
        minUser: 0,
    }

    const formContext = useForm<WorkTypeForm>()

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
                <FormInputText name="name" label={t('event.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
                <FormInputColor name="color" label={t('entity.color')} />
                <FormInputNumber name="minUser" label={t('work.shift.minUser')} />
                <FormInputNumber name="maxUser" label={t('work.shift.maxUser')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: WorkTypeForm): WorkTypeUpsertDto {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        minUser: formData.minUser,
        maxUser: formData.maxUser,
        color: formData.color,
    }
}

function mapDtoToForm(dto: WorkTypeDto): WorkTypeForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        minUser: dto.minUser,
        maxUser: dto.maxUser,
        color: dto.color,
    }
}

export default WorkTypeDialog
