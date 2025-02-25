import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {RacePropertiesFormInputs} from '../RacePropertiesFormInputs.tsx'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {
    mapRaceFormToRacePropertiesRequest,
    mapRacePropertiesToRaceForm,
    RaceForm,
    raceFormDefaultValues,
} from '../common.ts'
import {useCallback} from 'react'
import {addRaceTemplate, updateRaceTemplate} from "@api/sdk.gen.ts";
import {RaceTemplateDto} from "@api/types.gen.ts";

const RaceTemplateDialog = (props: BaseEntityDialogProps<RaceTemplateDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: RaceForm) => {
        return addRaceTemplate({
            body: {properties: mapRaceFormToRacePropertiesRequest(formData)},
        })
    }

    const editAction = (formData: RaceForm, entity: RaceTemplateDto) => {
        return updateRaceTemplate({
            path: {raceTemplateId: entity.id},
            body: {properties: mapRaceFormToRacePropertiesRequest(formData)},
        })
    }


    const formContext = useForm<RaceForm>()

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? mapRacePropertiesToRaceForm(props.entity.properties, t('decimal.point'))
                : raceFormDefaultValues,
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
                <RacePropertiesFormInputs formContext={formContext} />
            </Stack>
        </EntityDialog>
    )
}

export default RaceTemplateDialog
