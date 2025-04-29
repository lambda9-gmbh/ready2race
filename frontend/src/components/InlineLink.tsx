import {Link, LinkComponentProps} from '@tanstack/react-router'
import {PropsWithChildren} from 'react'

const InlineLink = ({children, ...props}: PropsWithChildren<LinkComponentProps<'a'>>) => {
    return (
        <Link style={{color: 'revert', textDecoration: 'revert'}} {...props}>
            {children}
        </Link>
    )
}
export default InlineLink
