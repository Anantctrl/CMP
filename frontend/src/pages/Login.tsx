import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { motion } from "framer-motion";
import { Mail, Lock, Shield, CheckCircle2Icon } from "lucide-react";
import { useState, type FormEvent } from "react";
import type LoginData from "@/models/LoginData";
import toast from "react-hot-toast";
import { Navigate, useNavigate, NavLink } from "react-router";
import { Alert, AlertTitle } from "@/components/ui/alert";
import { Spinner } from "@/components/ui/spinner";
import useAuth from "@/auth/store";

const getErrorMessage = (error: unknown): string => {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;

    if (typeof response === "object" && response !== null && "data" in response) {
      const data = response.data;

      if (typeof data === "object" && data !== null && "message" in data) {
        return String(data.message);
      }
    }
  }

  return error instanceof Error ? error.message : "Unable to sign in";
};

function Login() {
  const [loginData, setLoginData] = useState<LoginData>({
    email: "",
    password: "",
  });

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();
  const login = useAuth((state) => state.login);
  const isAuthenticated = useAuth(
    (state) => Boolean(state.accessToken && state.authStatus)
  );

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setLoginData({
      ...loginData,
      [event.target.name]: event.target.value,
    });
  };

  const handleFormSubmit = async (event: FormEvent) => {
    event.preventDefault();

    if (loginData.email.trim() === "") {
      toast.error("Input required !");
      return;
    }
    if (loginData.password.trim() === "") {
      toast.error("Input required !");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await login(loginData);
      toast.success("Login success");
      navigate("/dashboard");
    } catch (err: unknown) {
      toast.error("Error !!");
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center bg-background text-foreground px-4 py-8 sm:py-12">
      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md mx-auto"
      >
        <Card className="bg-card/80 backdrop-blur-xl border-border shadow-xl rounded-2xl p-4 sm:p-6">
          <CardContent className="p-2 sm:p-4 space-y-6">
            {/* Header */}
            <div className="text-center space-y-2">
              <div className="mx-auto w-12 h-12 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary mb-2 shadow-sm">
                <Shield className="w-6 h-6" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
                Welcome Back
              </h1>
              <p className="text-xs sm:text-sm text-muted-foreground">
                Sign in to your CopMap tactical command session
              </p>
            </div>

            {/* Error banner */}
            {error && (
              <Alert variant="destructive" className="rounded-xl py-3">
                <CheckCircle2Icon className="w-4 h-4" />
                <AlertTitle className="text-xs sm:text-sm font-medium">{error}</AlertTitle>
              </Alert>
            )}

            {/* Form */}
            <form onSubmit={handleFormSubmit} className="space-y-4 sm:space-y-5">
              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="email" className="text-xs sm:text-sm font-semibold">
                  Official Email
                </Label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="officer@police.gov"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="email"
                    autoComplete="email"
                    value={loginData.email}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              {/* Password */}
              <div className="space-y-1.5">
                <Label htmlFor="password" className="text-xs sm:text-sm font-semibold">
                  Password
                </Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="••••••••"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="password"
                    autoComplete="current-password"
                    value={loginData.password}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              <Button
                disabled={loading}
                className="w-full h-11 sm:h-12 cursor-pointer rounded-xl text-sm sm:text-base font-semibold shadow-md shadow-primary/20 mt-2"
              >
                {loading ? (
                  <>
                    <Spinner className="mr-2" />
                    Authenticating...
                  </>
                ) : (
                  "Sign In"
                )}
              </Button>
            </form>

            <div className="text-center pt-2 border-t border-border/60">
              <p className="text-xs sm:text-sm text-muted-foreground">
                Don't have an account yet?{" "}
                <NavLink to="/signup" className="text-primary font-semibold hover:underline">
                  Register here
                </NavLink>
              </p>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}

export default Login;
