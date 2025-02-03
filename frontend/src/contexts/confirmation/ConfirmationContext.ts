import {createContext, useContext} from "react";

export type Confirmation = {
    confirmAction: (action: () => void, options?: ConfirmationOptions) => void
}

export const ConfirmationContext = createContext<Confirmation | null>(null)

export const useConfirmation = (): Confirmation => {
    const confirmation = useContext(ConfirmationContext)
    if(confirmation === null){
        throw Error('Confirmation context not initialized')
    }
    return confirmation
}

export type ConfirmationOptions = {
    title?: string
    content?: string
    cancelText?: string
    okText?: string
    cancelAction?: () => void
}
