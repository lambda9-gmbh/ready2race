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
import {useFeedback} from '../../../utils/hooks.ts'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import {AutocompleteOption} from '../../../utils/types.ts'
import {eventRoute} from '../../../routes.tsx'
import {useState} from 'react'
import RaceAndDayAssignmentList from './RaceAndDayAssignmentList.tsx'
import {SubmitButton} from '../../form/SubmitButton.tsx'
import {assignDaysToRace, assignRacesToEventDay} from '../../../api'

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
    racesToDay: boolean
    onSuccess: () => void
}
const RaceAndDayAssignment = ({racesToDay, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    console.log(props.assignedEntities)

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
        toBeAssigned: racesToDay ? t('event.race.races') : t('event.eventDay.eventDays'),
        assignedTo: racesToDay ? t('event.eventDay.eventDay') : t('event.race.race'),
    }

    const onSubmit = async (formData: AssignmentForm) => {
        setSubmitting(true)
        const result = racesToDay
            ? await assignRacesToEventDay({
                  path: {eventId: eventId, eventDayId: props.entityPathId},
                  body: {
                      races: formData.selected.map(value => value.entry.id),
                  },
              })
            : await assignDaysToRace({
                  path: {eventId: eventId, raceId: props.entityPathId},
                  body: {
                      days: formData.selected.map(value => value.entry.id),
                  },
              })
        setSubmitting(false)

        if (result) {
            if (result.error) {
                // todo better error display with specific error types
                console.log(result.error)
                feedback.error(t('event.assign.save.error', entityNames))
            } else {
                closeDialog()
                // todo reloadData()
                props.onSuccess()
                feedback.success(t('event.assign.save.success', entityNames))
            }
        }
    }

    const filteredOptions = props.options.filter(
        value => !entityFields.some(ar => ar.entry.id === value.id),
    )

    const [autocompleteContent, setAutocompleteContent] = useState({id: '', label: ''})

    return (
        <Box sx={{flex: 1, border: 1, borderRadius: 4, p: 4}}>
            <Typography variant="h2">{racesToDay ? t('event.eventDay.assignedRaces') : t('event.race.assignedDays')}</Typography>
            <Button onClick={openDialog} variant="outlined" sx={{mt: 1, mb: 2}}>
                {t('common.edit')}
            </Button>
            <RaceAndDayAssignmentList
                assignedEntities={entityFields.map(value => value.entry)}
                racesToDay={racesToDay}
            />
            <Dialog
                open={dialogOpen}
                onClose={closeDialog}
                fullWidth={true}
                maxWidth={'xs'}
                className="ready2race">
                <Box sx={{ mx: 4, my: 2}}>
                    <Autocomplete
                        value={autocompleteContent}
                        options={filteredOptions}
                        onChange={(_e, newValue) => {
                            if (newValue) {
                                appendEntity({entry: newValue})
                                setAutocompleteContent({id: '', label: ''})
                            }
                        }}
                        renderInput={params => (
                            <TextField {...params} placeholder={props.assignEntityLabel} />
                        )}
                        sx={{mt: 4}}
                    />
                    <FormContainer formContext={formContext} onSuccess={onSubmit}>
                        <RaceAndDayAssignmentList
                            assignedEntities={entityFields.map(value => value.entry)}
                            racesToDay={racesToDay}
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

export default RaceAndDayAssignment
