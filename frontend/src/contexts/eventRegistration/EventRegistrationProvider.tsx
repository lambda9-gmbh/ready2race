import {PropsWithChildren} from "react";
import {EventRegistration, EventRegistrationContext} from "@contexts/eventRegistration/EventRegistrationContext.ts";
import {EventRegistrationInfoDto} from "@api/types.gen.ts";

type Props = {
    info: EventRegistrationInfoDto | null
}

const EventRegistrationProvider = ({info, children}: PropsWithChildren<Props>) => {

    const eventRegistrationValue: EventRegistration = {
        info
    }

    return <EventRegistrationContext.Provider value={eventRegistrationValue}>{children}</EventRegistrationContext.Provider>
}

export default EventRegistrationProvider