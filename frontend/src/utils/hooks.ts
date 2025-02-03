import {RequestResult} from '@hey-api/client-fetch'
import {DependencyList, useEffect, useState} from 'react'
import {useSnackbar} from 'notistack'
import {GridValidRowModel} from "@mui/x-data-grid";

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

export type ParamDialogState<T> = [boolean, (params?: T) => void, () => void, props: T | undefined]

export const useParamDialogState = <T>(defaultOpen: boolean): ParamDialogState<T> => {
    const [dialogOpen, setDialogOpen] = useState(defaultOpen)
    const [props, setProps] = useState<T>()

    return [
        dialogOpen,
        (props?: T) => {
            setDialogOpen(true)
            setProps(props)
        },
        () => setDialogOpen(false),
        props,
    ]
}

export type EntityAdministration<T> = {
    dialogIsOpen: boolean
    openDialog: (entity?: T) => void
    closeDialog: () => void
    entity?: T
    lastRequested: number
    reloadData: () => void
}

export const useEntityAdministration = <
    T extends GridValidRowModel | undefined = undefined,
>(): EntityAdministration<T> => {
    const [dialogIsOpen, openDialog, closeDialog, entity] = useParamDialogState<T>(false)

    const [lastRequested, setLastRequested] = useState(Date.now())

    return {
        dialogIsOpen,
        openDialog,
        closeDialog,
        entity,
        lastRequested,
        reloadData: () => {
            setLastRequested(Date.now())
        },
    }
}

export const useFeedback = () => {
    const {enqueueSnackbar} = useSnackbar()

    return {
        success: (msg: string) => enqueueSnackbar(msg, {variant: 'success'}),
        warning: (msg: string) => enqueueSnackbar(msg, {variant: 'warning'}),
        error: (msg: string) => enqueueSnackbar(msg, {variant: 'error'}),
        info: (msg: string) => enqueueSnackbar(msg, {variant: 'info'}),
    }
}

export const useDebounce = <T>(value: T, delay?: number) => {
    const [debounced, setDebounced] = useState<T>(value)

    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay ?? 500)

        return () => {
            clearTimeout(timer)
        }
    }, [value, delay])

    return debounced
}
