import {Button, Stack, Typography} from "@mui/material";
import {useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {router} from "@routes";

const QrEventsPage = () => {
    const navigate = router.navigate

    const {data} = useFetch(signal => getEvents({signal}))

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" fontSize="2rem" textAlign="center">
                QR-Events
            </Typography>
            {data?.data?.map(event =>
                <Button
                    key={event.id}
                    onClick={() => navigate({
                        to: "/app/$eventId/scanner",
                        params: {eventId: event.id}
                    })}
                    sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }}
                    fullWidth
                >
                    {event.name}
                </Button>
            )}
        </Stack>
    )
}

export default QrEventsPage