import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { motion } from "framer-motion";
import { Mail, Lock, User, BadgeCheck, ShieldAlert, Shield } from "lucide-react";
import { useState, type ChangeEvent, type FormEvent } from "react";
import toast from "react-hot-toast";
import type RegisterData from "@/models/RegisterData";
import { registerUser } from "@/services/AuthService";
import { useNavigate, NavLink } from "react-router";

function Signup() {
  const [data, setData] = useState<RegisterData>({
    name: "",
    email: "",
    password: "",
    policePost: "",
    supervisor: false,
  });

  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    setData((value) => ({
      ...value,
      [event.target.name]: event.target.value,
    }));
  };

  const handleCheckboxChange = (event: ChangeEvent<HTMLInputElement>) => {
    setData((value) => ({
      ...value,
      supervisor: event.target.checked,
    }));
  };

  const handleFormSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (data.name.trim() === "") {
      toast.error("Name is required !");
      return;
    }

    if (data.email.trim() === "") {
      toast.error("Email is required !");
      return;
    }

    if (data.password.trim() === "") {
      toast.error("Password is required !");
      return;
    }

    if (data.policePost.trim() === "") {
      toast.error("Designation is required !");
      return;
    }

    try {
      setLoading(true);
      await registerUser(data);
      toast.success(
        data.supervisor
          ? "Supervisor account registered successfully!"
          : "User registered successfully!"
      );
      setData({
        name: "",
        email: "",
        password: "",
        policePost: "",
        supervisor: false,
      });
      navigate("/login");
    } catch {
      toast.error("Error in registering the user...");
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
                Create Account
              </h1>
              <p className="text-xs sm:text-sm text-muted-foreground">
                Join the tactical operational command roster
              </p>
            </div>

            {/* Form */}
            <form onSubmit={handleFormSubmit} className="space-y-4">
              {/* Name */}
              <div className="space-y-1.5">
                <Label htmlFor="name" className="text-xs sm:text-sm font-semibold">Full Name</Label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="name"
                    type="text"
                    placeholder="Inspector John Stone"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="name"
                    value={data.name}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="email" className="text-xs sm:text-sm font-semibold">Official Email</Label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="john.stone@police.gov"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="email"
                    autoComplete="email"
                    value={data.email}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              {/* Password */}
              <div className="space-y-1.5">
                <Label htmlFor="password" className="text-xs sm:text-sm font-semibold">Password</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="••••••••"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="password"
                    autoComplete="new-password"
                    value={data.password}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              {/* Designation */}
              <div className="space-y-1.5">
                <Label htmlFor="policePost" className="text-xs sm:text-sm font-semibold">Police Post / Designation</Label>
                <div className="relative">
                  <BadgeCheck className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="policePost"
                    type="text"
                    placeholder="e.g. Sub Inspector, Inspector, DGP"
                    className="pl-9 h-11 rounded-xl text-sm"
                    name="policePost"
                    value={data.policePost}
                    onChange={handleInputChange}
                    required
                  />
                </div>
              </div>

              {/* Supervisor Privilege Checkbox */}
              <label htmlFor="supervisor" className="flex items-start space-x-3 p-3 bg-muted/50 rounded-xl border border-border/80 cursor-pointer select-none">
                <input
                  id="supervisor"
                  type="checkbox"
                  name="supervisor"
                  checked={data.supervisor || false}
                  onChange={handleCheckboxChange}
                  className="w-4 h-4 mt-0.5 rounded border-border text-primary focus:ring-primary cursor-pointer shrink-0"
                />
                <div className="flex flex-col">
                  <span className="text-xs sm:text-sm font-bold flex items-center gap-1.5 text-foreground cursor-pointer">
                    <ShieldAlert className="w-3.5 h-3.5 text-primary shrink-0" />
                    Register as Supervisor
                  </span>
                  <span className="text-[11px] sm:text-xs text-muted-foreground leading-snug mt-0.5">
                    Grants command-level authority, team creation, and operation approvals
                  </span>
                </div>
              </label>

              <Button
                type="submit"
                disabled={loading}
                className="w-full h-11 sm:h-12 rounded-xl text-sm sm:text-base font-semibold shadow-md shadow-primary/20 mt-2"
              >
                {loading ? "Registering..." : "Create Account"}
              </Button>
            </form>

            <div className="text-center pt-2 border-t border-border/60">
              <p className="text-xs sm:text-sm text-muted-foreground">
                Already registered?{" "}
                <NavLink to="/login" className="text-primary font-semibold hover:underline">
                  Sign in here
                </NavLink>
              </p>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}

export default Signup;
