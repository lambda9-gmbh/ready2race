import {ReactElement} from 'react'
import {Link, LinkComponentProps} from '@tanstack/react-router'
import {Privilege} from '../../api'
import {ListItem, ListItemButton, ListItemIcon, ListItemText} from '@mui/material'
import {useSidebar} from './SidebarContext.ts'

type Props = {
    text: string
    icon: ReactElement
    privilege?: Privilege
} & LinkComponentProps<'a'>

const SidebarItem = ({text, icon, privilege, ...linkProps}: Props) => {
    const {open} = useSidebar()

    return (
        <Link {...linkProps}>
            {({isActive}) => (
                <ListItem disablePadding>
                    <ListItemButton
                        sx={[
                            isActive &&
                                (theme => ({
                                    color: theme.palette.primary.contrastText,
                                    backgroundColor: theme.palette.primary.main,
                                    '&:hover': {
                                        backgroundColor: theme.palette.primary.main,
                                    },
                                })),
                        ]}>
                        <ListItemIcon
                            sx={{
                                minWidth: 0,
                                paddingY: 0.5,
                                mr: open ? 3 : 'auto',
                            }}>
                            {icon}
                        </ListItemIcon>
                        {open && <ListItemText primary={text} sx={{margin: 0}} />}
                    </ListItemButton>
                </ListItem>
            )}
        </Link>
    )
}

export default SidebarItem
