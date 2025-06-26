import {Divider, IconButton, List, ListItem, Stack, Tooltip, Typography} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {eventRoute} from '@routes'
import {AutocompleteOption} from '@utils/types.ts'
import DeleteIcon from '@mui/icons-material/Delete'
import {useTranslation} from 'react-i18next'
import InputIcon from '@mui/icons-material/Input'

type Props = {
    assignedEntities: AutocompleteOption[]
    competitionsToDay: boolean
    removeElement?: (index: number) => void
}
const CompetitionAndDayAssignmentList = ({
    assignedEntities,
    competitionsToDay,
    removeElement,
}: Props) => {
    const {t} = useTranslation()
    const {eventId} = eventRoute.useParams()
    return (
        <List>
            {assignedEntities
                .filter(field => field !== null)
                .map((field, index) => (
                    <ListItem key={field.id + index}>
                        <Stack direction="row" spacing={2} sx={{mt: 1, alignItems: 'center'}}>
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
                            {removeElement && (
                                <Tooltip title={t('common.delete')}>
                                    <IconButton onClick={() => removeElement(index)}>
                                        <DeleteIcon />
                                    </IconButton>
                                </Tooltip>
                            )}
                        </Stack>
                        {index < assignedEntities.length - 1 && (
                            <Divider orientation="horizontal" sx={{mt: 1}} />
                        )}
                    </ListItem>
                ))}
        </List>
    )
}
export default CompetitionAndDayAssignmentList
