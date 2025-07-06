import {Button, Stack, Typography} from "@mui/material";
import {useFetch} from "@utils/hooks.ts";
import {getEvents} from "@api/sdk.gen.ts";
import {router} from "@routes";
import {useTranslation} from "react-i18next";

const QrEventsPage = () => {
    const { t } = useTranslation();
    const navigate = router.navigate

    const {data} = useFetch(signal => getEvents({signal}))

    return (
        <Stack spacing={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrEvents.title')}
            </Typography>
            {data?.data?.map(event =>
                <Button
                    key={event.id}
                    onClick={() => navigate({
                        to: "/app/$eventId/scanner",
                        params: {eventId: event.id}
                    })}
                    fullWidth
                >
                    {event.name}
                </Button>
            )}
        </Stack>
    )
}

export default QrEventsPage