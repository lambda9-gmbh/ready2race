import {InputAdornment, TextField} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import React, {useEffect} from "react";
import {useTranslation} from "react-i18next";
import {useDebounce} from "@utils/hooks.ts";

type Props = {
    setSearchQuery: (query: string) => void
}
const AssignmentSearchField = (props: Props) => {
    const {t} = useTranslation()

    const [searchQuery, setSearchQuery] = React.useState('')
    const debouncedSearchQuery = useDebounce(searchQuery, 700)

    useEffect(() => {
        props.setSearchQuery(debouncedSearchQuery)
    }, [debouncedSearchQuery, props]);

    return (<TextField
        fullWidth
        placeholder={t('common.search')}
        value={searchQuery}
        onChange={e => setSearchQuery(e.target.value)}
        slotProps={{
            input: {
                startAdornment: (
                    <InputAdornment position="start">
                        <SearchIcon/>
                    </InputAdornment>
                ),
            },
        }}
        sx={{mb: 1}}
    />)
}

export default AssignmentSearchField