
import { useLayoutEffect, useRef } from "react";
import { Outlet, useLocation } from "react-router-dom";

import useAuth from "@/auth/store";
import Navbar from "../components/Navbar";
import { Toaster } from "react-hot-toast";

function RootLayout() {
  const location = useLocation();
  const logout = useAuth((state) => state.logout);
  const isAuthenticated = useAuth(
    (state) => Boolean(state.accessToken && state.authStatus)
  );
  const previousPath = useRef(location.pathname);

  useLayoutEffect(() => {
    const leftDashboard = previousPath.current.startsWith("/dashboard");
    const isPublicDestination = ["/", "/login", "/signup"].includes(
      location.pathname
    );

    previousPath.current = location.pathname;

    if (leftDashboard && isPublicDestination && isAuthenticated) {
      void logout();
    }
  }, [isAuthenticated, location.pathname, logout]);

  return (
    <div>
      <Toaster />
      <Navbar />
      <Outlet />
    </div>
  );
}

export default RootLayout;
