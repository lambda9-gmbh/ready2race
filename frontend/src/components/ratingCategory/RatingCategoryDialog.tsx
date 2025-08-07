import {BaseEntityDialogProps} from "@utils/types.ts";
import {RatingCategoryDto, RatingCategoryRequest} from "@api/types.gen.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {addRatingCategory, updateRatingCategory} from "@api/sdk.gen.ts";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import { Stack } from "@mui/material";
import {useTranslation} from "react-i18next";
import {takeIfNotEmpty} from "@utils/ApiUtils.ts";

type Form = {
    name: string
    description: string
}

const defaultValues: Form = {
    name: '',
    description: '',
}

const addAction = (formData: Form) =>
    addRatingCategory({
        body: mapFormToRequest(formData)
    })

const editAction = (formData: Form, entity: RatingCategoryDto) =>
    updateRatingCategory({
        path: {ratingCategoryId: entity.id},
        body: mapFormToRequest(formData)
    })

const RatingCategoryDialog = (props: BaseEntityDialogProps<RatingCategoryDto>) => {

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
                <FormInputText name={'name'} label={t('configuration.ratingCategory.name')} required />
                <FormInputText name={'description'} label={t('configuration.ratingCategory.description')} />
            </Stack>
        </EntityDialog>
    )
}

const mapFormToRequest = (formData: Form): RatingCategoryRequest => ({
    name: formData.name,
    description: takeIfNotEmpty(formData.description)
})

const mapDtoToForm = (dto: RatingCategoryDto): Form => ({
    name: dto.name,
    description: dto.description ?? ''
})

export default RatingCategoryDialog