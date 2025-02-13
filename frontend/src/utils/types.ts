export type PartialRequired<T, K extends keyof T = keyof T> = Omit<T, K> & Required<Pick<T, K>>

export type BaseEntityTableProps<E> = {
    lastRequested: number
    reloadData: () => void
    openDialog: (entity?: E) => void
    title?: string
}

export type BaseEntityDialogProps<E> = {
    dialogIsOpen: boolean
    closeDialog: () => void
    reloadData: () => void
    entity?: E
}

export type AutocompleteOption = {id: string; label: string}
