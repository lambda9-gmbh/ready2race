import {useFetch} from '@utils/hooks.ts'
import {getPossibleSubIns} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'

type Props = {
    setupRoundId: string
    selectedParticipantOut: string | null
}
const SubstitutionSelectParticipantIn = ({setupRoundId, selectedParticipantOut}: Props) => {
    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()
    const {data: subInsData} = useFetch(
        signal =>
            getPossibleSubIns({
                signal,
                path: {
                    eventId,
                    competitionId,
                    competitionSetupRoundId: setupRoundId,
                },
                query: {
                    participantId: selectedParticipantOut!,
                },
            }),
        {
            preCondition: () => selectedParticipantOut != null,
            deps: [eventId, competitionId, setupRoundId, selectedParticipantOut],
        },
    )

    console.log('Sub In Options', subInsData)

    return subInsData ? (
        <FormInputSelect
            name={'participantIn'}
            options={[
                ...subInsData.currentlyParticipating,
                ...subInsData.notCurrentlyParticipating,
            ].map(p => ({
                id: p.id,
                label: p.firstName + ' ' + p.lastName,
            }))}
            required
            label={'todo: P in'}
        />
    ) : (
        <></>
    )
}

export default SubstitutionSelectParticipantIn
