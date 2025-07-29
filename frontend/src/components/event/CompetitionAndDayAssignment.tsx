import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    List,
    ListItem,
    Stack,
    Typography,
} from '@mui/material'
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
import {Link} from '@tanstack/react-router'
import InputIcon from '@mui/icons-material/Input'
import BaseDialog from '@components/BaseDialog.tsx'

type AssignmentForm = {
    selected: string[]
}

type Props = {
    entityPathId: string
    options: AutocompleteOption[]
    assignedEntities: string[]
    assignEntityLabel: string
    competitionsToDay: boolean
    reloadData: () => void
}
const CompetitionAndDayAssignment = ({competitionsToDay, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const {eventId} = eventRoute.useParams()

    const formContext = useForm<AssignmentForm>({
        values: {selected: props.assignedEntities},
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
            feedback.success(t('event.assign.save.success', entityNames))
        }
        props.reloadData()
    }

    const assignedEntities = props.assignedEntities.map(entityId =>
        props.options.find(opt => opt?.id === entityId),
    )

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
            <List>
                {assignedEntities
                    .filter(field => field !== null)
                    .map(
                        (field, index) =>
                            field && (
                                <ListItem key={field.id + index}>
                                    <Stack
                                        direction="row"
                                        spacing={2}
                                        sx={{mt: 1, alignItems: 'center'}}>
                                        <Link
                                            to={
                                                competitionsToDay
                                                    ? '/event/$eventId/competition/$competitionId'
                                                    : '/event/$eventId/eventDay/$eventDayId'
                                            }
                                            params={
                                                competitionsToDay
                                                    ? {eventId: eventId, competitionId: field.id}
                                                    : {eventId: eventId, eventDayId: field.id}
                                            }
                                            style={{alignItems: 'center', display: 'flex'}}>
                                            <InputIcon />
                                        </Link>
                                        <Typography variant="body1">{field.label}</Typography>
                                    </Stack>
                                    {index < assignedEntities.length - 1 && (
                                        <Divider orientation="horizontal" sx={{mt: 1}} />
                                    )}
                                </ListItem>
                            ),
                    )}
            </List>
            <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'xs'}>
                <DialogTitle>
                    {competitionsToDay
                        ? t('event.eventDay.assignedCompetitions')
                        : t('event.competition.assignedDays')}
                </DialogTitle>
                <FormContainer formContext={formContext} onSuccess={onSubmit}>
                    <DialogContent dividers>
                        <MultiSelectElement
                            name={'selected'}
                            options={[...props.options]}
                            showCheckbox
                            showChips
                            formControlProps={{sx: {width: 1}}}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeDialog} disabled={submitting}>
                            {t('common.cancel')}
                        </Button>
                        <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        </>
    )
}

export default CompetitionAndDayAssignment
