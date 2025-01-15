import {RequestResult} from '@hey-api/client-fetch'
import {DependencyList, useEffect, useState} from 'react'

type UseFetchOptions<T, E> = {
    onResponse?: (result: Awaited<RequestResult<T, E, false>>) => void
    preCondition?: () => boolean
}

export type FetchError<E> = {
    status: number
    error: E
}

export type UseFetchReturn<T, E> =
    | {
          pending: true
          error: null
          data: null
      }
    | {
          pending: false
          error: FetchError<E>
          data: null
      }
    | {
          pending: false
          error: null
          data: T
      }

const fetchPending: UseFetchReturn<unknown, unknown> = {
    pending: true,
    error: null,
    data: null,
}

export const useFetch = <T, E>(
    req: (abortSignal: AbortSignal) => RequestResult<T, E, false>,
    options?: UseFetchOptions<T, E>,
    deps?: DependencyList,
): UseFetchReturn<T, E> => {
    const [result, setResult] = useState<UseFetchReturn<T, E>>(fetchPending)

    useEffect(() => {
        if (options?.preCondition?.() != false) {
            const controller = new AbortController()
            setResult(fetchPending)
            ;(async () => {
                try {
                    const result = await req(controller.signal)
                    const {data, error, response} = result
                    if (data !== undefined) {
                        options?.onResponse?.(result)
                        setResult({
                            pending: false,
                            error: null,
                            data,
                        })
                    } else if (error !== undefined) {
                        setResult({
                            pending: false,
                            error: {
                                status: response.status,
                                error,
                            },
                            data: null,
                        })
                    }
                } catch (error) {
                    if (!controller.signal.aborted) {
                        throw error
                    }
                }
            })()

            return () => {
                controller.abort()
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [...(deps ?? [])])

    return result
}
