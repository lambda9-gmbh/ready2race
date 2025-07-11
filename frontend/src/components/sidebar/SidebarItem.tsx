import {ReactElement} from 'react'
import {Link, LinkComponentProps} from '@tanstack/react-router'
import {ListItem, ListItemButton, ListItemIcon, ListItemText} from '@mui/material'
import {useSidebar} from './SidebarContext.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {Privilege} from '@api/types.gen.ts'

type Props = {
    text: string
    icon: ReactElement
} & (
    | {
          authenticatedOnly?: never
          privilege: Privilege
      }
    | {
          authenticatedOnly?: boolean
          privilege?: never
      }
) &
    LinkComponentProps<'a'>

const SidebarItem = ({text, icon, authenticatedOnly, privilege, ...linkProps}: Props) => {
    const {open} = useSidebar()
    const user = useUser()

    if (!user.loggedIn) {
        if (authenticatedOnly || privilege) {
            return <></>
        }
    } else {
        if (privilege && !user.checkPrivilege(privilege)) {
            return <></>
        }
    }

    return (
        <Link {...linkProps} title={text}>
            {({isActive}) => (
                <ListItem disablePadding>
                    <ListItemButton
                        sx={[
                            isActive &&
                                (theme => ({
                                    backgroundColor: theme.palette.primary.light,
                                    '&:hover': {
                                        backgroundColor: theme.palette.primary.light,
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
