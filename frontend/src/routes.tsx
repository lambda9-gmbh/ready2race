import {
    createRootRouteWithContext,
    createRoute,
    createRouter,
    ParsedLocation,
    redirect,
    SearchSchemaInput,
} from '@tanstack/react-router'
import {User} from './contexts/user/UserContext.ts'
import RootLayout from './layouts/RootLayout.tsx'
import LoginPage from './pages/LoginPage.tsx'
import {Privilege} from './api'
import EventsPage from './pages/event/EventsPage.tsx'
import EventPage from './pages/event/EventPage.tsx'
import {eventReadGlobal, userReadGlobal} from './authorization/privileges.ts'
import UsersPage from './pages/UsersPage.tsx'
import UserPage from './pages/UserPage.tsx'

const checkAuth = (
    context: User,
    location: ParsedLocation,
    privilege?: Privilege,
    ownId?: string,
) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    if (privilege && !context.checkPrivilege(privilege) && context.id !== ownId) {
        throw redirect({to: '/dashboard'})
    }
}

export const rootRoute = createRootRouteWithContext<User>()({
    component: () => <RootLayout />,
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

export const usersRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'user',
})

export const usersIndexRoute = createRoute({
    getParentRoute: () => usersRoute,
    path: '/',
    component: () => <UsersPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, userReadGlobal)
    },
})

export const userRoute = createRoute({
    getParentRoute: () => usersRoute,
    path: '$userId',
})

export const userIndexRoute = createRoute({
    getParentRoute: () => userRoute,
    path: '/',
    component: () => <UserPage />,
    beforeLoad: ({context, location, params}) => {
        checkAuth(context, location, userReadGlobal, params.userId)
    },
})

export const eventsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'event',
})

export const eventsIndexRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '/',
    component: () => <EventsPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, eventReadGlobal)
    },
})

export const eventRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '$eventId',
})

export const eventIndexRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/',
    component: () => <EventPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, eventReadGlobal)
    },
})

const routeTree = rootRoute.addChildren([
    indexRoute,
    loginRoute,
    dashboardRoute,
    usersRoute.addChildren([usersIndexRoute, userRoute.addChildren([userIndexRoute])]),
    eventsRoute.addChildren([eventsIndexRoute, eventRoute.addChildren([eventIndexRoute])]),
])

export const router = createRouter({
    routeTree,
    context: undefined!,
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
