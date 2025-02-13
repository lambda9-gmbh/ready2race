import {addRace, getRaceTemplates, RaceDto, RaceRequest, updateRace} from '../../../api'
import {AutocompleteOption, BaseEntityDialogProps} from '../../../utils/types.ts'
import EntityDialog from '../../EntityDialog.tsx'
import {Autocomplete, Box, Stack, TextField} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {useForm} from 'react-hook-form-mui'
import {useFeedback, useFetch} from '../../../utils/hooks.ts'
import {useCallback, useEffect, useState} from 'react'
import {
    mapRaceFormToRacePropertiesRequest,
    mapRacePropertiesToRaceForm,
    RaceForm,
    raceFormDefaultValues,
} from './common.ts'
import {RacePropertiesFormInputs} from './RacePropertiesFormInputs.tsx'

const RaceDialog = (props: BaseEntityDialogProps<RaceDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: RaceForm) => {
        return addRace({
            path: {eventId: eventId},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const editAction = (formData: RaceForm, entity: RaceDto) => {
        return updateRace({
            path: {eventId: entity.event, raceId: entity.id},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const {data: templatesData, pending: templatesPending} = useFetch(
        signal => getRaceTemplates({signal}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.template.templates'),
                        }),
                    )
                }
            },
        },
        [], // todo: consider if the templates, raceCategories and namedParticipants are stale data and should be reloaded
    )
    const templates: AutocompleteOption[] =
        templatesData?.data.map(dto => ({
            id: dto.id,
            label: templateLabelName(dto.properties.identifier, dto.properties.name),
        })) ?? []

    const formContext = useForm<RaceForm>()

    const [template, setTemplate] = useState<AutocompleteOption | null>(null)
    const resetTemplate = () => {
        setTemplate(null)
    }

    useEffect(() => {
        // This ignores watch calls that are called when opening the form - This way the template doesn't reset again directly after opening the form
        const subscription = formContext.watch((_, foo) => {
            if (foo.name !== 'namedParticipants' && foo.name !== undefined) {
                resetTemplate()
            }
        })
        return () => subscription.unsubscribe()
    }, [formContext.watch])

    function fillFormWithTemplate(templateId: string) {
        const template = templatesData?.data.find(dto => dto?.id === templateId)
        if (template) {
            formContext.reset(mapRacePropertiesToRaceForm(template.properties, t('decimal.point')))
        }
    }

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? mapRacePropertiesToRaceForm(props.entity.properties, t('decimal.point'))
                : raceFormDefaultValues,
        )
        setTemplate(templates.find(t => t.id === props.entity?.template) ?? null)
    }, [props.entity, templatesData])

    const entityNameKey = {entity: t('event.race.race')}

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
            {
                // render only when all is loaded (templates, categories, namedParticipants)
                // also in RaceTemplates
            }
            <Stack spacing={2}>
                <Box sx={{pb: 4}}>
                    <Autocomplete
                        options={templates}
                        renderInput={params => (
                            <TextField {...params} label={t('event.race.template.template')} />
                        )}
                        value={template}
                        onChange={(_e, newValue: AutocompleteOption | null) => {
                            setTemplate(newValue)
                            if (newValue) {
                                fillFormWithTemplate(newValue.id)
                            }
                        }}
                        loading={templatesPending}
                    />
                </Box>
                <RacePropertiesFormInputs
                    formContext={formContext}
                    fieldArrayModified={resetTemplate}
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: RaceForm, templateId: string | undefined): RaceRequest {
    return {
        properties: mapRaceFormToRacePropertiesRequest(formData),
        template: templateId,
    }
}

function templateLabelName(identifier: string, name: string) {
    return `${identifier} | ${name}`
}

export default RaceDialog
