import {Button, Stack} from "@mui/material";
import {useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {router} from "@routes";

const QrEventsPage = () => {
    const navigate = router.navigate

    const {data} = useFetch(signal => getEvents({signal}))

    return (
        <Stack>
            {data?.data?.map(event =>
                <Button key={event.id} onClick={() => navigate({
                    to: "/app/$eventId/scanner",
                    params: {eventId: event.id}
                })}>{event.name}</Button>
            )}
        </Stack>
    )
}

export default QrEventsPage