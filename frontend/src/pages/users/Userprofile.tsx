import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { motion } from "framer-motion";
import { ShieldCheck, Info } from "lucide-react";
import useAuth from "@/auth/store";

function Userprofile() {
  const user = useAuth((state) => state.user);

  return (
    <div className="px-4 py-6 sm:p-6 md:p-8 max-w-4xl mx-auto space-y-6 sm:space-y-8 select-none">
      {/* Heading */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center space-y-1"
      >
        <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
          Officer Profile
        </h1>
        <p className="text-xs sm:text-sm text-muted-foreground">
          Departmental operational clearance and credentials
        </p>
      </motion.div>

      {/* Profile Card */}
      <Card className="rounded-2xl shadow-md border-border/80 bg-card/80 backdrop-blur-xl">
        <CardHeader className="p-4 sm:p-6 pb-2 sm:pb-4 border-b border-border/60">
          <CardTitle className="text-lg sm:text-xl font-bold flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
            <span>Identity & Operational Clearance</span>
            <Badge variant="outline" className="gap-1 font-mono text-xs py-1">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" />
              Verified Personnel
            </Badge>
          </CardTitle>
        </CardHeader>

        <CardContent className="p-4 sm:p-6 space-y-6">
          {/* Avatar */}
          <div className="flex flex-col items-center gap-2 pt-2">
            <Avatar className="w-20 h-20 sm:w-24 sm:h-24 border-2 border-primary/40 shadow-md">
              <AvatarImage src={user?.image || "https://api.dicebear.com/7.x/thumbs/svg?seed=user"} />
              <AvatarFallback className="bg-primary/10 text-primary font-bold text-xl sm:text-2xl">
                {user?.name?.charAt(0).toUpperCase() ?? "O"}
              </AvatarFallback>
            </Avatar>
            <p className="text-[11px] sm:text-xs text-muted-foreground font-medium">Department Registered Identity</p>
          </div>

          {/* User Details Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-6">
            {/* Full Name */}
            <div className="space-y-1.5">
              <Label id="name-label" className="text-xs sm:text-sm font-semibold">Full Name</Label>
              <div
                aria-labelledby="name-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-sm font-medium"
              >
                {user?.name ?? ""}
              </div>
            </div>

            {/* Email */}
            <div className="space-y-1.5">
              <Label id="email-label" className="text-xs sm:text-sm font-semibold">Email ID</Label>
              <div
                aria-labelledby="email-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-sm font-mono truncate"
              >
                {user?.email ?? ""}
              </div>
            </div>

            {/* Police Post / Designation */}
            <div className="space-y-1.5">
              <Label id="policePost-label" className="text-xs sm:text-sm font-semibold">Police Post / Designation</Label>
              <div
                aria-labelledby="policePost-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-sm font-medium"
              >
                {user?.policePost ?? "Unassigned"}
              </div>
            </div>

            {/* Application Role */}
            <div className="space-y-1.5">
              <Label id="role-label" className="text-xs sm:text-sm font-semibold">Command Role</Label>
              <div
                aria-labelledby="role-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-sm font-bold text-primary"
              >
                {user?.role?.name ?? "Field Member"}
              </div>
            </div>

            {/* Provider */}
            <div className="space-y-1.5">
              <Label id="provider-label" className="text-xs sm:text-sm font-semibold">Auth Provider</Label>
              <div
                aria-labelledby="provider-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-sm uppercase font-mono text-xs"
              >
                {user?.provider ?? "LOCAL"}
              </div>
            </div>

            {/* Officer System ID */}
            <div className="space-y-1.5">
              <Label id="id-label" className="text-xs sm:text-sm font-semibold">System Officer ID</Label>
              <div
                aria-labelledby="id-label"
                className="flex h-11 w-full min-w-0 items-center rounded-xl border border-input bg-muted/40 px-3.5 py-1 text-xs font-mono text-muted-foreground truncate"
              >
                {user?.id ?? "Pending"}
              </div>
            </div>
          </div>

          {/* Departmental Policy Notice */}
          <div className="flex items-start gap-3 p-3.5 sm:p-4 rounded-xl bg-muted/50 border border-border/80 text-xs text-muted-foreground leading-relaxed">
            <Info className="w-4 h-4 text-primary shrink-0 mt-0.5" />
            <p>
              Officer identities and designations are managed under department security policy. To request role alterations or unit transfers, contact your divisional supervisor.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export default Userprofile;
