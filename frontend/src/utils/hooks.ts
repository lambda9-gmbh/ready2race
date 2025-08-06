import {RequestResult} from '@hey-api/client-fetch'
import {DependencyList, useEffect, useLayoutEffect, useState} from 'react'
import {useSnackbar} from 'notistack'
import {GridValidRowModel} from '@mui/x-data-grid'
import {useTranslation} from 'react-i18next'
import {ApiError, CaptchaDto} from '@api/types.gen.ts'
import {newCaptcha} from '@api/sdk.gen.ts'

type UseFetchOptions<T, E, R> = {
    mapData?: (data: T) => R
    onResponse?: (result: Awaited<RequestResult<T, E, false>>) => void
    preCondition?: () => boolean
    deps?: DependencyList
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

export const useFetch = <T, E, R = T>(
    req: (abortSignal: AbortSignal) => RequestResult<T, E, false>,
    options?: UseFetchOptions<T, E, R>,
): UseFetchReturn<R, E> => {
    const [result, setResult] = useState<UseFetchReturn<R, E>>(fetchPending)

    useEffect(() => {
        if (options?.preCondition?.() != false) {
            const controller = new AbortController()
            setResult(fetchPending)
            ;(async () => {
                try {
                    const result = await req(controller.signal)
                    const {data, error, response} = result
                    options?.onResponse?.(result)
                    if (data !== undefined) {
                        setResult({
                            pending: false,
                            error: null,
                            // @ts-ignore
                            data: options?.mapData ? options.mapData(data) : data,
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
    }, [...(options?.deps ?? [])])

    return result
}

export type UseParamDialogStateReturn<T> = [
    boolean,
    (params?: T) => void,
    () => void,
    props: T | undefined,
]

export const useParamDialogState = <T>(defaultOpen: boolean): UseParamDialogStateReturn<T> => {
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

export type UseEntityAdministrationOptions = {
    entityCreate?: boolean
    entityUpdate?: boolean
    entityDelete?: boolean
}

export type UseEntityAdministrationReturn<T> = {
    table: {
        entityName: string
        lastRequested: number
        reloadData: () => void
        openDialog: (entity?: T) => void
        options: UseEntityAdministrationOptions
    }
    dialog: {
        entityName: string
        dialogIsOpen: boolean
        closeDialog: () => void
        reloadData: () => void
        entity?: T
    }
}

export const useEntityAdministration = <T extends GridValidRowModel | undefined = undefined>(
    entityName: string,
    {entityCreate = true, entityUpdate = true, entityDelete = true}: UseEntityAdministrationOptions = {},
): UseEntityAdministrationReturn<T> => {
    const [dialogIsOpen, openDialog, closeDialog, entity] = useParamDialogState<T>(false)

    const [lastRequested, setLastRequested] = useState(Date.now())

    const reloadData = () => setLastRequested(Date.now())

    const options: UseEntityAdministrationOptions = {
        entityCreate,
        entityUpdate,
        entityDelete
    }

    return {
        table: {
            entityName,
            lastRequested,
            reloadData,
            openDialog,
            options,
        },
        dialog: {
            entityName,
            dialogIsOpen,
            closeDialog,
            reloadData,
            entity,
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

export const useWindowSize = (delay?: number) => {
    const [size, setSize] = useState<[number, number]>([window.innerWidth, window.innerHeight])

    useLayoutEffect(() => {
        let timer: ReturnType<typeof setTimeout>
        const updateSize = () => {
            clearTimeout(timer)
            timer = setTimeout(() => setSize([window.innerWidth, window.innerHeight]), delay ?? 500)
        }
        window.addEventListener('resize', updateSize)
        return () => {
            clearTimeout(timer)
            window.removeEventListener('resize', updateSize)
        }
    }, [delay])
    return size
}

type CaptchaFetchProps = {
    captcha: UseFetchReturn<CaptchaDto, ApiError>
    onSubmitResult: () => void
}
export const useCaptcha = (onSuccess: (captcha: CaptchaDto) => void): CaptchaFetchProps => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [lastRequested, setLastRequested] = useState(Date.now())

    const captchaData = useFetch(signal => newCaptcha({signal}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                onSuccess(data)
            }
        },
        deps: [lastRequested],
    })

    return {
        captcha: captchaData,
        onSubmitResult: () => {
            setLastRequested(Date.now())
        },
    }
}
