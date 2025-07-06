import React from 'react';
import {Outlet} from "@tanstack/react-router";

const AppLayout: React.FC = () => {
    return (
        <div className="app-layout">
            <main>
                <Outlet />
            </main>
        </div>
    );
};

export default AppLayout; 