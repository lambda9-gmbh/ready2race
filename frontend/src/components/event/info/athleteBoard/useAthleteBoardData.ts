import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'
import {usePolledEndpoint, PolledState} from '@utils/usePolledEndpoint.ts'

const FALLBACK_INTERVAL_SECONDS = 15

export type AthleteBoardState = PolledState<AthleteBoardDto>

/**
 * Lädt die Athleten-Anzeige im Takt, den der Server vorgibt.
 *
 * Der Takt selbst steckt in [usePolledEndpoint] — dieselbe Mechanik trägt „Mein Event".
 * Die drei Eigenschaften, auf die es hier ankommt, sind dort beschrieben und dürfen nicht
 * verlorengehen: der letzte gute Stand bleibt bei einem Netzabbruch stehen, „nie geladen"
 * bleibt von „geladen, aber leer" unterscheidbar (sonst behauptet die Anzeige fälschlich, es
 * sei kein Lauf in der Arena), und im Hintergrund wird nicht geladen.
 */
export const useAthleteBoardData = (eventId: string): AthleteBoardState =>
    usePolledEndpoint<AthleteBoardDto>(
        signal => getAthleteBoard({signal, path: {eventId}}),
        data =>
            data.refreshIntervalSeconds > 0
                ? data.refreshIntervalSeconds
                : FALLBACK_INTERVAL_SECONDS,
        [eventId],
    )
