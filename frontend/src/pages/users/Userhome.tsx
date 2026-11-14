import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { motion } from "framer-motion";
import { ShieldCheck, Activity, Shield, Users, Radio, ArrowRight, UserCheck } from "lucide-react";
import useAuth from "@/auth/store";
import { useNavigate } from "react-router";

function Userhome() {
  const user = useAuth((state) => state.user);
  const navigate = useNavigate();

  const isSupervisor = user?.role?.name?.toLowerCase() === "supervisor";

  return (
    <div className="min-h-screen bg-background text-foreground px-4 py-6 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6 sm:space-y-8">
      {/* Page Title & Top CTA */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-border/60 pb-5 sm:pb-6">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="space-y-1"
        >
          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
            <h1 className="text-2xl sm:text-3xl md:text-4xl font-extrabold tracking-tight">
              Tactical Operations HQ
            </h1>
            {isSupervisor && (
              <Badge variant="destructive" className="font-bold text-xs uppercase px-2 py-0.5">
                SUPERVISOR
              </Badge>
            )}
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground">
            Welcome back, <span className="font-semibold text-foreground">{user?.name || "Officer"}</span>. Designated Post:{" "}
            <span className="font-medium text-foreground">{user?.policePost || "Central Command"}</span>.
          </p>
        </motion.div>

        <Button
          onClick={() => navigate("/dashboard/teams")}
          className="gap-2 shadow-md w-full sm:w-auto h-11 sm:h-10 text-sm font-semibold rounded-xl"
        >
          <Shield className="w-4 h-4" />
          Access Tactical Teams
          <ArrowRight className="w-4 h-4" />
        </Button>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
        {[
          {
            title: "Assigned Post",
            value: user?.policePost || "HQ Dispatch",
            icon: <UserCheck className="w-5 h-5 sm:w-6 sm:h-6 text-primary" />,
          },
          {
            title: "Security Clearance",
            value: isSupervisor ? "Level 1 (Full)" : "Level 2 (Tactical)",
            icon: <ShieldCheck className="w-5 h-5 sm:w-6 sm:h-6 text-primary" />,
          },
          {
            title: "Telemetry Stream",
            value: "20s Freshness",
            icon: <Radio className="w-5 h-5 sm:w-6 sm:h-6 text-emerald-500 animate-pulse" />,
          },
          {
            title: "Active Unit State",
            value: "Operational",
            icon: <Activity className="w-5 h-5 sm:w-6 sm:h-6 text-primary" />,
          },
        ].map((stat, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, delay: i * 0.06 }}
          >
            <Card className="bg-card/80 backdrop-blur-lg border-border rounded-2xl shadow-sm hover:border-primary/40 transition-colors">
              <CardContent className="p-4 sm:p-6 flex items-center gap-3 sm:gap-4">
                <div className="p-2.5 sm:p-3 bg-muted rounded-xl shrink-0">{stat.icon}</div>
                <div className="min-w-0">
                  <p className="text-muted-foreground text-[11px] sm:text-xs uppercase tracking-wider font-semibold truncate">
                    {stat.title}
                  </p>
                  <h3 className="text-lg sm:text-xl font-bold truncate text-foreground">{stat.value}</h3>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>

      {/* Tactical Banner */}
      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
      >
        <Card className="bg-gradient-to-br from-primary/10 via-card to-background border-primary/20 rounded-2xl p-5 sm:p-6 md:p-8 shadow-sm">
          <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-5 sm:gap-6">
            <div className="space-y-2 max-w-2xl">
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 sm:w-6 sm:h-6 text-primary shrink-0" />
                <h2 className="text-xl sm:text-2xl font-bold">Team Command & Deployments</h2>
              </div>
              <p className="text-muted-foreground text-xs sm:text-sm leading-relaxed">
                Dispatch unit tasks, manage officer assignments, coordinate multi-team operations (Bandobast, Patrolling, Nakabandi), and monitor real-time officer telemetry updates.
              </p>
            </div>
            <Button
              size="lg"
              onClick={() => navigate("/dashboard/teams")}
              className="gap-2 w-full lg:w-auto h-11 sm:h-12 rounded-xl font-semibold shadow-md shadow-primary/20 shrink-0"
            >
              Open Tactical Dashboard
              <ArrowRight className="w-4 h-4" />
            </Button>
          </div>
        </Card>
      </motion.div>

      {/* System Status Indicators */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4, delay: 0.3 }}
      >
        <Card className="bg-card/70 backdrop-blur-lg border-border rounded-2xl shadow-sm">
          <CardContent className="p-5 sm:p-6">
            <h2 className="text-lg sm:text-xl font-bold mb-4 flex items-center gap-2">
              <Activity className="w-5 h-5 text-primary" /> System Readiness Status
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs sm:text-sm text-muted-foreground">
              <div className="flex items-center gap-2.5 p-2 rounded-xl bg-muted/30">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
                <span>Authentication Gateway: Dual-Datasource Online</span>
              </div>
              <div className="flex items-center gap-2.5 p-2 rounded-xl bg-muted/30">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
                <span>Operational Postgres Telemetry & Team Engine Ready</span>
              </div>
              <div className="flex items-center gap-2.5 p-2 rounded-xl bg-muted/30">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
                <span>Officer Profile Biometric Identity Synchronized</span>
              </div>
              <div className="flex items-center gap-2.5 p-2 rounded-xl bg-muted/30">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
                <span>20-Second GPS Freshness Evaluation Active</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}

export default Userhome;
