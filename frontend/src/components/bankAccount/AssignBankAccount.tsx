import {Box, MenuItem, Select, Typography} from "@mui/material";
import {assignBankAccount, getAssignedBankAccount, getBankAccounts} from "@api/sdk.gen.ts";
import {useFetch} from "@utils/hooks.ts";
import {useState} from "react";
import Throbber from "@components/Throbber.tsx";
import {Trans} from "react-i18next";

const AssignBankAccount = () => {

    const [lastRequested, setLastRequested] = useState(Date.now())

    const handleChange = async (accountId?: string) => {
        await assignBankAccount({
            body: {
                bankAccount: accountId
            }
        })

        setLastRequested(Date.now())
    }

    const {data: accounts} = useFetch(
        signal => getBankAccounts({signal}),
        {
            deps: [lastRequested]
        }
    )

    const {data: assigned} = useFetch(
        signal => getAssignedBankAccount({signal}),
        {
            deps: [lastRequested]
        }
    )

    return (
        <Box>
            <Typography variant={'h2'}>
                <Trans i18nKey={'invoice.bank.global'}/>
            </Typography>
            {accounts && assigned ? (
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
                        {accounts?.data.map(acc => (
                            <MenuItem key={acc.id} value={acc.id}>
                                {acc.iban}
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

export default AssignBankAccount