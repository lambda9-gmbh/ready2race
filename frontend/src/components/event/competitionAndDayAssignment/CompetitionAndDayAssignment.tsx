import {
    Autocomplete,
    Box,
    Button,
    Dialog,
    DialogActions,
    TextField,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {AutocompleteOption} from '@utils/types.ts'
import {eventRoute} from '@routes'
import {useState} from 'react'
import CompetitionAndDayAssignmentList from './CompetitionAndDayAssignmentList.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {assignDaysToCompetition, assignCompetitionsToEventDay} from '@api/sdk.gen.ts'

type AssignmentEntry = {
    entry: AutocompleteOption
}
type AssignmentForm = {
    selected: AssignmentEntry[]
}

type Props = {
    entityPathId: string
    options: AutocompleteOption[]
    assignedEntities: AutocompleteOption[]
    assignEntityLabel: string
    competitionsToDay: boolean
    onSuccess: () => void
}
const CompetitionAndDayAssignment = ({competitionsToDay, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const formContext = useForm<AssignmentForm>({
        values: {selected: props.assignedEntities.map(value => ({entry: value}))},
    })

    const {
        fields: entityFields,
        append: appendEntity,
        remove: removeEntity,
    } = useFieldArray({
        control: formContext.control,
        name: 'selected',
        keyName: 'fieldId',
    })

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)

    const openDialog = () => {
        setDialogOpen(true)
    }

    const closeDialog = () => {
        setDialogOpen(false)
    }

    const entityNames = {
        toBeAssigned: competitionsToDay
            ? t('event.competition.competitions')
            : t('event.eventDay.eventDays'),
        assignedTo: competitionsToDay
            ? t('event.eventDay.eventDay')
            : t('event.competition.competition'),
    }

    const onSubmit = async (formData: AssignmentForm) => {
        setSubmitting(true)
        const {error} = competitionsToDay
            ? await assignCompetitionsToEventDay({
                  path: {eventId: eventId, eventDayId: props.entityPathId},
                  body: {
                      competitions: formData.selected
                          .map(value => value.entry?.id)
                          .filter(value => value !== undefined),
                  },
              })
            : await assignDaysToCompetition({
                  path: {eventId: eventId, competitionId: props.entityPathId},
                  body: {
                      days: formData.selected
                          .map(value => value.entry?.id)
                          .filter(value => value !== undefined),
                  },
              })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.assign.save.error', entityNames))
        } else {
            closeDialog()
            props.onSuccess()
            feedback.success(t('event.assign.save.success', entityNames))
        }
    }

    const filteredOptions = props.options.filter(
        value => !entityFields.some(ar => ar.entry?.id === value?.id),
    )

    const [autocompleteContent, setAutocompleteContent] = useState<AutocompleteOption>(null)

    return (
        <Box sx={{flex: 1, border: 1, borderRadius: 4, p: 4}}>
            <Typography variant="h2">
                {competitionsToDay
                    ? t('event.eventDay.assignedCompetitions')
                    : t('event.competition.assignedDays')}
            </Typography>
            <Button onClick={openDialog} variant="outlined" sx={{mt: 1, mb: 2}}>
                {t('common.edit')}
            </Button>
            <CompetitionAndDayAssignmentList
                assignedEntities={entityFields.map(value => value.entry)}
                competitionsToDay={competitionsToDay}
            />
            <Dialog
                open={dialogOpen}
                onClose={closeDialog}
                fullWidth={true}
                maxWidth={'xs'}
                className="ready2competition">
                <Box sx={{mx: 4, my: 2}}>
                    <Autocomplete
                        value={autocompleteContent}
                        options={filteredOptions}
                        onChange={(_e, newValue) => {
                            if (newValue) {
                                appendEntity({entry: newValue})
                                setAutocompleteContent(null)
                            }
                        }}
                        renderInput={params => (
                            <TextField {...params} placeholder={props.assignEntityLabel} />
                        )}
                        sx={{mt: 4}}
                    />
                    <FormContainer formContext={formContext} onSuccess={onSubmit}>
                        <CompetitionAndDayAssignmentList
                            assignedEntities={entityFields.map(value => value.entry)}
                            competitionsToDay={competitionsToDay}
                            removeElement={removeEntity}
                        />
                        <Box sx={{mt: 2}}>
                            <DialogActions>
                                <Button onClick={closeDialog} disabled={submitting}>
                                    {t('common.cancel')}
                                </Button>
                                <SubmitButton label={t('common.save')} submitting={submitting} />
                            </DialogActions>
                        </Box>
                    </FormContainer>
                </Box>
            </Dialog>
        </Box>
    )
}

export default CompetitionAndDayAssignment
