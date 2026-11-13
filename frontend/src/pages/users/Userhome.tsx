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
    <div className="min-h-screen bg-background text-foreground p-6 max-w-7xl mx-auto space-y-8">
      {/* Page Title */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <div className="flex items-center gap-3">
            <h1 className="text-4xl font-bold tracking-tight">Tactical Operations HQ</h1>
            {isSupervisor && (
              <Badge variant="destructive" className="font-semibold">
                SUPERVISOR
              </Badge>
            )}
          </div>
          <p className="text-muted-foreground mt-1">
            Welcome back, Officer {user?.name || "Member"}. Police Post: {user?.policePost || "Central Command"}.
          </p>
        </motion.div>

        <Button onClick={() => navigate("/dashboard/teams")} className="gap-2 shadow-sm">
          <Shield className="w-4 h-4" />
          Access Tactical Teams
          <ArrowRight className="w-4 h-4" />
        </Button>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {[
          {
            title: "Assigned Post",
            value: user?.policePost || "HQ Dispatch",
            icon: <UserCheck className="w-6 h-6 text-primary" />,
          },
          {
            title: "Security Clearance",
            value: isSupervisor ? "Level 1 (Full)" : "Level 2 (Tactical)",
            icon: <ShieldCheck className="w-6 h-6 text-primary" />,
          },
          {
            title: "Telemetry Stream",
            value: "20s Interval",
            icon: <Radio className="w-6 h-6 text-emerald-500 animate-pulse" />,
          },
          {
            title: "Active Unit State",
            value: "Ready",
            icon: <Activity className="w-6 h-6 text-primary" />,
          },
        ].map((stat, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: i * 0.08 }}
          >
            <Card className="bg-card/70 backdrop-blur-lg border-border rounded-2xl shadow-sm">
              <CardContent className="p-6 flex items-center gap-4">
                <div className="p-3 bg-muted rounded-xl">{stat.icon}</div>
                <div>
                  <p className="text-muted-foreground text-xs uppercase tracking-wider font-semibold">{stat.title}</p>
                  <h3 className="text-xl font-bold">{stat.value}</h3>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>

      {/* Tactical Banner */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.2 }}
      >
        <Card className="bg-gradient-to-r from-primary/10 via-card to-background border-primary/20 rounded-2xl p-6 shadow-sm">
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Users className="w-6 h-6 text-primary" />
                <h2 className="text-2xl font-bold">Team Command & Deployments</h2>
              </div>
              <p className="text-muted-foreground text-sm max-w-2xl">
                Dispatch unit tasks, manage officer assignments, coordinate multi-team operations (Bandobast, Patrolling, Nakabandi), and monitor real-time officer telemetry updates.
              </p>
            </div>
            <Button
              size="lg"
              onClick={() => navigate("/dashboard/teams")}
              className="gap-2 shrink-0"
            >
              Open Tactical Dashboard
              <ArrowRight className="w-4 h-4" />
            </Button>
          </div>
        </Card>
      </motion.div>

      {/* Recent System Activity */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.6, delay: 0.3 }}
      >
        <Card className="bg-card/70 backdrop-blur-lg border-border rounded-2xl shadow-sm">
          <CardContent className="p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
              <Activity className="w-5 h-5 text-primary" /> System Readiness Status
            </h2>
            <ul className="space-y-3 text-sm text-muted-foreground">
              <li className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                Authentication Gateway operational (MySQL dual-datasource connected)
              </li>
              <li className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                Operational Postgres telemetry & team engine online
              </li>
              <li className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                Auto-provisioned Officer Profile identity linked
              </li>
              <li className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                Real-time 20-second GPS signal freshness monitoring active
              </li>
            </ul>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}

export default Userhome;
