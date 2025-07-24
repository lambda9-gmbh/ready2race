import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getPossibleSubIns} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {ListSubheader, MenuItem, Select} from '@mui/material'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {Controller} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {groupBy} from '@utils/helpers.ts'

type Props = {
    setupRoundId: string
    selectedParticipantOut: string | null
}
const SubstitutionSelectParticipantIn = ({setupRoundId, selectedParticipantOut}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

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
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('event.competition.execution.substitution.substituteFor.load.error'))
                }
            },
            deps: [eventId, competitionId, setupRoundId, selectedParticipantOut],
        },
    )

    const notCurrentlyParticipating =
        subInsData?.notCurrentlyParticipating.sort((a, b) =>
            a.firstName > b.firstName
                ? 1
                : a.firstName === b.firstName
                  ? a.lastName > b.lastName
                      ? 1
                      : -1
                  : -1,
        ) ?? []

    const currentlyParticipating = Array.from(groupBy(subInsData?.currentlyParticipating ?? [], val => val.registrationName))
        .map(([key, participants]) => ({
            teamName: key,
            participants: participants
                .map(p => ({
                    id: p.id,
                    fullName: p.firstName + ' ' + p.lastName,
                    roleName: p.namedParticipantName,
                }))
                .sort((a, b) =>
                    (a.roleName ?? '') > (b.roleName ?? '')
                        ? 1
                        : a.roleName === b.roleName
                          ? a.fullName > b.fullName
                              ? 1
                              : -1
                          : -1,
                ),
        }))
        .sort((a, b) => ((a.teamName ?? '') > (b.teamName ?? '') ? 1 : -1))

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
                        {notCurrentlyParticipating.map(p => (
                            <MenuItem key={p.id} value={p.id}>
                                {p.firstName + ' ' + p.lastName}
                            </MenuItem>
                        ))}
                        <ListSubheader >
                            {t(
                                'event.competition.execution.substitution.substituteFor.currentlyParticipating',
                            )}
                        </ListSubheader>
                        {currentlyParticipating.flatMap(optGroup => [
                            <ListSubheader sx={{pl: 4}} key={`header-${optGroup.teamName}`}>
                                {t('event.competition.execution.substitution.substituteFor.team') + ": " + optGroup.teamName}
                            </ListSubheader>,
                            ...optGroup.participants.map(p => (
                                <MenuItem key={`ps-${p.id}`} value={p.id}>
                                    {p.fullName} ({p.roleName})
                                </MenuItem>
                            )),
                        ])}
                    </Select>
                </FormInputLabel>
            )}
        />
    ) : (
        <></>
    )
}

export default SubstitutionSelectParticipantIn
