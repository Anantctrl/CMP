
import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
import { Button } from "./ui/button";
import { NavLink, useNavigate } from "react-router";
import useAuth from "@/auth/store";
import {
  Avatar,
  AvatarFallback,
} from "@/components/ui/avatar";
type Theme = "light" | "dark";
const THEME_STORAGE_KEY = "cmp-theme";

function Navbar() {
  const user = useAuth((state) => state.user);
  const logout = useAuth((state) => state.logout);
  const isLoggedIn = useAuth(
    (state) => Boolean(state.accessToken && state.authStatus)
  );

  const navigate = useNavigate();

  const [theme, setTheme] = useState<Theme>(() => {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    return savedTheme === "dark" ? "dark" : "light";
  });

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  const handleLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <nav className="flex w-full flex-col items-center justify-between gap-4 border-b border-gray-700 py-5 md:h-14 md:flex-row md:py-0 md:px-6">

      <div className="font-semibold items-center flex gap-2">
        <span className="inline-block text-center h-6 w-6 rounded-md bg-gradient-to-r from-primary to-primary/40">
          C
        </span>
        <span className="text-base tracking-tight">CMP</span>
      </div>

      <div className="flex gap-4 items-center">

        <Button
          type="button"
          size="icon"
          variant="outline"
          className="cursor-pointer"
          aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          aria-pressed={theme === "dark"}
          onClick={() =>
            setTheme((currentTheme) =>
              currentTheme === "dark" ? "light" : "dark"
            )
          }
        >
          {theme === "dark" ? <Sun /> : <Moon />}
        </Button>

        {isLoggedIn ? (
          <>
          <NavLink to="/dashboard/profile" title="My Profile">
          <Avatar className="border-2 border-[#F5F5DC] hover:scale-110 transition-transform duration-300 cursor-pointer">
            <AvatarFallback className="bg-primary text-primary-foreground font-bold text-lg">
              {user?.name?.charAt(0).toUpperCase() ?? "U"}
            </AvatarFallback>
          </Avatar>
        </NavLink>
            <Button
              onClick={handleLogout}
              size="sm"
              className="ml-auto cursor-pointer"
              variant="destructive"
            >
              Logout
            </Button>
          </>
        ) : (
          <>
            <NavLink to="/">
              <Button size="sm" className="cursor-pointer" variant="outline">
                Home
              </Button>
            </NavLink>

            <NavLink to="/login">
              <Button size="sm" className="cursor-pointer" variant="outline">
                Login
              </Button>
            </NavLink>

            <NavLink to="/signup">
              <Button size="sm" className="cursor-pointer" variant="outline">
                Signup
              </Button>
            </NavLink>
          </>
        )}

      </div>
    </nav>
  );
}

export default Navbar;
