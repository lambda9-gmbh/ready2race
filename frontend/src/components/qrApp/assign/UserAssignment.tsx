import {
    Box,
    Card,
    CardActionArea,
    CardContent,
    InputAdornment,
    Stack,
    TextField,
    Typography
} from "@mui/material";
import React from "react";
import { useFetch } from "@utils/hooks.ts";
import { getUsers } from "@api/sdk.gen.ts";
import { useTranslation } from "react-i18next";
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';

interface UserAssignmentProps {
    onSelectUser: (user: {
        id: string;
        firstname: string;
        lastname: string;
    }) => void;
}

const UserAssignment: React.FC<UserAssignmentProps> = ({ onSelectUser }) => {
    const { t } = useTranslation();
    const [searchQuery, setSearchQuery] = React.useState('');

    const users = useFetch(signal => getUsers({ signal })).data;

    const filteredUsers = users?.data.filter(user =>
        searchQuery === '' ||
        `${user.firstname} ${user.lastname}`.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <Stack sx={{ width: '100%' }} spacing={2}>
            <Typography variant="subtitle1" fontWeight="medium">
                {t('qrAssign.users')}
            </Typography>

            <TextField
                fullWidth
                placeholder={t('common.search')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                slotProps={{
                    input: {
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon />
                            </InputAdornment>
                        ),
                    }
                }}
                sx={{ mb: 1 }}
            />

            <Stack spacing={1}>
                {filteredUsers && filteredUsers.length > 0 ? (
                    filteredUsers.map(user => (
                        <Card key={user.id}>
                            <CardActionArea onClick={() => onSelectUser(user)}>
                                <CardContent sx={{ py: 2 }}>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                        <PersonIcon sx={{ color: 'text.secondary' }} />
                                        <Typography variant="body1" fontWeight="medium">
                                            {user.firstname} {user.lastname}
                                        </Typography>
                                    </Box>
                                </CardContent>
                            </CardActionArea>
                        </Card>
                    ))
                ) : (
                    <Typography align="center" color="text.secondary">
                        {t('qrAssign.noData')}
                    </Typography>
                )}
            </Stack>
        </Stack>
    );
};

export default UserAssignment;