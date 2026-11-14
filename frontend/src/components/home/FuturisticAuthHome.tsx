import { useState } from "react"
import { useNavigate } from "react-router"
import { motion } from "framer-motion"
import {
  Shield,
  Radio,
  MapPin,
  Search,
  CheckCircle2,
  Play,
  FileDown,
  Database,
  Layers,
  ArrowRight,
  ChevronRight,
  Check
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import useAuth from "@/auth/store"
import toast from "react-hot-toast"

export default function FuturisticAuthHome() {
  const navigate = useNavigate()
  const isLoggedIn = useAuth((state) => Boolean(state.accessToken && state.authStatus))

  // --- STATE FOR INTERACTIVE SIMULATORS ---
  const [activeTab, setActiveTab] = useState<"radar" | "ops" | "lifecycle" | "redis">("radar")

  // Radar simulator state
  const [selectedUnit, setSelectedUnit] = useState<number>(0)
  const [isPinging, setIsPinging] = useState<boolean>(false)
  const [unitsData, setUnitsData] = useState([
    {
      id: "Alpha-1",
      officer: "Inspector Vikram Rane",
      post: "Wakad Police Station",
      role: "TEAM_LEAD",
      lat: 18.5982,
      lng: 73.7641,
      area: "Wakad Chowk & Flyover Beat",
      accuracy: 4,
      freshness: "FRESH",
      speed: "24 km/h",
      lastUpdate: "3s ago",
      battery: "94%"
    },
    {
      id: "Bravo-3",
      officer: "Sub-Inspector Anita Deshmukh",
      post: "Hinjewadi Phase 1",
      role: "FIELD_OPERATOR",
      lat: 18.5913,
      lng: 73.7389,
      area: "Hinjewadi IT Corridor Checkpoint",
      accuracy: 6,
      freshness: "FRESH",
      speed: "Stationary",
      lastUpdate: "11s ago",
      battery: "88%"
    },
    {
      id: "Delta-9",
      officer: "ASI Suresh Kadam",
      post: "Bavdhan Sector 4",
      role: "FIELD_OPERATOR",
      lat: 18.5120,
      lng: 73.7715,
      area: "Highway Entry Nakabandi Barricade",
      accuracy: 12,
      freshness: "DEGRADED",
      speed: "12 km/h",
      lastUpdate: "48s ago",
      battery: "62%"
    }
  ])

  // Operation Dispatcher State
  const [selectedOpType, setSelectedOpType] = useState<"BANDOBAST" | "NAKABANDI" | "PATROLLING">("BANDOBAST")

  const operationsData = {
    BANDOBAST: {
      title: "VIP Convoy & Public Assembly Security",
      type: "BANDOBAST",
      scope: "Multi-Team Perimeter & Crowd Control",
      teamsRequired: 4,
      targetLocation: "District Collectorate & Main Boulevard",
      status: "APPROVED / ACTIVE",
      manpower: "32 Officers Dispatched",
      points: [
        "Primary outer corridor barricading",
        "Roof-top observation & sniper post",
        "Rapid intervention squad on standby",
        "Continuous 20s telemetry verification"
      ]
    },
    NAKABANDI: {
      title: "Operation Night-Hawk Arterial Interception",
      type: "NAKABANDI",
      scope: "High-Alert Vehicle Checking & Spikes",
      teamsRequired: 2,
      targetLocation: "Wakad Expressway Toll Plaza",
      status: "ACTIVE EXECUTION",
      manpower: "16 Officers Dispatched",
      points: [
        "Two-lane funnel with tactical tyre deflators",
        "Breathalyzer & trunk frisking protocol",
        "Direct radio link with Police Control Room",
        "Automated license plate telemetry lookup"
      ]
    },
    PATROLLING: {
      title: "Beat Geofence Surveillance Sector 7",
      type: "PATROLLING",
      scope: "24-Hour Mobile Patrol & Hotspot Check",
      teamsRequired: 3,
      targetLocation: "Commercial Hub & Residential Beats",
      status: "ONGOING ROUTINE",
      manpower: "24 Officers Dispatched",
      points: [
        "Regular 30-minute landmark check-ins",
        "Live GPS tracking for signal freshness",
        "Night beat citizen grievance response",
        "Automated patrol route history logging"
      ]
    }
  }

  // Duty Lifecycle Simulator State
  const [dutyStep, setDutyStep] = useState<number>(1)
  const dutySteps = [
    {
      step: 1,
      title: "Task Dispatched",
      actor: "Senior Planner / Team Lead",
      desc: "Nakabandi Task created for Hinjewadi Phase 1 with specific lead orders.",
      status: "ASSIGNED",
      badgeClass: "bg-blue-500/20 text-blue-400 border-blue-500/30"
    },
    {
      step: 2,
      title: "Duty Initiated",
      actor: "Deployed Beat Officer",
      desc: "Officer acknowledges orders, arrives at designated post, and begins telemetry.",
      status: "IN_PROGRESS",
      badgeClass: "bg-amber-500/20 text-amber-400 border-amber-500/30"
    },
    {
      step: 3,
      title: "Field Report Submitted",
      actor: "Deployed Beat Officer",
      desc: "Officer files findings: '142 vehicles screened, 0 contraband found, perimeter secure.'",
      status: "SUBMITTED",
      badgeClass: "bg-purple-500/20 text-purple-400 border-purple-500/30"
    },
    {
      step: 4,
      title: "Command Verified & Signed",
      actor: "Team Lead / Supervisor",
      desc: "Tactical performance approved. Immutable audit log recorded and PDF signed.",
      status: "COMPLETED",
      badgeClass: "bg-emerald-500/20 text-emerald-400 border-emerald-500/30"
    }
  ]

  // Redis Officer Directory Search Simulator
  const [searchQuery, setSearchQuery] = useState("")
  const mockOfficerDirectory = [
    { name: "Inspector Vikram Rane", post: "Wakad Police Station", designation: "Inspector", role: "Team Lead", email: "v.rane@police.gov" },
    { name: "Sub-Inspector Anita Deshmukh", post: "Hinjewadi Phase 1", designation: "Sub-Inspector", role: "Control Operator", email: "a.deshmukh@police.gov" },
    { name: "ASI Suresh Kadam", post: "Wakad Police Station", designation: "Asst. Sub-Inspector", role: "Field Operator", email: "s.kadam@police.gov" },
    { name: "Sr. Inspector Ramesh Pawar", post: "Shivajinagar Division", designation: "Senior Inspector", role: "Senior Planner", email: "r.pawar@police.gov" },
    { name: "ACP Sanjay Gaikwad", post: "Central Headquarters", designation: "Assistant Commissioner", role: "Supervisor", email: "s.gaikwad@police.gov" },
    { name: "Head Constable Dilip Shinde", post: "Hinjewadi Phase 2", designation: "Head Constable", role: "Field Operator", email: "d.shinde@police.gov" }
  ]

  const filteredMockOfficers = mockOfficerDirectory.filter((o) => {
    if (!searchQuery) return true
    const q = searchQuery.toLowerCase()
    return (
      o.name.toLowerCase().includes(q) ||
      o.post.toLowerCase().includes(q) ||
      o.designation.toLowerCase().includes(q) ||
      o.role.toLowerCase().includes(q)
    )
  })

  // Role Hierarchy Explorer State
  const [selectedHierarchyRole, setSelectedHierarchyRole] = useState<string>("SUPERVISOR")
  const hierarchyRoles = [
    {
      id: "SUPERVISOR",
      name: "Division Supervisor",
      insignia: "Ashok Stambh / Star",
      badgeColor: "text-amber-400 bg-amber-500/10 border-amber-500/30",
      description: "Highest division-level authority. Authorized to approve cross-team joint operations, inspect system-wide audit trails, and oversee entire tactical forces.",
      powers: [
        "Approve or reject multi-team operations across units",
        "View immutable audit logs for all personnel and status changes",
        "Download full division tactical dossiers and officer service reports",
        "Direct emergency police post redeployments"
      ]
    },
    {
      id: "SENIOR_PLANNER",
      name: "Senior Tactical Planner",
      insignia: "Three Stars / Laurel",
      badgeColor: "text-blue-400 bg-blue-500/10 border-blue-500/30",
      description: "Strategic operational architect. Plans operations (Bandobast, Nakabandi, Patrolling), defines landmark locations, and scopes necessary manpower quotas.",
      powers: [
        "Establish tactical operations and specify checkpoints",
        "Dispatch squad-level tactical tasks to units",
        "Manage unit roster by adding or removing officers via Redis directory",
        "Generate comprehensive mission briefing PDF dossiers"
      ]
    },
    {
      id: "TEAM_LEAD",
      name: "Tactical Team Lead",
      insignia: "Two Stars",
      badgeColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/30",
      description: "Frontline tactical commander. Responsible for assigning deployed officers to specific checkpoint responsibilities and reviewing their field reports.",
      powers: [
        "Deploy officers to specific tasks with tailored orders",
        "Monitor live 20-second officer GPS freshness in real-time",
        "Review and endorse completed duty field reports",
        "Download authenticated unit operational dossiers"
      ]
    },
    {
      id: "CONTROL_OPERATOR",
      name: "Control Room Operator",
      insignia: "Radio & Radar Crest",
      badgeColor: "text-cyan-400 bg-cyan-500/10 border-cyan-500/30",
      description: "24/7 command center operator. Monitors real-time Leaflet tactical map tracking, identifies signal degradations, and coordinates dispatch communication.",
      powers: [
        "Continuously observe live officer GPS positions & coordinates",
        "Detect stale signals (>60s) or perimeter breaches",
        "Dispatch emergency broadcast coordinates to ground teams",
        "Download officer telemetry logs and location reports"
      ]
    },
    {
      id: "FIELD_OPERATOR",
      name: "Beat / Ground Officer",
      insignia: "Police Badge Shield",
      badgeColor: "text-indigo-400 bg-indigo-500/10 border-indigo-500/30",
      description: "On-the-ground law enforcement personnel executing assignments. Streams live GPS telemetry, initiates duty orders, and files field findings.",
      powers: [
        "View personal assigned tasks, duty landmark, and specific orders",
        "Start duty with automated GPS position tagging",
        "File comprehensive field completion reports with incident notes",
        "Download personal verified service & task report"
      ]
    }
  ]

  // Handler for simulated GPS Ping
  const handleSimulateGpsPing = () => {
    setIsPinging(true)
    setTimeout(() => {
      setUnitsData((prev) =>
        prev.map((unit, idx) => {
          if (idx === selectedUnit) {
            return {
              ...unit,
              freshness: "FRESH",
              lastUpdate: "Just now",
              lat: Number((unit.lat + (Math.random() - 0.5) * 0.0008).toFixed(4)),
              lng: Number((unit.lng + (Math.random() - 0.5) * 0.0008).toFixed(4))
            }
          }
          return unit
        })
      )
      setIsPinging(false)
      toast.success(`📡 20-sec GPS fix received: ${unitsData[selectedUnit].id} (${unitsData[selectedUnit].officer})`, {
        icon: "🛰️",
        style: { borderRadius: "12px", background: "#0f172a", color: "#f8fafc" }
      })
    }, 600)
  }

  return (
    <div className="min-h-screen bg-background text-foreground transition-colors overflow-x-hidden select-none">
      {/* --- HERO SECTION --- */}
      <section className="relative pt-12 pb-16 sm:pt-20 sm:pb-24 px-4 sm:px-6 max-w-7xl mx-auto flex flex-col items-center text-center">
        {/* Police Emergency Flashing Indicator */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-bold tracking-wider uppercase mb-6 shadow-sm"
        >
          <span className="flex h-2.5 w-2.5 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-600"></span>
          </span>
          <Shield className="w-3.5 h-3.5 text-primary" />
          <span>POLICE TACTICAL COMMAND &bull; COP-MAP SYSTEM ACTIVE</span>
        </motion.div>

        {/* Main Title */}
        <motion.h1
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="text-3xl sm:text-5xl md:text-6xl lg:text-7xl font-extrabold tracking-tight max-w-5xl leading-tight"
        >
          Unified Police Operations,{" "}
          <span className="bg-gradient-to-r from-primary via-primary/80 to-blue-500 bg-clip-text text-transparent">
            Bandobast &amp; Live Telemetry
          </span>
        </motion.h1>

        {/* Subtitle */}
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2, duration: 0.6 }}
          className="mt-4 sm:mt-6 max-w-3xl text-base sm:text-lg md:text-xl text-muted-foreground px-2"
        >
          The mission-critical law enforcement dispatch platform. Stream live 20-second officer GPS updates,
          orchestrate multi-team Nakabandi and Bandobast perimeters, enforce command hierarchies, and export
          instant court-admissible tactical dossiers.
        </motion.p>

        {/* Hero Action Buttons */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.35, duration: 0.6 }}
          className="mt-8 sm:mt-10 flex flex-col sm:flex-row gap-3 sm:gap-4 w-full sm:w-auto max-w-sm sm:max-w-none justify-center px-4"
        >
          <Button
            size="lg"
            className="rounded-2xl text-base sm:text-lg px-7 h-12 sm:h-14 cursor-pointer font-bold gap-2 shadow-lg shadow-primary/20 bg-primary hover:bg-primary/90 text-primary-foreground"
            onClick={() => navigate(isLoggedIn ? "/dashboard" : "/login")}
          >
            <Shield className="w-5 h-5" />
            {isLoggedIn ? "Enter Command Center" : "Officer Sign In"}
            <ArrowRight className="w-4 h-4 ml-1" />
          </Button>

          <Button
            size="lg"
            variant="outline"
            className="rounded-2xl text-base sm:text-lg px-7 h-12 sm:h-14 border-border cursor-pointer font-semibold gap-2 hover:bg-muted"
            onClick={() => {
              const el = document.getElementById("interactive-simulator")
              if (el) el.scrollIntoView({ behavior: "smooth" })
            }}
          >
            <Radio className="w-4 h-4 text-emerald-500 animate-pulse" />
            Test Live Simulator
          </Button>

          {!isLoggedIn && (
            <Button
              size="lg"
              variant="ghost"
              className="rounded-2xl text-base sm:text-lg px-5 h-12 sm:h-14 cursor-pointer font-medium text-muted-foreground hover:text-foreground"
              onClick={() => navigate("/signup")}
            >
              Register Officer ID
            </Button>
          )}
        </motion.div>

        {/* Tactical Key Metrics Ribbon */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5, duration: 0.6 }}
          className="mt-12 sm:mt-16 w-full grid grid-cols-2 md:grid-cols-4 gap-3 sm:gap-4 max-w-5xl"
        >
          {[
            { label: "GPS REFRESH", val: "20s Automated", sub: "Strict Freshness Grading", icon: <Radio className="w-4 h-4 text-emerald-400" /> },
            { label: "DUTY ROLES", val: "5-Tier Command", sub: "Supervisor to Field Beat", icon: <Shield className="w-4 h-4 text-blue-400" /> },
            { label: "REDIS DIRECTORY", val: "< 10ms Cached", sub: "Search by Police Post", icon: <Database className="w-4 h-4 text-cyan-400" /> },
            { label: "DOSSIER EXPORT", val: "1-Click PDF", sub: "Court-Ready Audit Trail", icon: <FileDown className="w-4 h-4 text-amber-400" /> }
          ].map((stat, i) => (
            <div key={i} className="p-3.5 sm:p-4 rounded-2xl bg-card/80 border border-border shadow-xs text-left">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground font-semibold mb-1">
                {stat.icon}
                <span>{stat.label}</span>
              </div>
              <div className="text-lg sm:text-xl font-bold text-foreground">{stat.val}</div>
              <div className="text-[11px] text-muted-foreground mt-0.5 truncate">{stat.sub}</div>
            </div>
          ))}
        </motion.div>
      </section>

      {/* --- INTERACTIVE CENTERPIECE: TACTICAL COMMAND CONSOLE --- */}
      <section id="interactive-simulator" className="py-12 sm:py-20 px-4 sm:px-6 max-w-7xl mx-auto">
        <div className="text-center mb-8 sm:mb-12">
          <Badge variant="outline" className="mb-2 text-xs font-semibold px-3 py-1 text-primary border-primary/30">
            INTERACTIVE EXPERIENCE
          </Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
            Live Tactical Command Simulator
          </h2>
          <p className="text-muted-foreground text-sm sm:text-base max-w-2xl mx-auto mt-2">
            Interact with the core CopMap police operations modules in real-time. Test telemetry feeds,
            dispatch bandobast units, simulate task lifecycle verification, and test the sub-10ms officer search.
          </p>
        </div>

        {/* Tactical Console Container */}
        <div className="bg-card border border-border rounded-3xl shadow-xl overflow-hidden">
          {/* Console Top Navigation Bar */}
          <div className="bg-muted/40 border-b border-border p-2 sm:p-3 flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-1.5 sm:gap-2">
              <span className="w-3 h-3 rounded-full bg-rose-500 inline-block" />
              <span className="w-3 h-3 rounded-full bg-amber-500 inline-block" />
              <span className="w-3 h-3 rounded-full bg-emerald-500 inline-block" />
              <span className="text-xs font-mono font-bold text-muted-foreground ml-2 hidden sm:inline">
                COPMAP-TACTICAL-SHELL v2.4 // SECURE
              </span>
            </div>

            {/* Module Switcher Buttons */}
            <div className="flex items-center gap-1 overflow-x-auto max-w-full">
              {[
                { id: "radar", label: "🛰️ Live GPS Radar" },
                { id: "ops", label: "🛡️ Bandobast Dispatch" },
                { id: "lifecycle", label: "⚡ Duty Lifecycle" },
                { id: "redis", label: "⚡ Redis Directory" }
              ].map((m) => (
                <button
                  key={m.id}
                  onClick={() => setActiveTab(m.id as any)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                    activeTab === m.id
                      ? "bg-primary text-primary-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>
          </div>

          {/* Module 1: Live GPS Radar & Telemetry Feed */}
          {activeTab === "radar" && (
            <div className="p-4 sm:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Simulated Radar Map Canvas */}
              <div className="lg:col-span-7 bg-slate-950 rounded-2xl p-4 sm:p-6 text-slate-100 flex flex-col justify-between relative overflow-hidden border border-slate-800 min-h-[340px] sm:min-h-[400px]">
                {/* Tactical Radar Grid Overlay */}
                <div className="absolute inset-0 bg-[radial-gradient(#334155_1px,transparent_1px)] [background-size:24px_24px] opacity-40" />

                {/* Concentric Radar Rings */}
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <div className="w-72 h-72 rounded-full border border-primary/20 animate-pulse" />
                  <div className="w-48 h-48 rounded-full border border-primary/30" />
                  <div className="w-24 h-24 rounded-full border border-primary/40" />
                  {/* Radar sweep line */}
                  <div className={`absolute w-36 h-36 border-t-2 border-r-2 border-primary/60 rounded-tr-full origin-bottom-left ${isPinging ? "animate-spin" : ""}`} />
                </div>

                {/* Radar Top Header */}
                <div className="relative z-10 flex justify-between items-start flex-wrap gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping" />
                      <span className="text-xs font-mono font-bold tracking-wider text-emerald-400 uppercase">
                        Real-time Tactical Radar Feed
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 mt-0.5">Streaming 20-second officer GPS updates</p>
                  </div>
                  <Badge variant="outline" className="border-slate-700 bg-slate-900 text-slate-300 text-[10px] font-mono">
                    SECTOR: PUNE-PCMC METRO
                  </Badge>
                </div>

                {/* Radar Units Visual Positioning */}
                <div className="relative z-10 my-auto py-8 flex flex-col gap-3">
                  {unitsData.map((unit, idx) => {
                    const isSelected = selectedUnit === idx
                    return (
                      <div
                        key={unit.id}
                        onClick={() => setSelectedUnit(idx)}
                        className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center justify-between text-xs backdrop-blur-md ${
                          isSelected
                            ? "bg-primary/20 border-primary text-white shadow-lg"
                            : "bg-slate-900/80 border-slate-800 text-slate-300 hover:bg-slate-800/80"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <div className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs ${
                            isSelected ? "bg-primary text-white" : "bg-slate-800 text-slate-400"
                          }`}>
                            {unit.id.split("-")[1]}
                          </div>
                          <div>
                            <div className="font-bold flex items-center gap-2">
                              <span>{unit.id} &bull; {unit.officer}</span>
                              <Badge
                                variant={unit.freshness === "FRESH" ? "success" : "warning"}
                                className="text-[9px] py-0 px-1"
                              >
                                {unit.freshness}
                              </Badge>
                            </div>
                            <div className="text-[11px] text-slate-400 flex items-center gap-2 mt-0.5">
                              <MapPin className="w-3 h-3 text-rose-400" />
                              <span>{unit.area}</span>
                            </div>
                          </div>
                        </div>

                        <div className="text-right font-mono text-[11px] text-slate-400 hidden sm:block">
                          <div>{unit.lat}° N, {unit.lng}° E</div>
                          <div className="text-emerald-400 text-[10px]">{unit.lastUpdate}</div>
                        </div>
                      </div>
                    )
                  })}
                </div>

                {/* Radar Bottom Controls */}
                <div className="relative z-10 pt-3 border-t border-slate-800 flex justify-between items-center flex-wrap gap-2">
                  <div className="text-[11px] text-slate-400 font-mono">
                    Fix Accuracy: <span className="text-emerald-400 font-bold">&plusmn;{unitsData[selectedUnit].accuracy}m</span> | Battery: {unitsData[selectedUnit].battery}
                  </div>
                  <Button
                    size="sm"
                    onClick={handleSimulateGpsPing}
                    disabled={isPinging}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs gap-1.5 h-8 px-3 rounded-lg shadow cursor-pointer"
                  >
                    <Radio className={`w-3.5 h-3.5 ${isPinging ? "animate-spin" : "animate-pulse"}`} />
                    {isPinging ? "Broadcasting Ping..." : "Simulate GPS Ping"}
                  </Button>
                </div>
              </div>

              {/* Right Column: Selected Unit Detail Dossier */}
              <div className="lg:col-span-5 flex flex-col justify-between space-y-4">
                <div className="p-4 sm:p-5 rounded-2xl bg-muted/40 border border-border space-y-4">
                  <div className="flex items-center justify-between border-b border-border pb-3">
                    <div>
                      <span className="text-xs font-bold text-primary uppercase tracking-wide">Selected Patrol Unit</span>
                      <h3 className="text-xl font-bold">{unitsData[selectedUnit].officer}</h3>
                      <p className="text-xs text-muted-foreground">{unitsData[selectedUnit].post}</p>
                    </div>
                    <div className="w-10 h-10 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary font-bold">
                      <Shield className="w-5 h-5" />
                    </div>
                  </div>

                  {/* Telemetry Metrics Grid */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div className="p-2.5 rounded-xl bg-card border border-border">
                      <div className="text-muted-foreground text-[10px] uppercase font-bold">Assigned Sector</div>
                      <div className="font-semibold text-foreground mt-0.5 line-clamp-1">{unitsData[selectedUnit].area}</div>
                    </div>
                    <div className="p-2.5 rounded-xl bg-card border border-border">
                      <div className="text-muted-foreground text-[10px] uppercase font-bold">Movement Speed</div>
                      <div className="font-semibold text-foreground mt-0.5">{unitsData[selectedUnit].speed}</div>
                    </div>
                    <div className="p-2.5 rounded-xl bg-card border border-border">
                      <div className="text-muted-foreground text-[10px] uppercase font-bold">Freshness Level</div>
                      <div className="font-semibold text-emerald-500 mt-0.5 flex items-center gap-1">
                        <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                        {unitsData[selectedUnit].freshness} ({unitsData[selectedUnit].lastUpdate})
                      </div>
                    </div>
                    <div className="p-2.5 rounded-xl bg-card border border-border">
                      <div className="text-muted-foreground text-[10px] uppercase font-bold">Unit Role</div>
                      <div className="font-semibold text-foreground mt-0.5">{unitsData[selectedUnit].role.replace("_", " ")}</div>
                    </div>
                  </div>

                  <div className="text-xs text-muted-foreground bg-card/60 p-3 rounded-xl border border-border flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                    <span>
                      20-second background heartbeat keeps control room informed. Automatic reverse geocoding pinpoints landmark areas instantly.
                    </span>
                  </div>
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    className="w-full text-xs font-semibold gap-1.5 h-10 rounded-xl"
                    onClick={() => {
                      toast.success(`Exporting live officer dossier preview for ${unitsData[selectedUnit].officer}...`, { icon: "📄" })
                    }}
                  >
                    <FileDown className="w-4 h-4 text-primary" />
                    Export Officer Dossier (PDF)
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Module 2: Bandobast, Nakabandi & Patrolling Operations Dispatcher */}
          {activeTab === "ops" && (
            <div className="p-4 sm:p-8 space-y-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 border-b border-border pb-4">
                <div>
                  <h3 className="text-xl font-bold">Tactical Operation Dispatch Planner</h3>
                  <p className="text-xs text-muted-foreground">Select operation type to view checkpoint protocols and multi-team coordination.</p>
                </div>

                <div className="flex gap-1.5 bg-muted p-1 rounded-xl">
                  {(["BANDOBAST", "NAKABANDI", "PATROLLING"] as const).map((type) => (
                    <button
                      key={type}
                      onClick={() => setSelectedOpType(type)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        selectedOpType === type
                          ? "bg-card text-foreground shadow-xs border border-border"
                          : "text-muted-foreground hover:text-foreground"
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              {/* Active Operation Details Showcase */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="md:col-span-2 space-y-4">
                  <div className="p-5 rounded-2xl bg-card border border-border space-y-3">
                    <div className="flex justify-between items-start flex-wrap gap-2">
                      <div>
                        <Badge variant="outline" className="text-xs mb-1 text-primary border-primary/30">
                          {operationsData[selectedOpType].type} PROTOCOL
                        </Badge>
                        <h4 className="text-xl font-extrabold">{operationsData[selectedOpType].title}</h4>
                      </div>
                      <Badge variant="success" className="text-xs">
                        {operationsData[selectedOpType].status}
                      </Badge>
                    </div>

                    <p className="text-sm text-muted-foreground">{operationsData[selectedOpType].scope}</p>

                    <div className="pt-2 border-t border-border grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                      <div>
                        <span className="text-muted-foreground font-semibold">Deployment Area:</span>
                        <p className="font-bold text-foreground mt-0.5">{operationsData[selectedOpType].targetLocation}</p>
                      </div>
                      <div>
                        <span className="text-muted-foreground font-semibold">Allocated Manpower:</span>
                        <p className="font-bold text-foreground mt-0.5">{operationsData[selectedOpType].manpower} across {operationsData[selectedOpType].teamsRequired} Teams</p>
                      </div>
                    </div>
                  </div>

                  {/* Standard Operating Procedure Checklist */}
                  <div className="p-5 rounded-2xl bg-muted/40 border border-border space-y-2">
                    <div className="text-xs font-bold text-foreground uppercase tracking-wider mb-2">Tactical Execution Checklist</div>
                    {operationsData[selectedOpType].points.map((pt, i) => (
                      <div key={i} className="flex items-center gap-2.5 text-xs text-muted-foreground">
                        <Check className="w-4 h-4 text-emerald-500 shrink-0" />
                        <span>{pt}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right Column: Supervisor Authorization & Joint Operations */}
                <div className="p-5 rounded-2xl bg-card border border-border flex flex-col justify-between space-y-4">
                  <div className="space-y-3">
                    <div className="flex items-center gap-2 text-xs font-bold text-primary uppercase">
                      <Shield className="w-4 h-4" />
                      Cross-Team Approvals
                    </div>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      Joint operations requiring manpower from external police stations are validated through formal Supervisor authorization requests before field execution.
                    </p>
                    <div className="p-3 rounded-xl bg-muted/60 border border-border text-xs space-y-1.5">
                      <div className="font-semibold text-foreground">Authorization State: APPROVED</div>
                      <div className="text-muted-foreground text-[11px]">Reviewed by: ACP Division Supervisor</div>
                      <div className="text-[10px] text-emerald-500 font-mono font-medium">Digital Hash Verified &bull; Logged</div>
                    </div>
                  </div>

                  <Button
                    size="sm"
                    className="w-full text-xs font-bold rounded-xl cursor-pointer"
                    onClick={() => navigate(isLoggedIn ? "/dashboard/teams" : "/login")}
                  >
                    Manage Unit Operations
                    <ArrowRight className="w-3.5 h-3.5 ml-1" />
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Module 3: Tactical Duty Lifecycle (Step-Through Simulator) */}
          {activeTab === "lifecycle" && (
            <div className="p-4 sm:p-8 space-y-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 border-b border-border pb-4">
                <div>
                  <h3 className="text-xl font-bold">End-to-End Duty Lifecycle</h3>
                  <p className="text-xs text-muted-foreground">Step through a tactical police task from Senior Planner dispatch to Supervisor audit verification.</p>
                </div>

                <Button
                  size="sm"
                  onClick={() => setDutyStep((s) => (s < 4 ? s + 1 : 1))}
                  className="text-xs font-bold gap-1.5 rounded-xl cursor-pointer"
                >
                  <Play className="w-3.5 h-3.5" />
                  {dutyStep < 4 ? `Simulate Next Step (${dutyStep + 1}/4)` : "Reset Simulator"}
                </Button>
              </div>

              {/* Step Progression Visualizer */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                {dutySteps.map((s) => {
                  const isActive = dutyStep === s.step
                  const isDone = dutyStep > s.step

                  return (
                    <div
                      key={s.step}
                      onClick={() => setDutyStep(s.step)}
                      className={`p-4 rounded-2xl border transition-all cursor-pointer flex flex-col justify-between ${
                        isActive
                          ? "bg-primary/10 border-primary shadow-md"
                          : isDone
                          ? "bg-muted/40 border-emerald-500/30 text-foreground"
                          : "bg-card border-border/70 opacity-60 hover:opacity-100"
                      }`}
                    >
                      <div className="space-y-2">
                        <div className="flex justify-between items-center">
                          <span className="text-[11px] font-mono font-bold text-muted-foreground">STEP 0{s.step}</span>
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${s.badgeClass}`}>
                            {s.status}
                          </span>
                        </div>
                        <h4 className="font-bold text-sm text-foreground">{s.title}</h4>
                        <p className="text-[11px] text-muted-foreground line-clamp-2">{s.desc}</p>
                      </div>

                      <div className="pt-3 mt-3 border-t border-border/50 text-[10px] text-muted-foreground flex items-center gap-1">
                        <Shield className="w-3 h-3 text-primary" />
                        <span>Actor: {s.actor}</span>
                      </div>
                    </div>
                  )
                })}
              </div>

              {/* Active Step Live Terminal Output */}
              <div className="p-4 rounded-2xl bg-slate-950 text-slate-200 border border-slate-800 font-mono text-xs space-y-2">
                <div className="flex justify-between items-center text-slate-400 text-[11px] border-b border-slate-800 pb-2">
                  <span>LIVE TASK LOG CONSOLE</span>
                  <span className="text-emerald-400">ACTIVE STEP: {dutySteps[dutyStep - 1].title.toUpperCase()}</span>
                </div>
                <div className="space-y-1 text-[11px]">
                  <p className="text-slate-400">&gt; Event Triggered: {dutySteps[dutyStep - 1].title}</p>
                  <p className="text-slate-400">&gt; Assigned Role / Actor: {dutySteps[dutyStep - 1].actor}</p>
                  <p className="text-emerald-400">&gt; Status Transition: [{dutySteps[dutyStep - 1].status}] recorded with PostgreSQL immutable timestamp</p>
                  <p className="text-slate-300">&gt; Details: {dutySteps[dutyStep - 1].desc}</p>
                </div>
              </div>
            </div>
          )}

          {/* Module 4: High-Speed Redis Officer Directory Search */}
          {activeTab === "redis" && (
            <div className="p-4 sm:p-8 space-y-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 border-b border-border pb-4">
                <div>
                  <h3 className="text-xl font-bold">Redis-Cached Officer Directory</h3>
                  <p className="text-xs text-muted-foreground">Sub-10ms instantaneous lookups by Officer Name, Police Post, or Rank across divisions.</p>
                </div>

                <Badge variant="outline" className="text-xs font-mono border-primary/30 text-primary">
                  QUERY LATENCY: 2.4ms (REDIS HIT)
                </Badge>
              </div>

              {/* Search Bar & Quick Filters */}
              <div className="space-y-3">
                <div className="relative">
                  <Search className="w-4 h-4 absolute left-3.5 top-3.5 text-muted-foreground" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search by officer name, police post (e.g. Wakad, Hinjewadi), or designation..."
                    className="w-full h-11 pl-10 pr-4 rounded-xl border border-input bg-card text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
                  />
                </div>

                <div className="flex items-center gap-2 flex-wrap text-xs text-muted-foreground">
                  <span>Quick Filters:</span>
                  {["All", "Wakad", "Hinjewadi", "Inspector", "Supervisor"].map((filter) => (
                    <button
                      key={filter}
                      onClick={() => setSearchQuery(filter === "All" ? "" : filter)}
                      className="px-2.5 py-1 rounded-lg border border-border bg-muted/40 hover:bg-muted text-foreground cursor-pointer transition-all"
                    >
                      {filter}
                    </button>
                  ))}
                </div>
              </div>

              {/* Results Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {filteredMockOfficers.map((officer, i) => (
                  <div key={i} className="p-4 rounded-xl border border-border bg-card shadow-xs flex flex-col justify-between space-y-3">
                    <div className="space-y-1">
                      <div className="flex justify-between items-start">
                        <h4 className="font-bold text-sm text-foreground">{officer.name}</h4>
                        <Badge variant="secondary" className="text-[10px]">{officer.role}</Badge>
                      </div>
                      <p className="text-xs text-muted-foreground flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-rose-500" />
                        {officer.post}
                      </p>
                      <p className="text-xs text-muted-foreground">{officer.designation} &bull; {officer.email}</p>
                    </div>

                    <div className="pt-2 border-t border-border flex justify-between items-center text-[10px] text-muted-foreground">
                      <span className="text-emerald-500 font-semibold">&bull; Cached in Redis</span>
                      <span className="text-primary font-medium hover:underline cursor-pointer">View Dossier</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* --- INTERACTIVE POLICE COMMAND HIERARCHY --- */}
      <section className="py-12 sm:py-20 px-4 sm:px-6 max-w-7xl mx-auto border-t border-border/80">
        <div className="text-center mb-10 sm:mb-14">
          <Badge variant="outline" className="mb-2 text-xs font-semibold px-3 py-1 text-primary border-primary/30">
            CHAIN OF COMMAND
          </Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
            5-Tier Police Operational Hierarchy
          </h2>
          <p className="text-muted-foreground text-sm sm:text-base max-w-2xl mx-auto mt-2">
            Every department action is strictly scoped to law enforcement authority. Explore what each command tier controls.
          </p>
        </div>

        {/* Clickable Hierarchy Tabs */}
        <div className="flex gap-2 overflow-x-auto pb-2 justify-start sm:justify-center mb-8">
          {hierarchyRoles.map((role) => (
            <button
              key={role.id}
              onClick={() => setSelectedHierarchyRole(role.id)}
              className={`px-4 py-2.5 rounded-2xl text-xs sm:text-sm font-bold whitespace-nowrap transition-all cursor-pointer border ${
                selectedHierarchyRole === role.id
                  ? "bg-primary text-primary-foreground border-primary shadow-md"
                  : "bg-card text-muted-foreground border-border hover:text-foreground hover:bg-muted"
              }`}
            >
              {role.name}
            </button>
          ))}
        </div>

        {/* Selected Role Interactive Profile Card */}
        {(() => {
          const role = hierarchyRoles.find((r) => r.id === selectedHierarchyRole) || hierarchyRoles[0]
          return (
            <motion.div
              key={role.id}
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
              className="bg-card border border-border p-6 sm:p-8 rounded-3xl shadow-lg max-w-4xl mx-auto"
            >
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-border pb-6">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider">COMMAND LEVEL</span>
                    <Badge variant="outline" className={`text-xs ${role.badgeColor}`}>
                      {role.insignia}
                    </Badge>
                  </div>
                  <h3 className="text-2xl sm:text-3xl font-extrabold">{role.name}</h3>
                </div>

                <Button
                  size="sm"
                  onClick={() => navigate(isLoggedIn ? "/dashboard/teams" : "/signup")}
                  className="rounded-xl text-xs font-bold gap-1.5"
                >
                  Deploy as {role.name}
                  <ChevronRight className="w-3.5 h-3.5" />
                </Button>
              </div>

              <div className="py-6 space-y-4">
                <p className="text-sm sm:text-base text-muted-foreground leading-relaxed">
                  {role.description}
                </p>

                <div className="space-y-2.5 pt-2">
                  <div className="text-xs font-bold text-foreground uppercase tracking-wider">Authorized Operational Powers:</div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    {role.powers.map((p, idx) => (
                      <div key={idx} className="flex items-center gap-2 text-xs text-foreground bg-muted/40 p-2.5 rounded-xl border border-border">
                        <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                        <span>{p}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </motion.div>
          )
        })()}
      </section>

      {/* --- ARCHITECTURE & SECURITY PILLARS --- */}
      <section className="py-12 sm:py-20 px-4 sm:px-6 max-w-7xl mx-auto border-t border-border/80">
        <div className="text-center mb-10 sm:mb-14">
          <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
            Engineered for Defense &amp; High-Availability
          </h2>
          <p className="text-muted-foreground text-sm sm:text-base max-w-2xl mx-auto mt-2">
            A battle-tested technology stack built to support 24/7 uninterrupted law enforcement coordination.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {[
            {
              title: "Dual-Database Isolation",
              desc: "MySQL encrypts user identities & JWT tokens, while PostgreSQL securely manages spatial telemetry, operations, and tasks.",
              icon: <Database className="w-6 h-6 text-blue-500" />
            },
            {
              title: "Redis In-Memory Speed",
              desc: "Officer directory rosters are cached in Redis with instant invalidation hooks on member recruitments for sub-10ms queries.",
              icon: <Layers className="w-6 h-6 text-rose-500" />
            },
            {
              title: "20s Freshness Engine",
              desc: "Continuous telemetry evaluation tags signals as Fresh (<30s), Degraded (<60s), or Stale to prevent lost-officer scenarios.",
              icon: <Radio className="w-6 h-6 text-emerald-500" />
            },
            {
              title: "Tactical PDF Dossiers",
              desc: "Generates court-admissible unit dossiers and individual officer service logs with digital signatures and security classifications.",
              icon: <FileDown className="w-6 h-6 text-amber-500" />
            }
          ].map((pillar, i) => (
            <Card key={i} className="p-6 rounded-2xl bg-card border-border shadow-xs hover:border-primary/40 transition-colors">
              <div className="w-12 h-12 rounded-2xl bg-muted flex items-center justify-center mb-4">
                {pillar.icon}
              </div>
              <h3 className="text-lg font-bold mb-2">{pillar.title}</h3>
              <p className="text-xs sm:text-sm text-muted-foreground leading-relaxed">{pillar.desc}</p>
            </Card>
          ))}
        </div>
      </section>

      {/* --- CALL TO ACTION --- */}
      <section className="py-16 sm:py-24 px-4 sm:px-6 text-center bg-card/60 backdrop-blur-lg border-t border-border">
        <div className="max-w-3xl mx-auto space-y-5">
          <Badge variant="outline" className="text-xs px-3 py-1 font-semibold text-primary border-primary/30">
            DEPLOY COMMAND READINESS
          </Badge>
          <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight">
            Ready to Coordinate Your Police Division?
          </h2>
          <p className="text-muted-foreground text-sm sm:text-base max-w-xl mx-auto">
            Equip supervisors, planners, and beat officers with real-time GPS telemetry, bandobast dispatches, and automated dossiers.
          </p>

          <div className="flex flex-col sm:flex-row gap-3 justify-center pt-4">
            <Button
              size="lg"
              className="rounded-2xl px-8 h-12 sm:h-14 text-base font-bold shadow-md shadow-primary/20 cursor-pointer"
              onClick={() => navigate(isLoggedIn ? "/dashboard" : "/login")}
            >
              {isLoggedIn ? "Open Command HQ" : "Sign In With Police ID"}
              <ArrowRight className="w-4 h-4 ml-1" />
            </Button>
            {!isLoggedIn && (
              <Button
                size="lg"
                variant="outline"
                className="rounded-2xl px-8 h-12 sm:h-14 text-base font-semibold border-border cursor-pointer hover:bg-muted"
                onClick={() => navigate("/signup")}
              >
                Register Department Account
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* --- FOOTER --- */}
      <footer className="py-8 px-4 text-center text-xs text-muted-foreground border-t border-border bg-card/30">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row justify-between items-center gap-4">
          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4 text-primary" />
            <span className="font-bold text-foreground">CopMap Tactical Command System</span>
            <span>&bull;</span>
            <span>Official Law Enforcement Operations Platform</span>
          </div>
          <div>&copy; {new Date().getFullYear()} State Police Department. All rights reserved.</div>
        </div>
      </footer>
    </div>
  )
}
