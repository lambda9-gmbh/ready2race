import {getBoardView} from '@api/sdk.gen'
import {BoardViewDto} from '@api/types.gen'
import {usePolledEndpoint, PolledState} from '@utils/usePolledEndpoint.ts'

const FALLBACK_INTERVAL_SECONDS = 15

export type BoardViewState = PolledState<BoardViewDto>

/**
 * Lädt ein Board im Takt, den der Server vorgibt.
 *
 * Der Takt selbst steckt in [usePolledEndpoint] — dieselbe Mechanik trug die alte
 * Athleten-Anzeige und trägt „Mein Event". Die drei Eigenschaften, auf die es hier
 * ankommt, sind dort beschrieben und dürfen nicht verlorengehen: der letzte gute Stand
 * bleibt bei einem Netzabbruch stehen, „nie geladen" bleibt von „geladen, aber leer"
 * unterscheidbar (sonst behauptet die Anzeige fälschlich, es sei kein Lauf in der
 * Arena), und im Hintergrund wird nicht geladen.
 */
export const useBoardViewData = (eventId: string, boardId: string): BoardViewState =>
    usePolledEndpoint<BoardViewDto>(
        signal => getBoardView({signal, path: {eventId, boardId}}),
        data =>
            data.refreshIntervalSeconds > 0
                ? data.refreshIntervalSeconds
                : FALLBACK_INTERVAL_SECONDS,
        [eventId, boardId],
    )
