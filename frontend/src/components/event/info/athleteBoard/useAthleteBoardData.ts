import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'
import {usePolledEndpoint, PolledState} from '@utils/usePolledEndpoint.ts'

const FALLBACK_INTERVAL_SECONDS = 15

export type AthleteBoardState = PolledState<AthleteBoardDto>

export const useAthleteBoardData = (eventId: string): AthleteBoardState =>
    usePolledEndpoint<AthleteBoardDto>(
        signal => getAthleteBoard({signal, path: {eventId}}),
        data =>
            data.refreshIntervalSeconds > 0
                ? data.refreshIntervalSeconds
                : FALLBACK_INTERVAL_SECONDS,
        [eventId],
    )
