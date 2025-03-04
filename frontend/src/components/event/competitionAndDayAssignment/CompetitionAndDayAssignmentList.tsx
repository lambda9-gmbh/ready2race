import {Box, Divider, IconButton, Stack, Tooltip, Typography, Zoom} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {eventRoute} from '@routes'
import {AutocompleteOption} from '@utils/types.ts'
import DeleteIcon from '@mui/icons-material/Delete'
import {useTranslation} from 'react-i18next'

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
        // todo: maybe use mui List component
        <>
            {assignedEntities
                .filter(field => field !== null)
                .map((field, index) => (
                    <Box key={`entry${index}`}>
                        <Stack
                            direction="row"
                            spacing={2}
                            alignItems="center"
                            justifyContent="space-between"
                            sx={{mt: 1}}>
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
                                }>
                                <Typography variant="body1">{field?.label}</Typography>
                            </Link>
                            {removeElement && (
                                <Tooltip
                                    title={t('common.delete')}
                                    disableInteractive
                                    slots={{
                                        transition: Zoom,
                                    }}>
                                    <IconButton onClick={() => removeElement(index)}>
                                        <DeleteIcon />
                                    </IconButton>
                                </Tooltip>
                            )}
                        </Stack>
                        {index < assignedEntities.length - 1 && (
                            <Divider orientation="horizontal" sx={{mt: 1}} />
                        )}
                    </Box>
                ))}
        </>
    )
}
export default CompetitionAndDayAssignmentList
