import {Stack, Typography, FormControlLabel, Checkbox} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'

interface RequirementsChecklistProps {
    requirements: ParticipantRequirementForEventDto[]
    checkedRequirements: string[]
    pending: boolean
    onRequirementChange: (
        requirementId: string,
        checked: boolean,
        namedParticipantId?: string,
    ) => void
    namedParticipantIds: string[]
}

export const RequirementsChecklist = ({
    requirements,
    checkedRequirements,
    pending,
    onRequirementChange,
    namedParticipantIds,
}: RequirementsChecklistProps) => {
    const {t} = useTranslation()

    return (
        <Stack spacing={1}>
            <Typography variant="h6">
                {t('participantRequirement.participantRequirements')}
            </Typography>

            {pending && <Typography>{t('qrParticipant.loading') as string}</Typography>}

            {requirements.length === 0 && !pending && (
                <Typography>{t('qrParticipant.noRequirements') as string}</Typography>
            )}

            {requirements.map(req => (
                <FormControlLabel
                    key={req.id}
                    control={
                        <Checkbox
                            checked={checkedRequirements.includes(req.id)}
                            onChange={e => {
                                console.log('XXX', req.requirements)
                                console.log('YYY', namedParticipantIds)
                                console.log(
                                    'ZZZ',
                                    req.requirements?.find(npReq =>
                                        namedParticipantIds.some(np => np === npReq.id),
                                    )?.id,
                                )
                                onRequirementChange(
                                    req.id,
                                    e.target.checked,
                                    req.requirements?.find(npReq =>
                                        namedParticipantIds.some(np => np === npReq.id),
                                    )?.id,
                                )
                            }}
                            disabled={pending}
                        />
                    }
                    label={req.name}
                />
            ))}
        </Stack>
    )
}
