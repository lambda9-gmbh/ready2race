import {BaseEntityDialogProps} from '@utils/types.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {Box, Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {useForm} from 'react-hook-form-mui'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useCallback} from 'react'
import {
    mapCompetitionFormToCompetitionPropertiesRequest,
    mapCompetitionPropertiesToCompetitionForm,
    CompetitionForm,
    competitionFormDefaultValues,
    competitionLabelName,
} from './common.ts'
import {CompetitionPropertiesFormInputs} from './CompetitionPropertiesFormInputs.tsx'
import {CompetitionDto} from '@api/types.gen.ts'
import {addCompetition, getCompetitionTemplates, updateCompetition} from '@api/sdk.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {createEventGlobal} from '@authorization/privileges.ts'
import SelectionMenu from '@components/SelectionMenu.tsx'

const CompetitionDialog = (props: BaseEntityDialogProps<CompetitionDto>) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: CompetitionForm) => {
        return addCompetition({
            path: {eventId: eventId},
            body: mapCompetitionFormToCompetitionPropertiesRequest(formData),
        })
    }

    const editAction = (formData: CompetitionForm, entity: CompetitionDto) => {
        return updateCompetition({
            path: {eventId: entity.event, competitionId: entity.id},
            body: mapCompetitionFormToCompetitionPropertiesRequest(formData),
        })
    }

    const {data: templatesData, pending: templatesPending} = useFetch(
        signal => getCompetitionTemplates({signal}),
        {
            preCondition: () =>
                user.checkPrivilege(createEventGlobal) && props.entity === undefined,
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

    const formContext = useForm<CompetitionForm>()

    const fillFormWithTemplate = async (templateId: string) => {
        const template = templatesData?.data.find(dto => dto?.id === templateId)
        if (template) {
            formContext.reset(
                mapCompetitionPropertiesToCompetitionForm(
                    template.properties,
                    t('decimal.point'),
                    props.entity ? undefined : template.setupTemplate,
                ),
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
    }, [props.entity])

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
                {props.entity === undefined && (
                    <Box>
                        <SelectionMenu
                            buttonContent={t('event.competition.template.select')}
                            onSelectItem={fillFormWithTemplate}
                            keyLabel={'competition-template-selection'}
                            items={templatesData?.data.map(template => ({
                                id: template.id,
                                label: competitionLabelName(
                                    template.properties.identifier,
                                    template.properties.name,
                                ),
                            }))}
                            pending={templatesPending}
                        />
                    </Box>
                )}
                <CompetitionPropertiesFormInputs
                    formContext={formContext}
                    hideCompetitionSetupTemplate={props.entity !== undefined}
                />
            </Stack>
        </EntityDialog>
    )
}

export default CompetitionDialog
