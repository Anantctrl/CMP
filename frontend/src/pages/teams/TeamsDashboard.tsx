import { useEffect, useState, type FormEvent } from "react"
import { useNavigate } from "react-router"
import { getAuthorizedTeams, createTeam, type Team } from "@/services/teamService"
import useAuth from "@/auth/store"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Skeleton } from "@/components/ui/skeleton"
import { Shield, Users, Plus, ArrowRight, ShieldCheck, Activity } from "lucide-react"
import toast from "react-hot-toast"

export default function TeamsDashboard() {
  const [teams, setTeams] = useState<Team[]>([])
  const [loading, setLoading] = useState(true)
  const [openCreate, setOpenCreate] = useState(false)
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [submitting, setSubmitting] = useState(false)

  const navigate = useNavigate()
  const user = useAuth((state) => state.user)
  const isSupervisor = user?.role?.name?.toLowerCase() === "supervisor"

  const loadTeams = async () => {
    try {
      setLoading(true)
      const data = await getAuthorizedTeams()
      setTeams(data)
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Failed to load teams")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadTeams()
  }, [])

  const handleCreateTeam = async (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) {
      toast.error("Team name is required")
      return
    }

    try {
      setSubmitting(true)
      await createTeam({ name: name.trim(), description: description.trim() })
      toast.success("Team created successfully")
      setName("")
      setDescription("")
      setOpenCreate(false)
      loadTeams()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Failed to create team")
    } finally {
      setSubmitting(false)
    }
  }

  const getRoleBadgeVariant = (role: string) => {
    switch (role?.toUpperCase()) {
      case "SUPERVISOR":
        return "default"
      case "SENIOR_PLANNER":
        return "info"
      case "TEAM_LEAD":
        return "warning"
      case "CONTROL_OPERATOR":
        return "secondary"
      case "FIELD_OPERATOR":
        return "success"
      default:
        return "outline"
    }
  }

  return (
    <div className="px-4 py-6 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6 sm:space-y-8 select-none">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-border/60 pb-5 sm:pb-6">
        <div className="space-y-1">
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight flex items-center gap-2.5">
            <Shield className="w-7 h-7 text-primary shrink-0" />
            Tactical Teams Dashboard
          </h1>
          <p className="text-xs sm:text-sm text-muted-foreground">
            Authorized unit deployments, operational tasking, and real-time telemetry.
          </p>
        </div>

        {isSupervisor && (
          <Dialog open={openCreate} onOpenChange={setOpenCreate}>
            <DialogTrigger asChild>
              <Button className="gap-2 shadow-md w-full sm:w-auto h-11 sm:h-10 rounded-xl font-semibold">
                <Plus className="w-4 h-4" />
                New Tactical Team
              </Button>
            </DialogTrigger>
            <DialogContent className="w-[95vw] sm:max-w-md max-h-[90vh] overflow-y-auto rounded-2xl p-4 sm:p-6">
              <form onSubmit={handleCreateTeam}>
                <DialogHeader>
                  <DialogTitle className="text-lg sm:text-xl font-bold">Create Tactical Unit</DialogTitle>
                  <DialogDescription className="text-xs sm:text-sm">
                    Establish a new law-enforcement team unit for coordinated operations and tasking.
                  </DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="team-name" className="text-xs sm:text-sm font-semibold">Team Name</Label>
                    <Input
                      id="team-name"
                      placeholder="e.g. Cyber Crime Cell, Traffic Division A"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      className="h-10 sm:h-11 rounded-xl text-sm"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="team-desc" className="text-xs sm:text-sm font-semibold">Operational Description</Label>
                    <Textarea
                      id="team-desc"
                      placeholder="Jurisdiction, mandate, or sector coverage..."
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      className="rounded-xl text-sm min-h-[80px]"
                    />
                  </div>
                </div>
                <DialogFooter className="flex-col sm:flex-row gap-2 pt-2">
                  <Button type="button" variant="outline" className="w-full sm:w-auto rounded-xl" onClick={() => setOpenCreate(false)}>
                    Cancel
                  </Button>
                  <Button type="submit" disabled={submitting} className="w-full sm:w-auto rounded-xl font-semibold">
                    {submitting ? "Creating..." : "Establish Unit"}
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        )}
      </div>

      {/* Grid of Teams */}
      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
          {[1, 2, 3].map((n) => (
            <Card key={n} className="p-5 sm:p-6 space-y-4 rounded-2xl">
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-full" />
              <div className="flex gap-2">
                <Skeleton className="h-8 w-20" />
                <Skeleton className="h-8 w-20" />
              </div>
            </Card>
          ))}
        </div>
      ) : teams.length === 0 ? (
        <Card className="p-8 sm:p-12 text-center border-dashed rounded-2xl">
          <div className="flex flex-col items-center justify-center space-y-3">
            <ShieldCheck className="w-10 h-10 sm:w-12 sm:h-12 text-muted-foreground" />
            <h3 className="text-lg sm:text-xl font-bold">No Units Assigned</h3>
            <p className="text-xs sm:text-sm text-muted-foreground max-w-md">
              You are not currently assigned to any tactical teams. Contact your divisional supervisor to be onboarded.
            </p>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
          {teams.map((team) => (
            <Card
              key={team.id}
              className="flex flex-col justify-between hover:border-primary/50 transition-all hover:shadow-lg cursor-pointer group rounded-2xl bg-card/80 backdrop-blur-sm"
              onClick={() => navigate(`/dashboard/teams/${team.id}`)}
            >
              <CardHeader className="p-5 sm:p-6 space-y-2">
                <div className="flex justify-between items-start gap-2">
                  <CardTitle className="text-lg sm:text-xl font-bold group-hover:text-primary transition-colors line-clamp-1">
                    {team.name}
                  </CardTitle>
                  <Badge variant={getRoleBadgeVariant(team.userRoleInTeam)} className="text-[11px] shrink-0">
                    {team.userRoleInTeam.replace("_", " ")}
                  </Badge>
                </div>
                <CardDescription className="text-xs sm:text-sm line-clamp-2 min-h-[2.5rem]">
                  {team.description || "Active tactical operational unit."}
                </CardDescription>
              </CardHeader>

              <CardContent className="px-5 sm:px-6">
                <div className="grid grid-cols-2 gap-3 py-2 border-y border-border/50 text-xs sm:text-sm">
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Users className="w-4 h-4 text-primary shrink-0" />
                    <span className="truncate">{team.memberCount} Officers</span>
                  </div>
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Activity className="w-4 h-4 text-emerald-500 shrink-0" />
                    <span className="truncate">{team.activeOperationsCount} Operations</span>
                  </div>
                </div>
              </CardContent>

              <CardFooter className="px-5 sm:px-6 py-4 flex justify-between items-center text-xs sm:text-sm font-semibold text-primary">
                <span>View Command Hub</span>
                <ArrowRight className="w-4 h-4 transform group-hover:translate-x-1 transition-transform" />
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
