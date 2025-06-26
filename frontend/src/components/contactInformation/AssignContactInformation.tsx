import {useState} from "react";
import {useFetch} from "@utils/hooks.ts";
import {Box, MenuItem, Select, Typography} from "@mui/material";
import Throbber from "@components/Throbber.tsx";
import {assignContact, getAssignedContact, getContacts} from "@api/sdk.gen.ts";
import {Trans} from "react-i18next";

const AssignContactInformation = () => {

    const [lastRequested, setLastRequested] = useState(Date.now())

    const handleChange = async (contactId?: string) => {
        await assignContact({
            body: { contact: contactId }
        })

        setLastRequested(Date.now())
    }

    const {data: contacts} = useFetch(
        signal => getContacts({signal}),
        {
            deps: [lastRequested]
        }
    )

    const {data: assigned} = useFetch(
        signal => getAssignedContact({signal}),
        {
            deps: [lastRequested]
        }
    )

    return (
        <Box>
            <Typography variant={'h2'}>
                <Trans i18nKey={'contact.global'} />
            </Typography>
            {contacts && assigned ? (
                <Box sx={{mt: 2, pr: 2,display: 'flex', justifyContent: 'end'}}>
                    <Select
                        value={assigned?.assigned?.id ?? 'none'}
                        onChange={e => {
                            const value = e.target.value as string
                            if (value === 'none') {
                                handleChange()
                            } else {
                                handleChange(value)
                            }
                        }}
                    >
                        <MenuItem value={'none'}>
                            <Trans i18nKey={'common.form.select.none'} />
                        </MenuItem>
                        {contacts?.data.map(con => (
                            <MenuItem key={con.id} value={con.id}>
                                {con.name}
                            </MenuItem>
                        ))}
                    </Select>
                </Box>
            ) : (
                <Throbber/>
            )}
        </Box>
    )

}

export default AssignContactInformation