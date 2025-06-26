import {Box, Button, Dialog, DialogActions, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {FormContainer, MultiSelectElement, useForm} from 'react-hook-form-mui'
import {AutocompleteOption} from '@utils/types.ts'
import {eventRoute} from '@routes'
import {useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {assignCompetitionsToEventDay, assignDaysToCompetition} from '@api/sdk.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import CompetitionAndDayAssignmentList from '@components/event/competitionAndDayAssignment/CompetitionAndDayAssignmentList.tsx'

type AssignmentForm = {
    selected: string[]
}

type Props = {
    entityPathId: string
    options: AutocompleteOption[]
    assignedEntities: string[]
    assignEntityLabel: string
    competitionsToDay: boolean
    onSuccess: () => void
}
const CompetitionAndDayAssignment = ({competitionsToDay, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const {eventId} = eventRoute.useParams()

    const formContext = useForm<AssignmentForm>({
        values: {selected: props.assignedEntities},
    })

    const watchSelected = formContext.watch('selected')

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
        console.log(formData)
        const {error} = competitionsToDay
            ? await assignCompetitionsToEventDay({
                  path: {eventId: eventId, eventDayId: props.entityPathId},
                  body: {
                      competitions: formData.selected,
                  },
              })
            : await assignDaysToCompetition({
                  path: {eventId: eventId, competitionId: props.entityPathId},
                  body: {
                      days: formData.selected,
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

    return (
        <>
            <Typography variant="h6">
                {competitionsToDay
                    ? t('event.eventDay.assignedCompetitions')
                    : t('event.competition.assignedDays')}
            </Typography>
            {user.checkPrivilege(updateEventGlobal) && (
                <Button onClick={openDialog} variant="outlined" sx={{mt: 1, mb: 2}}>
                    {t('common.edit')}
                </Button>
            )}
            <CompetitionAndDayAssignmentList
                assignedEntities={watchSelected
                    .map(val => props.options.find(opt => opt?.id ?? '' === val))
                    .filter(val => val !== undefined)}
                competitionsToDay={competitionsToDay}
            />
            <Dialog
                open={dialogOpen}
                onClose={closeDialog}
                fullWidth={true}
                maxWidth={'xs'}
                className="ready2race">
                <Box sx={{mx: 4, my: 2}}>
                    <FormContainer formContext={formContext} onSuccess={onSubmit}>
                        <MultiSelectElement
                            name={'selected'}
                            options={props.options}
                            showCheckbox
                            showChips
                            formControlProps={{sx: {width: 1, mt: 4}}}
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
        </>
    )
}

export default CompetitionAndDayAssignment
