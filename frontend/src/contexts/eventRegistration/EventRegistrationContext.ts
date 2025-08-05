import {createContext, useContext} from "react";
import {EventRegistrationInfoDto} from "@api/types.gen.ts";

export type EventRegistration = {
    info: EventRegistrationInfoDto | null
}

export const EventRegistrationContext = createContext<EventRegistration | null>(null)

export const useEventRegistration = (): EventRegistration => {
    const eventRegistration = useContext(EventRegistrationContext)
    if (eventRegistration === null) {
        throw Error('Event Registration context not initialized')
    }
    return eventRegistration
}