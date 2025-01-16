import {
    createRootRouteWithContext,
    createRoute,
    createRouter,
    ParsedLocation,
    redirect,
    SearchSchemaInput,
} from '@tanstack/react-router'
import {User} from './contexts/user/UserContext.ts'
import MainLayout from './layouts/MainLayout.tsx'
import LoginPage from './pages/LoginPage.tsx'
import {Privilege} from './api'
import EventPage from "./pages/EventPage.tsx";

const checkAuth = (
    context: User,
    location: ParsedLocation,
    privilege?: Privilege,
    globalOnly: boolean = false,
) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    if (privilege) {
        const privilegeScope = context.getPrivilegeScope(privilege)
        if (!privilegeScope || (globalOnly && privilegeScope !== 'global')) {
            throw redirect({to: '/dashboard'})
        }
    }
}

export const rootRoute = createRootRouteWithContext<User>()({
    component: () => <MainLayout />,
})

export const indexRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    beforeLoad: ({context}) => {
        throw redirect({to: context.loggedIn ? '/dashboard' : '/login'})
    },
})

type LoginSearch = {
    redirect?: string
}

export const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'login',
    component: () => <LoginPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
    validateSearch: ({redirect}: {redirect?: string} & SearchSchemaInput): LoginSearch => ({
        redirect,
    }),
})

export const dashboardRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'dashboard',
    component: () => <>dashboard</>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const eventRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'event',
    component: () => <EventPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    }
})

const routeTree = rootRoute.addChildren([indexRoute, loginRoute, dashboardRoute, eventRoute])

export const router = createRouter({
    routeTree,
    context: undefined!,
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
