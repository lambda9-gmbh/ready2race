import {useFetch} from '@utils/hooks.ts'
import {getPossibleSubIns} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {ListSubheader, MenuItem, Select} from '@mui/material'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {Controller} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'

type Props = {
    setupRoundId: string
    selectedParticipantOut: string | null
}
const SubstitutionSelectParticipantIn = ({setupRoundId, selectedParticipantOut}: Props) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()
    const {data: subInsData} = useFetch(
        signal =>
            getPossibleSubIns({
                signal,
                path: {
                    eventId,
                    competitionId,
                    participantId: selectedParticipantOut!,
                },
                query: {},
            }),
        {
            preCondition: () => selectedParticipantOut != null,
            deps: [eventId, competitionId, setupRoundId, selectedParticipantOut],
        },
    )

    return subInsData ? (
        <Controller
            name={'participantIn'}
            rules={{
                required: t('common.form.required'),
            }}
            render={({
                field: {onChange: participantInOnChange, value: participantInValue = ''},
            }) => (
                <FormInputLabel
                    label={t(
                        'event.competition.execution.substitution.substituteFor.substituteFor',
                    )}
                    required={true}>
                    <Select
                        value={participantInValue}
                        onChange={e => {
                            participantInOnChange(e)
                        }}
                        sx={{width: 1}}>
                        <ListSubheader>
                            {t(
                                'event.competition.execution.substitution.substituteFor.notCurrentlyParticipating',
                            )}
                        </ListSubheader>
                        {subInsData.notCurrentlyParticipating.map(p => (
                            <MenuItem key={p.id} value={p.id}>
                                {p.firstName + ' ' + p.lastName}
                            </MenuItem>
                        ))}
                        <ListSubheader>
                            {t(
                                'event.competition.execution.substitution.substituteFor.currentlyParticipating',
                            )}
                        </ListSubheader>
                        {subInsData?.currentlyParticipating.map(p => (
                            <MenuItem key={p.id} value={p.id}>
                                {`${p.firstName} ${p.lastName} (${t(
                                    'event.competition.execution.substitution.substituteFor.team',
                                )} ${p.registrationName})`}
                            </MenuItem>
                        ))}
                    </Select>
                </FormInputLabel>
            )}
        />
    ) : (
        <></>
    )
}

export default SubstitutionSelectParticipantIn
