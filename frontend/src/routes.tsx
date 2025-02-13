import {
    createRootRouteWithContext,
    createRoute,
    createRouter,
    ParsedLocation,
    redirect,
    SearchSchemaInput,
} from '@tanstack/react-router'
import {AuthenticatedUser, User} from './contexts/user/UserContext.ts'
import RootLayout from './layouts/RootLayout.tsx'
import LoginPage from './pages/LoginPage.tsx'
import {Action, Privilege, Resource, Scope} from './api'
import {readEventGlobal, readRoleGlobal, readUserGlobal} from './authorization/privileges.ts'
import UsersPage from './pages/UsersPage.tsx'
import UserPage from './pages/UserPage.tsx'
import RolesPage from './pages/RolesPage.tsx'
import EventsPage from './pages/event/EventsPage.tsx'
import EventPage from './pages/event/EventPage.tsx'
import RacePage from './pages/event/RacePage.tsx'
import EventDayPage from './pages/event/EventDayPage.tsx'
import RaceConfigPage from './pages/event/RaceConfigPage.tsx'

const checkAuth = (context: User, location: ParsedLocation, privilege?: Privilege) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    if (privilege && !context.checkPrivilege(privilege)) {
        throw redirect({to: '/dashboard'})
    }
}

const checkAuthWith = (
    context: User,
    location: ParsedLocation,
    action: Action,
    resource: Resource,
    f: (authenticated: AuthenticatedUser, scope: Scope) => boolean,
) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    const scope = context.getPrivilegeScope(action, resource)
    if (!scope || !f(context, scope)) {
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
        checkAuth(context, location, readUserGlobal)
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
        checkAuthWith(
            context,
            location,
            'READ',
            'USER',
            (user, scope) => scope === 'GLOBAL' || params.userId === user.id,
        )
    },
})

export const rolesRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'role',
})

export const rolesIndexRoute = createRoute({
    getParentRoute: () => rolesRoute,
    path: '/',
    component: () => <RolesPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readRoleGlobal)
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
        checkAuth(context, location, readEventGlobal)
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
        checkAuth(context, location, readEventGlobal)
    },
})

export const eventDayRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'eventDay/$eventDayId',
})

export const eventDayIndexRoute = createRoute({
    getParentRoute: () => eventDayRoute,
    path: '/',
    component: () => <EventDayPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const raceRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'race/$raceId',
})

export const raceIndexRoute = createRoute({
    getParentRoute: () => raceRoute,
    path: '/',
    component: () => <RacePage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const raceConfigRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'raceConfig',
})

export const raceConfigIndexRoute = createRoute({
    getParentRoute: () => raceConfigRoute,
    path: '/',
    component: () => <RaceConfigPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

const routeTree = rootRoute.addChildren([
    indexRoute,
    loginRoute,
    dashboardRoute,
    eventsRoute.addChildren([
        eventsIndexRoute,
        eventRoute.addChildren([
            eventIndexRoute,
            eventDayRoute.addChildren([eventDayIndexRoute]),
            raceRoute.addChildren([raceIndexRoute]),
        ]),
    ]),
    raceConfigRoute.addChildren([raceConfigIndexRoute]),
    usersRoute.addChildren([usersIndexRoute, userRoute.addChildren([userIndexRoute])]),
    rolesRoute.addChildren([rolesIndexRoute]),
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
