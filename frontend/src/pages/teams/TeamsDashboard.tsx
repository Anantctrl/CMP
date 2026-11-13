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
    <div className="p-6 max-w-7xl mx-auto space-y-8 select-none">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <Shield className="w-8 h-8 text-primary" />
            Tactical Operations & Team Dashboard
          </h1>
          <p className="text-muted-foreground mt-1">
            Authorized unit deployments, operational tasking, and real-time telemetry.
          </p>
        </div>

        {isSupervisor && (
          <Dialog open={openCreate} onOpenChange={setOpenCreate}>
            <DialogTrigger asChild>
              <Button className="gap-2 shadow-md">
                <Plus className="w-4 h-4" />
                New Tactical Team
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <form onSubmit={handleCreateTeam}>
                <DialogHeader>
                  <DialogTitle>Create Tactical Unit</DialogTitle>
                  <DialogDescription>
                    Establish a new law-enforcement team unit for coordinated operations and tasking.
                  </DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-4">
                  <div className="space-y-2">
                    <Label htmlFor="team-name">Team Name</Label>
                    <Input
                      id="team-name"
                      placeholder="e.g. Cyber Crime Cell, Traffic Division A"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="team-desc">Operational Description</Label>
                    <Textarea
                      id="team-desc"
                      placeholder="Jurisdiction, mandate, or sector coverage..."
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>
                </div>
                <DialogFooter>
                  <Button type="button" variant="outline" onClick={() => setOpenCreate(false)}>
                    Cancel
                  </Button>
                  <Button type="submit" disabled={submitting}>
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
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map((n) => (
            <Card key={n} className="p-6 space-y-4">
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
        <Card className="p-12 text-center border-dashed">
          <div className="flex flex-col items-center justify-center space-y-3">
            <ShieldCheck className="w-12 h-12 text-muted-foreground" />
            <h3 className="text-xl font-semibold">No Units Assigned</h3>
            <p className="text-muted-foreground max-w-md">
              You are not currently assigned to any tactical teams. Contact your divisional supervisor to be onboarded.
            </p>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {teams.map((team) => (
            <Card
              key={team.id}
              className="flex flex-col justify-between hover:border-primary/50 transition-all hover:shadow-lg cursor-pointer group"
              onClick={() => navigate(`/dashboard/teams/${team.id}`)}
            >
              <CardHeader className="space-y-2">
                <div className="flex justify-between items-start gap-2">
                  <CardTitle className="text-xl group-hover:text-primary transition-colors line-clamp-1">
                    {team.name}
                  </CardTitle>
                  <Badge variant={getRoleBadgeVariant(team.userRoleInTeam)}>
                    {team.userRoleInTeam.replace("_", " ")}
                  </Badge>
                </div>
                <CardDescription className="line-clamp-2 min-h-[2.5rem]">
                  {team.description || "Active tactical operational unit."}
                </CardDescription>
              </CardHeader>

              <CardContent>
                <div className="grid grid-cols-2 gap-4 py-2 border-y border-border/50 text-sm">
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Users className="w-4 h-4 text-primary" />
                    <span>{team.memberCount} Officers</span>
                  </div>
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Activity className="w-4 h-4 text-emerald-500" />
                    <span>{team.activeOperationsCount} Operations</span>
                  </div>
                </div>
              </CardContent>

              <CardFooter className="pt-2 flex justify-between items-center text-sm font-medium text-primary">
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
