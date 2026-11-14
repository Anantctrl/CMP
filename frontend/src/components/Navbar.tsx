import { useEffect, useState } from "react";
import { Moon, Sun, Menu, X, Shield, Users, User as UserIcon, LogOut, Home, LogIn, UserPlus } from "lucide-react";
import { Button } from "./ui/button";
import { NavLink, useNavigate, useLocation } from "react-router";
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
  const location = useLocation();

  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const [theme, setTheme] = useState<Theme>(() => {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    return savedTheme === "dark" ? "dark" : "light";
  });

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  // Close mobile menu on route changes
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    setMobileMenuOpen(false);
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/80 bg-background/80 backdrop-blur-md transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 flex h-16 items-center justify-between">
        {/* Brand / Logo */}
        <NavLink
          to={isLoggedIn ? "/dashboard" : "/"}
          className="flex items-center gap-2.5 font-bold text-foreground hover:opacity-90 transition-opacity"
        >
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground font-black shadow-md shadow-primary/20">
            <Shield className="w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <span className="text-base sm:text-lg tracking-tight font-extrabold leading-none">CopMap</span>
            <span className="text-[10px] text-muted-foreground font-mono tracking-widest uppercase">Command</span>
          </div>
        </NavLink>

        {/* Desktop Navigation (md+) */}
        <div className="hidden md:flex items-center gap-3">
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="rounded-xl h-9 w-9 cursor-pointer text-muted-foreground hover:text-foreground"
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
            onClick={() =>
              setTheme((currentTheme) =>
                currentTheme === "dark" ? "light" : "dark"
              )
            }
          >
            {theme === "dark" ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4" />}
          </Button>

          {isLoggedIn ? (
            <>
              <NavLink to="/dashboard" title="Dashboard">
                {({ isActive }) => (
                  <Button
                    size="sm"
                    variant={isActive ? "secondary" : "ghost"}
                    className="cursor-pointer rounded-xl font-medium"
                  >
                    Overview
                  </Button>
                )}
              </NavLink>

              <NavLink to="/dashboard/teams" title="Tactical Teams">
                {({ isActive }) => (
                  <Button
                    size="sm"
                    variant={isActive ? "secondary" : "ghost"}
                    className="cursor-pointer rounded-xl font-medium gap-1.5"
                  >
                    <Users className="w-3.5 h-3.5 text-primary" />
                    Tactical Teams
                  </Button>
                )}
              </NavLink>

              <NavLink to="/dashboard/profile" title="My Profile">
                <div className="flex items-center gap-2 pl-2">
                  <Avatar className="h-8 w-8 border border-primary/40 hover:scale-105 transition-transform cursor-pointer">
                    <AvatarFallback className="bg-primary/10 text-primary font-bold text-xs">
                      {user?.name?.charAt(0).toUpperCase() ?? "O"}
                    </AvatarFallback>
                  </Avatar>
                  <span className="text-xs font-semibold text-foreground max-w-[120px] truncate hidden lg:inline">
                    {user?.name || "Officer"}
                  </span>
                </div>
              </NavLink>

              <Button
                onClick={handleLogout}
                size="sm"
                variant="destructive"
                className="cursor-pointer rounded-xl h-8 px-3 text-xs ml-1"
              >
                <LogOut className="w-3.5 h-3.5 mr-1" />
                Logout
              </Button>
            </>
          ) : (
            <>
              <NavLink to="/">
                <Button size="sm" variant="ghost" className="cursor-pointer rounded-xl font-medium">
                  Home
                </Button>
              </NavLink>

              <NavLink to="/login">
                <Button size="sm" variant="outline" className="cursor-pointer rounded-xl font-medium">
                  Login
                </Button>
              </NavLink>

              <NavLink to="/signup">
                <Button size="sm" className="cursor-pointer rounded-xl font-medium shadow-sm">
                  Sign Up
                </Button>
              </NavLink>
            </>
          )}
        </div>

        {/* Mobile Header Controls (< md) */}
        <div className="flex md:hidden items-center gap-2">
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="rounded-xl h-9 w-9 text-muted-foreground hover:text-foreground"
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
            onClick={() =>
              setTheme((currentTheme) =>
                currentTheme === "dark" ? "light" : "dark"
              )
            }
          >
            {theme === "dark" ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4" />}
          </Button>

          <Button
            type="button"
            size="icon"
            variant="outline"
            className="rounded-xl h-9 w-9 border-border"
            aria-label="Toggle navigation menu"
            onClick={() => setMobileMenuOpen((open) => !open)}
          >
            {mobileMenuOpen ? <X className="w-5 h-5 text-foreground" /> : <Menu className="w-5 h-5 text-foreground" />}
          </Button>
        </div>
      </div>

      {/* Mobile Collapsible Navigation Menu */}
      {mobileMenuOpen && (
        <div className="md:hidden border-t border-border/80 bg-background/95 backdrop-blur-xl px-4 py-4 space-y-3 animate-in slide-in-from-top-2 duration-200">
          {isLoggedIn ? (
            <>
              {/* User Identity Chip on Mobile */}
              <div className="flex items-center gap-3 p-3 rounded-xl bg-muted/50 border border-border/60">
                <Avatar className="h-10 w-10 border border-primary/40">
                  <AvatarFallback className="bg-primary/10 text-primary font-bold text-sm">
                    {user?.name?.charAt(0).toUpperCase() ?? "O"}
                  </AvatarFallback>
                </Avatar>
                <div className="flex flex-col min-w-0 flex-1">
                  <span className="text-sm font-bold text-foreground truncate">{user?.name || "Officer"}</span>
                  <span className="text-xs text-muted-foreground truncate font-mono">
                    {user?.policePost || "Field Command"} &bull; {user?.role?.name || "Member"}
                  </span>
                </div>
              </div>

              {/* Navigation Links */}
              <div className="space-y-1 pt-1">
                <NavLink
                  to="/dashboard"
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                      isActive ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"
                    }`
                  }
                >
                  <Home className="w-4 h-4" />
                  HQ Overview
                </NavLink>

                <NavLink
                  to="/dashboard/teams"
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                      isActive ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"
                    }`
                  }
                >
                  <Users className="w-4 h-4" />
                  Tactical Teams
                </NavLink>

                <NavLink
                  to="/dashboard/profile"
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                      isActive ? "bg-primary text-primary-foreground" : "text-foreground hover:bg-muted"
                    }`
                  }
                >
                  <UserIcon className="w-4 h-4" />
                  Officer Profile
                </NavLink>
              </div>

              <div className="pt-2 border-t border-border/60">
                <Button
                  onClick={handleLogout}
                  variant="destructive"
                  className="w-full justify-center rounded-xl gap-2 font-semibold"
                >
                  <LogOut className="w-4 h-4" />
                  Sign Out of Command
                </Button>
              </div>
            </>
          ) : (
            <div className="space-y-2 pt-1">
              <NavLink
                to="/"
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold text-foreground hover:bg-muted transition-colors"
              >
                <Home className="w-4 h-4" />
                Home
              </NavLink>

              <div className="grid grid-cols-2 gap-2 pt-2 border-t border-border/60">
                <Button
                  variant="outline"
                  className="w-full rounded-xl gap-1.5"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    navigate("/login");
                  }}
                >
                  <LogIn className="w-4 h-4" />
                  Login
                </Button>
                <Button
                  className="w-full rounded-xl gap-1.5"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    navigate("/signup");
                  }}
                >
                  <UserPlus className="w-4 h-4" />
                  Sign Up
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </header>
  );
}

export default Navbar;
