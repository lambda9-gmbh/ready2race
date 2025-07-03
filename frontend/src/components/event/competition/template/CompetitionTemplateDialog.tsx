import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {CompetitionPropertiesFormInputs} from '../CompetitionPropertiesFormInputs.tsx'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {
    mapCompetitionFormToCompetitionPropertiesRequest,
    mapCompetitionPropertiesToCompetitionForm,
    CompetitionForm,
    competitionFormDefaultValues,
} from '../common.ts'
import {useCallback} from 'react'
import {addCompetitionTemplate, updateCompetitionTemplate} from "@api/sdk.gen.ts";
import {CompetitionTemplateDto} from "@api/types.gen.ts";

const CompetitionTemplateDialog = (props: BaseEntityDialogProps<CompetitionTemplateDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: CompetitionForm) => {
        return addCompetitionTemplate({
            body: {properties: mapCompetitionFormToCompetitionPropertiesRequest(formData)},
        })
    }

    const editAction = (formData: CompetitionForm, entity: CompetitionTemplateDto) => {
        return updateCompetitionTemplate({
            path: {competitionTemplateId: entity.id},
            body: {properties: mapCompetitionFormToCompetitionPropertiesRequest(formData)},
        })
    }


    const formContext = useForm<CompetitionForm>()

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? mapCompetitionPropertiesToCompetitionForm(props.entity.properties, t('decimal.point'), props.entity.setupTemplate)
                : competitionFormDefaultValues,
        )
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <CompetitionPropertiesFormInputs formContext={formContext} />
            </Stack>
        </EntityDialog>
    )
}

export default CompetitionTemplateDialog
