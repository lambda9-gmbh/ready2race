import {AutocompleteOption, BaseEntityDialogProps} from '@utils/types.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {Autocomplete, Box, Stack, TextField} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {useForm} from 'react-hook-form-mui'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useCallback, useEffect, useState} from 'react'
import {
    mapCompetitionFormToCompetitionPropertiesRequest,
    mapCompetitionPropertiesToCompetitionForm,
    CompetitionForm,
    competitionFormDefaultValues,
    competitionLabelName,
} from './common.ts'
import {CompetitionPropertiesFormInputs} from './CompetitionPropertiesFormInputs.tsx'
import {CompetitionDto, CompetitionRequest} from '@api/types.gen.ts'
import {addCompetition, getCompetitionTemplates, updateCompetition} from '@api/sdk.gen.ts'

const CompetitionDialog = (props: BaseEntityDialogProps<CompetitionDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: CompetitionForm) => {
        return addCompetition({
            path: {eventId: eventId},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const editAction = (formData: CompetitionForm, entity: CompetitionDto) => {
        return updateCompetition({
            path: {eventId: entity.event, competitionId: entity.id},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const {data: templatesData, pending: templatesPending} = useFetch(
        signal => getCompetitionTemplates({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.template.templates'),
                        }),
                    )
                }
            },
        },
        // todo: consider if the templates, competitionCategories and namedParticipants are stale data and should be reloaded
    )
    const templates: AutocompleteOption[] =
        templatesData?.data.map(dto => ({
            id: dto.id,
            label: competitionLabelName(dto.properties.identifier, dto.properties.name),
        })) ?? []

    const formContext = useForm<CompetitionForm>()

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
            formContext.reset(
                mapCompetitionPropertiesToCompetitionForm(template.properties, t('decimal.point')),
            )
        }
    }

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? mapCompetitionPropertiesToCompetitionForm(
                      props.entity.properties,
                      t('decimal.point'),
                  )
                : competitionFormDefaultValues,
        )
        setTemplate(templates.find(t => t?.id === props.entity?.template) ?? null)
    }, [props.entity, templatesData])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            {
                // render only when all is loaded (templates, categories, namedParticipants)
                // also in CompetitionTemplates
            }
            <Stack spacing={4}>
                <Box sx={{pb: 4}}>
                    <Autocomplete
                        options={templates}
                        renderInput={params => (
                            <TextField
                                {...params}
                                label={t('event.competition.template.template')}
                            />
                        )}
                        value={template}
                        onChange={(_e, newValue: AutocompleteOption) => {
                            setTemplate(newValue)
                            if (newValue) {
                                fillFormWithTemplate(newValue.id)
                            }
                        }}
                        loading={templatesPending}
                    />
                </Box>
                <CompetitionPropertiesFormInputs
                    formContext={formContext}
                    fieldArrayModified={resetTemplate}
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(
    formData: CompetitionForm,
    templateId: string | undefined,
): CompetitionRequest {
    return {
        properties:
            templateId === undefined
                ? mapCompetitionFormToCompetitionPropertiesRequest(formData)
                : undefined,
        template: templateId,
    }
}

export default CompetitionDialog
