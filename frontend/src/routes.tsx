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
import EventsPage from "./pages/event/EventsPage.tsx";
import EventPage from "./pages/event/EventPage.tsx";
import RacePage from "./pages/event/RacePage.tsx";
import EventDayPage from "./pages/event/EventDayPage.tsx";

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

export const eventsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'event',
})

export const eventsIndexRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '/',
    component: () => <EventsPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    }
})

export const eventRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '$eventId',
})

export const eventIndexRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/',
    component: () => <EventPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    }
})

export const eventDayRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'eventDay/$eventDayId',
})

export const eventDayIndexRoute = createRoute({
    getParentRoute: () => eventDayRoute,
    path: '/',
    component: () => <EventDayPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    }
})

export const raceRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'race/$raceId',
})

export const raceIndexRoute = createRoute({
    getParentRoute: () => raceRoute,
    path: '/',
    component: () => <RacePage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    }
})

const routeTree = rootRoute.addChildren([
    indexRoute,
    loginRoute,
    dashboardRoute,
    eventsRoute.addChildren([
        eventsIndexRoute,
        eventRoute.addChildren([
            eventIndexRoute,
            eventDayRoute.addChildren([
                eventDayIndexRoute,
            ]),
            raceRoute.addChildren([
                raceIndexRoute,
            ])
        ])
    ]),
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
