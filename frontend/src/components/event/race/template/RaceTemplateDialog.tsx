import {BaseEntityDialogProps} from '../../../../utils/types.ts'
import {addRaceTemplate, RaceTemplateDto, updateRaceTemplate} from '../../../../api'
import {useTranslation} from 'react-i18next'
import {RacePropertiesFormInputs} from '../RacePropertiesFormInputs.tsx'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '../../../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {
    mapRaceFormToRacePropertiesRequest,
    mapRacePropertiesToRaceForm,
    RaceForm,
    raceFormDefaultValues,
} from '../common.ts'
import {useCallback} from 'react'

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

    const entityNameKey = {entity: t('event.race.template.template')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            title={action =>
                action === 'add'
                    ? t('entity.add.action', entityNameKey)
                    : t('entity.edit.action', entityNameKey)
            } // could be shortened but then the translation key can not be found by intellij-search
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={2}>
                <RacePropertiesFormInputs formContext={formContext} />
            </Stack>
        </EntityDialog>
    )
}

export default RaceTemplateDialog
