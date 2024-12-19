import {
    createRootRouteWithContext,
    createRoute,
    createRouter,
    ParsedLocation,
    redirect,
} from '@tanstack/react-router'
import {User} from './contexts/user/UserContext.ts'
import MainLayout from './layouts/MainLayout.tsx'
import LoginPage from './pages/LoginPage.tsx'

const checkAuth = (
    context: User,
    location: ParsedLocation,
    privilege?: any,
    globalOnly: boolean = false,
) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    if (privilege) {
        const privilegeScope = context.getAuthorization(privilege)
        if (!privilegeScope || (globalOnly && privilegeScope !== 'global')) {
            throw redirect({to: '/dashboard'})
        }
    }
}

export const rootRoute = createRootRouteWithContext<User>()({
    component: () => <MainLayout />,
})

export const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'login',
    component: () => <LoginPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
})

export const dashboardRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'dashboard',
    component: () => <>dashboard</>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

const routeTree = rootRoute.addChildren([loginRoute, dashboardRoute])

export const router = createRouter({
    routeTree,
    context: undefined!,
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
