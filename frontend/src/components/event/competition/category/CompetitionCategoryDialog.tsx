import {BaseEntityDialogProps} from '@utils/types.ts'
import {CompetitionCategoryDto, CompetitionCategoryRequest} from '@api/types.gen.ts'
import {addCompetitionCategory, updateCompetitionCategory} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'

type CompetitionCategoryForm = {
    name: string
    description: string
}

const CompetitionCategoryDialog = (props: BaseEntityDialogProps<CompetitionCategoryDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: CompetitionCategoryForm) => {
        return addCompetitionCategory({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: CompetitionCategoryForm, entity: CompetitionCategoryDto) => {
        return updateCompetitionCategory({
            path: {competitionCategoryId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: CompetitionCategoryForm = {
        name: '',
        description: '',
    }

    const formContext = useForm<CompetitionCategoryForm>()

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
                <FormInputText name="name" label={t('entity.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: CompetitionCategoryForm): CompetitionCategoryRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
    }
}

function mapDtoToForm(dto: CompetitionCategoryDto): CompetitionCategoryForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
    }
}

export default CompetitionCategoryDialog
