import {GridActionsCellItemProps} from '@mui/x-data-grid'
import {Privilege} from '@api/types.gen.ts'
import {ReactElement, ReactNode} from 'react'
import {UseEntityAdministrationOptions} from './hooks.ts'

export type PartialRequired<T, K extends keyof T = keyof T> = Omit<T, K> & Required<Pick<T, K>>

export type BaseEntityTableProps<E> = {
    entityName: string
    lastRequested: number
    reloadData: () => void
    openDialog: (entity?: E) => void
    options: UseEntityAdministrationOptions
    title?: string
    id?: string
    hints?: ReactNode[]
}

export type EntityTableAction = ReactElement<
    GridActionsCellItemProps & {
        privilege?: Privilege
    }
>

export type BaseEntityDialogProps<E> = {
    entityName: string
    dialogIsOpen: boolean
    closeDialog: () => void
    reloadData: () => void
    entity?: E
}

export type AutocompleteOption = {id: string; label: string} | null
