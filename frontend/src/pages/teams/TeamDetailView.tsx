import { useEffect, useState, useCallback, type FormEvent } from "react"
import { useParams, useNavigate } from "react-router"
import {
  getTeamDetail,
  getTeamMembers,
  addTeamMember,
  removeTeamMember,
  getTeamAudit,
  type Team,
  type TeamMember,
  type TeamAudit
} from "@/services/teamService"
import {
  getTasks,
  createTask,
  assignOfficer,
  startAssignment,
  submitAssignment,
  approveAssignment,
  getTaskAssignments,
  type Task,
  type TaskAssignment
} from "@/services/taskService"
import {
  getOperations,
  createOperation,
  changeOperationStatus,
  getApprovalRequests,
  approveApprovalRequest,
  rejectApprovalRequest,
  type Operation,
  type OperationApprovalRequest
} from "@/services/operationService"
import {
  getTeamLiveLocations,
  sendLocationUpdate,
  type OfficerLiveLocation
} from "@/services/trackingService"
import { getAvailableOfficers, getAllOfficers, type OfficerDirectoryEntry } from "@/services/officerService"
import { generateTeamReportPDF, generateOfficerReportPDF } from "@/services/pdfReportService"
import { TacticalMapView } from "@/components/map/TacticalMapView"
import { OfficerMapModal } from "@/components/map/OfficerMapModal"
import { getFastHeuristicArea, getAreaName } from "@/services/geoService"
import useAuth from "@/auth/store"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Shield,
  Plus,
  ArrowLeft,
  Clock,
  MapPin,
  RefreshCw,
  Play,
  CheckCircle2,
  FileCheck,
  History,
  Radio,
  UserCheck,
  Search,
  UserPlus,
  Check,
  FileDown
} from "lucide-react"
import toast from "react-hot-toast"

export default function TeamDetailView() {
  const { teamId } = useParams<{ teamId: string }>()
  const navigate = useNavigate()
  const user = useAuth((state) => state.user)

  const [team, setTeam] = useState<Team | null>(null)
  const [members, setMembers] = useState<TeamMember[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [taskAssignmentsMap, setTaskAssignmentsMap] = useState<Record<string, TaskAssignment[]>>({})
  const [operations, setOperations] = useState<Operation[]>([])
  const [liveLocations, setLiveLocations] = useState<OfficerLiveLocation[]>([])
  const [approvals, setApprovals] = useState<OperationApprovalRequest[]>([])
  const [auditLogs, setAuditLogs] = useState<TeamAudit[]>([])
  const [loading, setLoading] = useState(true)

  // Modals state
  const [openTaskModal, setOpenTaskModal] = useState(false)
  const [taskTitle, setTaskTitle] = useState("")
  const [taskAction, setTaskAction] = useState("PATROLLING")
  const [taskDesc, setTaskDesc] = useState("")
  const [taskLocation, setTaskLocation] = useState("")

  const [openAssignModal, setOpenAssignModal] = useState(false)
  const [selectedTaskId, setSelectedTaskId] = useState("")
  const [assignOfficerId, setAssignOfficerId] = useState("")
  const [responsibility, setResponsibility] = useState("")
  const [instructions, setInstructions] = useState("")

  const [openSubmitModal, setOpenSubmitModal] = useState(false)
  const [submittingAssignmentId, setSubmittingAssignmentId] = useState("")
  const [completionReport, setCompletionReport] = useState("")

  const [openAddMemberModal, setOpenAddMemberModal] = useState(false)
  const [newOfficerId, setNewOfficerId] = useState("")
  const [newTeamRole, setNewTeamRole] = useState("FIELD_OPERATOR")
  const [availableOfficers, setAvailableOfficers] = useState<OfficerDirectoryEntry[]>([])
  const [loadingAvailableOfficers, setLoadingAvailableOfficers] = useState(false)
  const [officerSearchQuery, setOfficerSearchQuery] = useState("")

  // Fetch available officers from Redis cache when opening Add Member dialog
  useEffect(() => {
    if (openAddMemberModal && teamId) {
      setLoadingAvailableOfficers(true)
      setOfficerSearchQuery("")
      setNewOfficerId("")
      getAvailableOfficers(teamId)
        .then((data) => setAvailableOfficers(data))
        .catch(() => toast.error("Failed to load officer directory"))
        .finally(() => setLoadingAvailableOfficers(false))
    }
  }, [openAddMemberModal, teamId])

  const filteredOfficers = availableOfficers.filter((o) => {
    const q = officerSearchQuery.trim().toLowerCase()
    if (!q) return true
    return (
      (o.name && o.name.toLowerCase().includes(q)) ||
      (o.policePost && o.policePost.toLowerCase().includes(q)) ||
      (o.designation && o.designation.toLowerCase().includes(q)) ||
      (o.email && o.email.toLowerCase().includes(q))
    )
  })

  const selectedOfficer = availableOfficers.find((o) => o.officerId === newOfficerId)

  const [openOperationModal, setOpenOperationModal] = useState(false)
  const [opTitle, setOpTitle] = useState("")
  const [opType, setOpType] = useState("BANDOBAST")
  const [opDesc, setOpDesc] = useState("")

  const [activeTab, setActiveTab] = useState("tasks")

  // Map & live tracking modal state
  const [mapModalOpen, setMapModalOpen] = useState(false)
  const [selectedOfficerLocation, setSelectedOfficerLocation] = useState<OfficerLiveLocation | null>(null)
  const [selectedOfficerRole, setSelectedOfficerRole] = useState<string | undefined>(undefined)
  const [selectedMapOfficerId, setSelectedMapOfficerId] = useState<string | null>(null)
  const [trackingAreaNames, setTrackingAreaNames] = useState<Record<string, string>>({})

  const userRole = team?.userRoleInTeam?.toUpperCase() || ""
  const isSupervisor = user?.role?.name?.toLowerCase() === "supervisor" || userRole === "SUPERVISOR"
  const isPlanner = isSupervisor || userRole === "SENIOR_PLANNER"
  const isLead = isSupervisor || isPlanner || userRole === "TEAM_LEAD"
  const isControlOperator = userRole === "CONTROL_OPERATOR"
  const canDownloadReport =
    isSupervisor ||
    isPlanner ||
    isLead ||
    isControlOperator ||
    userRole === "ADMINISTRATOR"

  const [allOfficersDirectory, setAllOfficersDirectory] = useState<OfficerDirectoryEntry[]>([])

  useEffect(() => {
    getAllOfficers()
      .then((data) => setAllOfficersDirectory(data))
      .catch(() => {})
  }, [])

  // Download Comprehensive Team Tactical Dossier (PDF)
  const handleDownloadTeamReport = () => {
    if (!team) return
    toast.loading("Compiling tactical unit dossier (PDF)...", { id: "team-pdf" })
    try {
      generateTeamReportPDF({
        team,
        members,
        tasks,
        taskAssignmentsMap,
        operations,
        liveLocations,
        trackingAreaNames,
        generatedBy: {
          name: user?.name,
          email: user?.email,
          role: team.userRoleInTeam || (isSupervisor ? "SUPERVISOR" : "COMMAND")
        }
      })
      toast.success("Tactical Unit Dossier downloaded!", { id: "team-pdf" })
    } catch (err) {
      console.error("PDF generation error:", err)
      toast.error("Failed to generate PDF dossier", { id: "team-pdf" })
    }
  }

  // Download Individual Officer Performance & Task Dossier (PDF)
  const handleDownloadOfficerReport = (member: TeamMember) => {
    toast.loading(`Compiling dossier for ${member.officerName || "Officer"}...`, { id: "officer-pdf" })
    try {
      const officerLoc = liveLocations.find((l) => l.officerId === member.officerId)
      const dirEntry =
        allOfficersDirectory.find((o) => o.officerId === member.officerId) ||
        availableOfficers.find((o) => o.officerId === member.officerId)
      const post = dirEntry?.policePost
      const area = officerLoc
        ? trackingAreaNames[officerLoc.officerId] || getFastHeuristicArea(officerLoc.latitude, officerLoc.longitude)
        : undefined

      generateOfficerReportPDF({
        officer: member,
        team,
        tasks,
        taskAssignmentsMap,
        liveLocation: officerLoc,
        policePost: post,
        detectedArea: area,
        generatedBy: {
          name: user?.name,
          email: user?.email,
          role: team?.userRoleInTeam || (isSupervisor ? "SUPERVISOR" : "COMMAND")
        }
      })
      toast.success(`Dossier for ${member.officerName || "Officer"} downloaded!`, { id: "officer-pdf" })
    } catch (err) {
      console.error("Officer PDF generation error:", err)
      toast.error("Failed to generate officer dossier", { id: "officer-pdf" })
    }
  }

  const loadLiveLocations = useCallback(async () => {
    if (!teamId) return
    try {
      const locs = await getTeamLiveLocations(teamId)
      setLiveLocations(locs)
    } catch {
      // Background polling silent fail
    }
  }, [teamId])

  const loadAllData = useCallback(async () => {
    if (!teamId) return
    try {
      setLoading(true)
      const [teamData, membersData, tasksData, opsData] = await Promise.all([
        getTeamDetail(teamId),
        getTeamMembers(teamId).catch(() => []),
        getTasks(teamId).catch(() => []),
        getOperations().catch(() => [])
      ])
      setTeam(teamData)
      setMembers(membersData)
      setTasks(tasksData)
      setOperations(opsData.filter(op => op.participatingTeamIds?.includes(teamId)))

      // Load assignments for all tasks
      const map: Record<string, TaskAssignment[]> = {}
      for (const t of tasksData) {
        try {
          const assigns = await getTaskAssignments(t.id)
          map[t.id] = assigns
        } catch {
          map[t.id] = []
        }
      }
      setTaskAssignmentsMap(map)

      if (isSupervisor) {
        getApprovalRequests().then(setApprovals).catch(() => {})
      }
      if (isSupervisor || isPlanner || isLead) {
        getTeamAudit(teamId).then(setAuditLogs).catch(() => {})
      }

      // Immediately load live locations
      loadLiveLocations()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Failed to load team data")
    } finally {
      setLoading(false)
    }
  }, [teamId, isSupervisor, isPlanner, isLead, loadLiveLocations])

  useEffect(() => {
    loadAllData()
    loadLiveLocations()
    const interval = setInterval(loadLiveLocations, 15000)
    return () => clearInterval(interval)
  }, [loadAllData, loadLiveLocations])

  // Resolve human-readable areas for all live locations
  useEffect(() => {
    liveLocations.forEach(async (loc) => {
      const area = await getAreaName(loc.latitude, loc.longitude)
      setTrackingAreaNames((prev) => ({ ...prev, [loc.officerId]: area }))
    })
  }, [liveLocations])

  const handleOpenOfficerModal = (loc: OfficerLiveLocation, role?: string) => {
    const member = members.find((m) => m.officerId === loc.officerId)
    setSelectedOfficerLocation(loc)
    setSelectedOfficerRole(role || member?.teamRole)
    setSelectedMapOfficerId(loc.officerId)
    setMapModalOpen(true)
  }

  const handleOpenOfficerMapByMember = (member: TeamMember) => {
    const loc = liveLocations.find((l) => l.officerId === member.officerId)
    if (loc) {
      setSelectedOfficerLocation(loc)
      setSelectedOfficerRole(member.teamRole)
      setSelectedMapOfficerId(loc.officerId)
      setMapModalOpen(true)
    } else {
      toast.error(`No live GPS telemetry reported for ${member.officerName || "this officer"} yet.`)
    }
  }

  const handleOpenOfficerMapByOfficerId = (officerId: string, officerName?: string) => {
    const loc = liveLocations.find((l) => l.officerId === officerId)
    if (loc) {
      const member = members.find((m) => m.officerId === officerId)
      setSelectedOfficerLocation(loc)
      setSelectedOfficerRole(member?.teamRole)
      setSelectedMapOfficerId(officerId)
      setMapModalOpen(true)
    } else {
      toast.error(`No live GPS telemetry reported for ${officerName || "this officer"} yet.`)
    }
  }

  // Handlers
  const handleCreateTask = async (e: FormEvent) => {
    e.preventDefault()
    if (!teamId || !taskTitle.trim()) return
    try {
      await createTask({
        teamId,
        title: taskTitle.trim(),
        actionType: taskAction,
        description: taskDesc.trim(),
        locationName: taskLocation.trim()
      })
      toast.success("Task dispatched")
      setOpenTaskModal(false)
      setTaskTitle("")
      setTaskDesc("")
      setTaskLocation("")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Error creating task")
    }
  }

  const handleAssignOfficer = async (e: FormEvent) => {
    e.preventDefault()
    if (!selectedTaskId || !assignOfficerId || !responsibility.trim()) return
    try {
      await assignOfficer(selectedTaskId, {
        officerId: assignOfficerId,
        responsibility: responsibility.trim(),
        instructions: instructions.trim()
      })
      toast.success("Officer assigned to task")
      setOpenAssignModal(false)
      setResponsibility("")
      setInstructions("")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Assignment failed")
    }
  }

  const handleStartAssignment = async (assignmentId: string) => {
    try {
      await startAssignment(assignmentId)
      toast.success("Task assignment started")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Could not start assignment")
    }
  }

  const handleSubmitReport = async (e: FormEvent) => {
    e.preventDefault()
    if (!submittingAssignmentId || !completionReport.trim()) return
    try {
      await submitAssignment(submittingAssignmentId, completionReport.trim())
      toast.success("Completion report submitted for supervisor review")
      setOpenSubmitModal(false)
      setCompletionReport("")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Report submission failed")
    }
  }

  const handleApproveAssignment = async (assignmentId: string) => {
    const remarks = prompt("Review remarks for completion approval:", "Completed satisfactorily")
    if (remarks === null) return
    try {
      await approveAssignment(assignmentId, remarks)
      toast.success("Assignment approved and closed")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Approval failed")
    }
  }

  const handleCreateOperation = async (e: FormEvent) => {
    e.preventDefault()
    if (!teamId || !opTitle.trim()) return
    try {
      await createOperation({
        type: opType,
        title: opTitle.trim(),
        description: opDesc.trim(),
        teamIds: [teamId]
      })
      toast.success("Operation created")
      setOpenOperationModal(false)
      setOpTitle("")
      setOpDesc("")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Operation creation failed")
    }
  }

  const handleUpdateOpStatus = async (opId: string, status: string) => {
    try {
      await changeOperationStatus(opId, status)
      toast.success(`Operation status updated to ${status}`)
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Status update failed")
    }
  }

  const handleAddMember = async (e: FormEvent) => {
    e.preventDefault()
    if (!teamId || !newOfficerId.trim()) {
      toast.error("Please select an officer from the directory")
      return
    }
    try {
      await addTeamMember(teamId, {
        officerId: newOfficerId.trim(),
        teamRole: newTeamRole
      })
      toast.success(`${selectedOfficer?.name || "Officer"} successfully onboarded to unit`)
      setOpenAddMemberModal(false)
      setNewOfficerId("")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Failed to add member")
    }
  }

  const handleRemoveMember = async (officerId: string) => {
    if (!teamId) return
    const remarks = prompt("Enter removal reason for audit log:")
    if (remarks === null) return

    try {
      await removeTeamMember(teamId, officerId, remarks || "Member removed from unit")
      toast.success("Member removed and logged to audit trail")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Failed to remove member")
    }
  }

  const handleApproveRequest = async (id: string) => {
    try {
      await approveApprovalRequest(id, "Approved by supervisor")
      toast.success("Cross-team operation request approved")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Approval failed")
    }
  }

  const handleRejectRequest = async (id: string) => {
    try {
      await rejectApprovalRequest(id, "Rejected by supervisor")
      toast.success("Operation request rejected")
      loadAllData()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "Rejection failed")
    }
  }

  const handleSimulateGPS = async () => {
    try {
      // 1. Try actual hardware device GPS if browser permission is available
      if (typeof window !== "undefined" && "geolocation" in navigator) {
        try {
          const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
              enableHighAccuracy: true,
              timeout: 6000,
              maximumAge: 10000
            })
          })

          await sendLocationUpdate({
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
            accuracyMeters: Math.round(pos.coords.accuracy || 8),
            recordedAt: new Date(pos.timestamp || Date.now()).toISOString()
          })
          const area = await getAreaName(pos.coords.latitude, pos.coords.longitude)
          toast.success(`Live GPS update recorded: ${area}`)
          loadLiveLocations()
          return
        } catch {
          // Fall through to last reported coordinates
        }
      }

      // 2. Fallback: Update based on officer's actual coordinates (or default to team sector)
      const existing = liveLocations.find((l) => l.officerName?.includes(user?.name || "")) || liveLocations[0]
      const baseLat = existing ? existing.latitude : 18.6319
      const baseLon = existing ? existing.longitude : 73.7986

      await sendLocationUpdate({
        latitude: baseLat + (Math.random() - 0.5) * 0.001,
        longitude: baseLon + (Math.random() - 0.5) * 0.001,
        accuracyMeters: 4.8,
        recordedAt: new Date().toISOString()
      })
      toast.success("Live telemetry update recorded")
      loadLiveLocations()
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      toast.error(error.response?.data?.message || "GPS update failed")
    }
  }

  const getFreshnessBadge = (status: string) => {
    switch (status) {
      case "FRESH":
        return <Badge variant="success">🟢 Live (Fresh)</Badge>
      case "DEGRADED":
        return <Badge variant="warning">🟡 Degraded</Badge>
      default:
        return <Badge variant="destructive">🔴 Stale</Badge>
    }
  }

  if (loading && !team) {
    return (
      <div className="px-4 py-6 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  return (
    <div className="px-4 py-6 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6 select-none">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-card border border-border px-4 py-5 sm:p-6 rounded-2xl shadow-sm">
        <div className="space-y-1 min-w-0">
          <Button variant="ghost" size="sm" onClick={() => navigate("/dashboard/teams")} className="gap-2 mb-2 -ml-2 text-muted-foreground">
            <ArrowLeft className="w-4 h-4" />
            Back to Teams
          </Button>
          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight truncate">{team?.name}</h1>
            <Badge variant="default" className="text-xs shrink-0">
              {team?.userRoleInTeam.replace("_", " ")}
            </Badge>
          </div>
          <p className="text-muted-foreground text-sm">{team?.description || "Tactical Command Unit"}</p>
        </div>

        <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
          {canDownloadReport && (
            <Button
              onClick={handleDownloadTeamReport}
              variant="outline"
              className="gap-2 shadow-xs flex-1 sm:flex-initial justify-center border-primary/40 hover:bg-primary/10 text-foreground"
            >
              <FileDown className="w-4 h-4 text-primary" />
              Download Unit Dossier (PDF)
            </Button>
          )}
          {isPlanner && (
            <Button onClick={() => setOpenTaskModal(true)} className="gap-2 shadow-xs flex-1 sm:flex-initial justify-center">
              <Plus className="w-4 h-4" />
              New Task
            </Button>
          )}
          {isPlanner && (
            <Button onClick={() => setOpenOperationModal(true)} variant="outline" className="gap-2 shadow-xs flex-1 sm:flex-initial justify-center">
              <Shield className="w-4 h-4" />
              New Operation
            </Button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <div className="overflow-x-auto pb-1 -mx-1 px-1">
          <TabsList className="grid grid-cols-3 sm:grid-cols-6 w-full min-w-[440px] sm:min-w-0 sm:max-w-3xl">
            <TabsTrigger value="tasks" className="text-xs sm:text-sm">Tasks ({tasks.length})</TabsTrigger>
            <TabsTrigger value="operations" className="text-xs sm:text-sm">Ops ({operations.length})</TabsTrigger>
            <TabsTrigger value="tracking" className="text-xs sm:text-sm">Live Map</TabsTrigger>
            <TabsTrigger value="members" className="text-xs sm:text-sm">Officers ({members.length})</TabsTrigger>
            <TabsTrigger value="audit" className="text-xs sm:text-sm">Audit Log</TabsTrigger>
            {isSupervisor && <TabsTrigger value="approvals" className="text-xs sm:text-sm">Approvals ({approvals.length})</TabsTrigger>}
          </TabsList>
        </div>

        {/* --- TASKS TAB --- */}
        <TabsContent value="tasks" className="space-y-4">
          {tasks.length === 0 ? (
            <Card className="p-8 text-center text-muted-foreground">
              No tasks currently dispatched for this unit.
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {tasks.map((task) => {
                const assigns = taskAssignmentsMap[task.id] || []
                return (
                  <Card key={task.id} className="flex flex-col justify-between">
                    <CardHeader>
                      <div className="flex justify-between items-start">
                        <CardTitle className="text-lg">{task.title}</CardTitle>
                        <Badge variant="outline">{task.actionType}</Badge>
                      </div>
                      <CardDescription>{task.description || "No specific instructions provided."}</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3 text-sm">
                      {task.locationName && (
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <MapPin className="w-4 h-4 text-primary" />
                          <span>{task.locationName}</span>
                        </div>
                      )}
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Clock className="w-3.5 h-3.5" />
                        <span>Status: {task.status}</span>
                      </div>

                      {/* Display assigned officer names badge list directly on the task */}
                      {task.assignedOfficerNames && task.assignedOfficerNames.length > 0 && (
                        <div className="flex flex-wrap items-center gap-1.5 pt-1">
                          <span className="text-xs font-semibold text-muted-foreground flex items-center gap-1">
                            <UserCheck className="w-3.5 h-3.5 text-primary" /> Assigned Officers:
                          </span>
                          {task.assignedOfficerNames.map((name) => (
                            <Badge key={name} variant="secondary" className="text-xs font-medium">
                              {name}
                            </Badge>
                          ))}
                        </div>
                      )}

                      {/* Sub-assignments list with detailed officer names and responsibilities */}
                      <div className="pt-2 border-t space-y-2">
                        <p className="font-semibold text-xs text-muted-foreground">Officer Assignments ({assigns.length}):</p>
                        {assigns.length === 0 ? (
                          <p className="text-xs text-muted-foreground italic">No officers assigned yet.</p>
                        ) : (
                          <div className="space-y-2">
                            {assigns.map((a) => (
                              <div key={a.id} className="bg-muted/40 p-2.5 rounded-lg border text-xs space-y-1.5">
                                <div className="flex justify-between items-center">
                                  <div className="flex items-center gap-1.5 flex-wrap">
                                    {liveLocations.some((l) => l.officerId === a.officerId) ? (
                                      <button
                                        type="button"
                                        onClick={() => handleOpenOfficerMapByOfficerId(a.officerId, a.officerName)}
                                        className="font-bold text-primary hover:underline inline-flex items-center gap-1 cursor-pointer"
                                        title="Click to track live location on map"
                                      >
                                        <MapPin className="w-3.5 h-3.5 text-rose-500" />
                                        {a.officerName || `Officer ${a.officerId.substring(0, 8)}`}
                                      </button>
                                    ) : (
                                      <span className="font-bold text-foreground">
                                        {a.officerName || `Officer ${a.officerId.substring(0, 8)}`}
                                      </span>
                                    )}
                                    {a.officerDesignation && (
                                      <span className="text-muted-foreground text-[11px]">
                                        ({a.officerDesignation})
                                      </span>
                                    )}
                                    <span className="font-medium text-primary">
                                      — {a.responsibility}
                                    </span>
                                    {canDownloadReport && (
                                      <button
                                        type="button"
                                        onClick={() => {
                                          const member = members.find((m) => m.officerId === a.officerId) || {
                                            id: a.officerId,
                                            teamId: team?.id || "",
                                            officerId: a.officerId,
                                            officerName: a.officerName,
                                            designation: a.officerDesignation || "Officer",
                                            teamRole: "FIELD_OPERATOR" as const,
                                            joinedAt: a.assignedAt
                                          }
                                          handleDownloadOfficerReport(member)
                                        }}
                                        className="text-muted-foreground hover:text-primary transition-colors cursor-pointer inline-flex items-center gap-0.5 text-[10px] ml-1"
                                        title="Download Officer Dossier (PDF)"
                                      >
                                        <FileDown className="w-3 h-3 text-primary" />
                                        <span>PDF</span>
                                      </button>
                                    )}
                                  </div>
                                  <Badge variant="outline" className="text-[10px]">{a.status}</Badge>
                                </div>
                                {a.instructions && <p className="text-muted-foreground">{a.instructions}</p>}

                                <div className="flex gap-2 pt-1">
                                  {a.status === "ASSIGNED" && (
                                    <Button size="xs" variant="outline" onClick={() => handleStartAssignment(a.id)}>
                                      <Play className="w-3 h-3 mr-1" /> Start Duty
                                    </Button>
                                  )}
                                  {a.status === "IN_PROGRESS" && (
                                    <Button
                                      size="xs"
                                      variant="outline"
                                      onClick={() => {
                                        setSubmittingAssignmentId(a.id)
                                        setOpenSubmitModal(true)
                                      }}
                                    >
                                      <FileCheck className="w-3 h-3 mr-1" /> Submit Report
                                    </Button>
                                  )}
                                  {a.status === "SUBMITTED" && isLead && (
                                    <Button size="xs" onClick={() => handleApproveAssignment(a.id)}>
                                      <CheckCircle2 className="w-3 h-3 mr-1" /> Approve & Close
                                    </Button>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </CardContent>
                    <CardFooter className="border-t pt-3 flex justify-between items-center">
                      {isLead && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setSelectedTaskId(task.id)
                            setOpenAssignModal(true)
                          }}
                        >
                          <Plus className="w-3 h-3 mr-1" /> Assign Officer
                        </Button>
                      )}
                    </CardFooter>
                  </Card>
                )
              })}
            </div>
          )}
        </TabsContent>

        {/* --- OPERATIONS TAB --- */}
        <TabsContent value="operations" className="space-y-4">
          {operations.length === 0 ? (
            <Card className="p-8 text-center text-muted-foreground">
              No active operations assigned to this unit.
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {operations.map((op) => (
                <Card key={op.id} className="flex flex-col justify-between">
                  <CardHeader>
                    <div className="flex justify-between items-start">
                      <CardTitle className="text-lg">{op.title}</CardTitle>
                      <Badge variant={op.status === "ACTIVE" ? "success" : "default"}>
                        {op.status}
                      </Badge>
                    </div>
                    <CardDescription>{op.description || "Tactical operation brief."}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex justify-between text-muted-foreground">
                      <span>Type: {op.type}</span>
                      {op.pendingApproval && <Badge variant="warning">Approval Pending</Badge>}
                    </div>
                  </CardContent>
                  <CardFooter className="border-t pt-3 flex gap-2">
                    {isLead && op.status === "PLANNED" && (
                      <Button size="xs" onClick={() => handleUpdateOpStatus(op.id, "ACTIVE")}>
                        Start Operation
                      </Button>
                    )}
                    {isLead && op.status === "ACTIVE" && (
                      <Button size="xs" variant="outline" onClick={() => handleUpdateOpStatus(op.id, "COMPLETED")}>
                        Mark Completed
                      </Button>
                    )}
                  </CardFooter>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>

        {/* --- LIVE GPS TRACKING TAB --- */}
        <TabsContent value="tracking" className="space-y-6">
          {/* Tactical Map View */}
          {liveLocations.length > 0 && (
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <h4 className="text-sm font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                  <MapPin className="w-4 h-4 text-primary" />
                  Tactical Deployment Map ({liveLocations.length} Active Officers)
                </h4>
                <span className="text-xs text-muted-foreground">Click any marker or officer row to open detailed GPS telemetry</span>
              </div>
              <TacticalMapView
                locations={liveLocations}
                selectedOfficerId={selectedMapOfficerId}
                onSelectOfficer={(loc) => handleOpenOfficerModal(loc)}
                height="380px"
              />
            </div>
          )}

          <Card className="p-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
              <div>
                <h3 className="text-lg font-bold flex items-center gap-2">
                  <Radio className="w-5 h-5 text-emerald-500 animate-pulse" />
                  Live Unit Telemetry Monitor
                </h3>
                <p className="text-sm text-muted-foreground">
                  Streaming 20-second officer GPS updates with automated area detection and signal freshness validation.
                </p>
              </div>
              <div className="flex flex-wrap gap-2 items-center">
                <Button size="sm" variant="outline" onClick={loadLiveLocations} className="gap-2">
                  <RefreshCw className="w-4 h-4" />
                  Refresh
                </Button>
                <Button size="sm" onClick={handleSimulateGPS} className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-medium">
                  <Radio className="w-4 h-4 text-emerald-200 animate-pulse" />
                  Simulate GPS Ping
                </Button>
              </div>
            </div>

            {liveLocations.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground border border-dashed rounded-xl space-y-3">
                <MapPin className="w-8 h-8 mx-auto text-muted-foreground/60" />
                <p className="font-medium">No active GPS fixes reported for officers in this team yet.</p>
                <p className="text-xs text-muted-foreground">
                  Officers will appear automatically when telemetry updates are received.
                </p>
                <Button
                  size="sm"
                  onClick={handleSimulateGPS}
                  className="mt-2 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <Radio className="w-4 h-4 mr-2" />
                  Simulate GPS Ping
                </Button>
              </div>
            ) : (
              <div className="rounded-lg border overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-muted/30">
                      <TableHead>Officer Name</TableHead>
                      <TableHead>Designation</TableHead>
                      <TableHead>Detected Area / Sector</TableHead>
                      <TableHead>Coordinates (Lat, Lon)</TableHead>
                      <TableHead>Accuracy</TableHead>
                      <TableHead>Freshness</TableHead>
                      <TableHead>Timestamp</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {liveLocations.map((loc) => {
                      const area = trackingAreaNames[loc.officerId] || getFastHeuristicArea(loc.latitude, loc.longitude)
                      return (
                        <TableRow
                          key={loc.officerId}
                          className="hover:bg-muted/40 cursor-pointer transition-colors"
                          onClick={() => handleOpenOfficerModal(loc)}
                        >
                          <TableCell className="font-semibold text-xs">
                            <div className="flex items-center gap-2">
                              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
                              <span className="text-primary hover:underline font-bold">
                                {loc.officerName || `Officer ${loc.officerId.substring(0, 8)}...`}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell className="text-xs">{loc.designation}</TableCell>
                          <TableCell className="text-xs">
                            <div className="flex items-center gap-1.5 font-medium text-foreground">
                              <MapPin className="w-3.5 h-3.5 text-rose-500 shrink-0" />
                              <span>{area}</span>
                            </div>
                          </TableCell>
                          <TableCell className="font-mono text-xs text-muted-foreground">
                            {loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}
                          </TableCell>
                          <TableCell className="text-xs">{loc.accuracyMeters ? `±${loc.accuracyMeters}m` : "N/A"}</TableCell>
                          <TableCell>{getFreshnessBadge(loc.freshnessStatus)}</TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {new Date(loc.recordedAt).toLocaleTimeString()}
                          </TableCell>
                          <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                            <div className="flex items-center justify-end gap-1.5">
                              {canDownloadReport && (
                                <Button
                                  size="xs"
                                  variant="outline"
                                  className="gap-1 text-xs text-primary border-primary/30 hover:bg-primary/10"
                                  onClick={() => {
                                    const member = members.find((m) => m.officerId === loc.officerId) || {
                                      id: loc.officerId,
                                      teamId: team?.id || "",
                                      officerId: loc.officerId,
                                      officerName: loc.officerName,
                                      designation: loc.designation,
                                      teamRole: "FIELD_OPERATOR" as const,
                                      joinedAt: loc.recordedAt
                                    }
                                    handleDownloadOfficerReport(member)
                                  }}
                                  title="Download Officer Dossier (PDF)"
                                >
                                  <FileDown className="w-3.5 h-3.5" />
                                  PDF
                                </Button>
                              )}
                              <Button
                                size="xs"
                                variant="outline"
                                className="gap-1 text-xs"
                                onClick={() => handleOpenOfficerModal(loc)}
                              >
                                <MapPin className="w-3.5 h-3.5 text-primary" />
                                View Map
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </Card>
        </TabsContent>

        {/* --- MEMBERS TAB --- */}
        <TabsContent value="members" className="space-y-4">
          <Card className="p-4 sm:p-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 mb-6">
              <div>
                <h3 className="text-lg font-bold">Roster & Hierarchy</h3>
                <p className="text-sm text-muted-foreground">Assigned law-enforcement personnel.</p>
              </div>
              {isPlanner && (
                <Button size="sm" onClick={() => setOpenAddMemberModal(true)} className="gap-2 w-full sm:w-auto justify-center">
                  <Plus className="w-4 h-4" />
                  Add Member
                </Button>
              )}
            </div>

            <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Officer Name</TableHead>
                  <TableHead>Designation</TableHead>
                  <TableHead>Team Role</TableHead>
                  <TableHead>Live Location / Area</TableHead>
                  <TableHead>Joined Date</TableHead>
                  {(isPlanner || canDownloadReport) && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.map((m) => {
                  const officerLoc = liveLocations.find((l) => l.officerId === m.officerId)
                  const area = officerLoc
                    ? trackingAreaNames[officerLoc.officerId] || getFastHeuristicArea(officerLoc.latitude, officerLoc.longitude)
                    : null

                  return (
                    <TableRow key={m.id}>
                      <TableCell className="font-semibold text-xs">
                        <button
                          type="button"
                          className="hover:underline text-left text-primary font-bold flex items-center gap-1.5 cursor-pointer"
                          onClick={() => handleOpenOfficerMapByMember(m)}
                        >
                          <Shield className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                          <span>{m.officerName || `Officer ${m.officerId.substring(0, 8)}...`}</span>
                        </button>
                      </TableCell>
                      <TableCell>{m.designation}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{m.teamRole.replace("_", " ")}</Badge>
                      </TableCell>
                      <TableCell>
                        {officerLoc ? (
                          <button
                            type="button"
                            onClick={() => handleOpenOfficerMapByMember(m)}
                            className="flex items-center gap-1.5 text-xs text-foreground hover:text-primary transition-colors text-left cursor-pointer"
                          >
                            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
                            <span className="font-medium truncate max-w-[200px]">{area || "Live on Map"}</span>
                          </button>
                        ) : (
                          <span className="text-xs text-muted-foreground">Offline / No GPS</span>
                        )}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(m.joinedAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell className="text-right space-x-2">
                        {canDownloadReport && (
                          <Button
                            size="xs"
                            variant="outline"
                            className="gap-1 text-xs text-primary border-primary/30 hover:bg-primary/10"
                            onClick={() => handleDownloadOfficerReport(m)}
                            title="Download Officer Dossier & Assigned Tasks (PDF)"
                          >
                            <FileDown className="w-3 h-3" />
                            Dossier PDF
                          </Button>
                        )}
                        {officerLoc && (
                          <Button
                            size="xs"
                            variant="outline"
                            className="gap-1 text-xs text-emerald-500 border-emerald-500/30 hover:bg-emerald-500/10"
                            onClick={() => handleOpenOfficerMapByMember(m)}
                          >
                            <MapPin className="w-3 h-3 text-emerald-500" />
                            Live Map
                          </Button>
                        )}
                        {isPlanner && (
                          <Button
                            size="xs"
                            variant="destructive"
                            onClick={() => handleRemoveMember(m.officerId)}
                          >
                            Remove
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
            </div>
          </Card>
        </TabsContent>

        {/* --- AUDIT TRAIL TAB --- */}
        <TabsContent value="audit" className="space-y-4">
          <Card className="p-4 sm:p-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 mb-4">
              <div>
                <h3 className="text-lg font-bold flex items-center gap-2">
                  <History className="w-5 h-5 text-primary" />
                  Unit Governance & Audit Trail
                </h3>
                <p className="text-sm text-muted-foreground">Immutable audit records for all team changes.</p>
              </div>
            </div>

            {auditLogs.length === 0 ? (
              <p className="text-sm text-muted-foreground">No audit entries logged yet for this team.</p>
            ) : (
              <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Action</TableHead>
                    <TableHead>Actor Identity</TableHead>
                    <TableHead>Remarks</TableHead>
                    <TableHead>Recorded At</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {auditLogs.map((log) => (
                    <TableRow key={log.id}>
                      <TableCell className="font-semibold text-xs">{log.action}</TableCell>
                      <TableCell>{log.changedById ? log.changedById.substring(0, 8) : "Supervisor"}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">{log.remarks}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(log.createdAt).toLocaleString()}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              </div>
            )}
          </Card>
        </TabsContent>

        {/* --- APPROVALS TAB --- */}
        {isSupervisor && (
          <TabsContent value="approvals" className="space-y-4">
            <Card className="p-4 sm:p-6">
              <h3 className="text-lg font-bold mb-4">Cross-Team Operation Authorizations</h3>
              {approvals.length === 0 ? (
                <p className="text-sm text-muted-foreground">No pending operation requests requiring authorization.</p>
              ) : (
                <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Operation</TableHead>
                      <TableHead>Target Team</TableHead>
                      <TableHead>Remarks</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {approvals.map((req) => (
                      <TableRow key={req.id}>
                        <TableCell className="font-semibold">{req.operationTitle}</TableCell>
                        <TableCell>{req.targetTeamName}</TableCell>
                        <TableCell className="text-xs text-muted-foreground">{req.requestRemarks}</TableCell>
                        <TableCell>
                          <Badge variant={req.status === "APPROVED" ? "success" : "warning"}>
                            {req.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right space-x-2">
                          {req.status === "PENDING" && (
                            <>
                              <Button size="xs" onClick={() => handleApproveRequest(req.id)}>
                                Approve
                              </Button>
                              <Button size="xs" variant="destructive" onClick={() => handleRejectRequest(req.id)}>
                                Reject
                              </Button>
                            </>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                </div>
              )}
            </Card>
          </TabsContent>
        )}
      </Tabs>

      {/* --- CREATE TASK MODAL --- */}
      <Dialog open={openTaskModal} onOpenChange={setOpenTaskModal}>
        <DialogContent className="w-[95vw] sm:max-w-md max-h-[90vh] overflow-y-auto">
          <form onSubmit={handleCreateTask}>
            <DialogHeader>
              <DialogTitle>Dispatch Tactical Task</DialogTitle>
              <DialogDescription>Create a team-scoped operational work item.</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="t-title">Task Title</Label>
                <Input id="t-title" value={taskTitle} onChange={(e) => setTaskTitle(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="t-action">Action Type</Label>
                <select
                  id="t-action"
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm"
                  value={taskAction}
                  onChange={(e) => setTaskAction(e.target.value)}
                >
                  <option value="PATROLLING">PATROLLING</option>
                  <option value="NAKABANDI">NAKABANDI</option>
                  <option value="BANDOBAST">BANDOBAST</option>
                  <option value="INVESTIGATION">INVESTIGATION</option>
                  <option value="GENERAL_DUTY">GENERAL DUTY</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="t-loc">Location Landmark</Label>
                <Input id="t-loc" value={taskLocation} onChange={(e) => setTaskLocation(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="t-desc">Instructions</Label>
                <Textarea id="t-desc" value={taskDesc} onChange={(e) => setTaskDesc(e.target.value)} />
              </div>
            </div>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setOpenTaskModal(false)}>Cancel</Button>
              <Button type="submit" className="w-full sm:w-auto">Dispatch Task</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* --- ASSIGN OFFICER MODAL --- */}
      <Dialog open={openAssignModal} onOpenChange={setOpenAssignModal}>
        <DialogContent className="w-[95vw] sm:max-w-md max-h-[90vh] overflow-y-auto">
          <form onSubmit={handleAssignOfficer}>
            <DialogHeader>
              <DialogTitle>Assign Officer to Task</DialogTitle>
              <DialogDescription>Deploy a team member to carry out this assignment.</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="a-officer">Select Officer</Label>
                <select
                  id="a-officer"
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm"
                  value={assignOfficerId}
                  onChange={(e) => setAssignOfficerId(e.target.value)}
                  required
                >
                  <option value="">-- Choose Officer --</option>
                  {members.map((m) => (
                    <option key={m.officerId} value={m.officerId}>
                      {m.officerName ? `${m.officerName} (${m.designation})` : m.designation} - {m.officerId.substring(0, 8)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="a-resp">Responsibility</Label>
                <Input id="a-resp" placeholder="e.g. Lead Patrol, Gate Check" value={responsibility} onChange={(e) => setResponsibility(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="a-inst">Specific Orders</Label>
                <Textarea id="a-inst" value={instructions} onChange={(e) => setInstructions(e.target.value)} />
              </div>
            </div>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setOpenAssignModal(false)}>Cancel</Button>
              <Button type="submit" className="w-full sm:w-auto">Assign</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* --- SUBMIT REPORT MODAL --- */}
      <Dialog open={openSubmitModal} onOpenChange={setOpenSubmitModal}>
        <DialogContent className="w-[95vw] sm:max-w-md max-h-[90vh] overflow-y-auto">
          <form onSubmit={handleSubmitReport}>
            <DialogHeader>
              <DialogTitle>Submit Completion Report</DialogTitle>
              <DialogDescription>Document actions taken during this assignment.</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="r-rep">Detailed Field Report</Label>
                <Textarea
                  id="r-rep"
                  rows={4}
                  placeholder="Summarize patrol route, incidents, or findings..."
                  value={completionReport}
                  onChange={(e) => setCompletionReport(e.target.value)}
                  required
                />
              </div>
            </div>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setOpenSubmitModal(false)}>Cancel</Button>
              <Button type="submit" className="w-full sm:w-auto">Submit to Team Lead</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* --- ADD MEMBER MODAL (Redis-Powered Search by Name & Police Post) --- */}
      <Dialog open={openAddMemberModal} onOpenChange={setOpenAddMemberModal}>
        <DialogContent className="w-[95vw] sm:max-w-lg max-h-[90vh] overflow-y-auto">
          <form onSubmit={handleAddMember}>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <UserPlus className="w-5 h-5 text-primary" />
                Add Officer to Unit
              </DialogTitle>
              <DialogDescription>
                Search and select an officer by Name or Police Post (fast cached directory).
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-3">
              {/* Search Field */}
              <div className="space-y-1.5">
                <Label htmlFor="officer-search">Search Officer Directory</Label>
                <div className="relative">
                  <Search className="w-4 h-4 absolute left-3 top-2.5 text-muted-foreground" />
                  <Input
                    id="officer-search"
                    placeholder="Search by name, post, or designation (e.g. Wakad, Pawar)..."
                    value={officerSearchQuery}
                    onChange={(e) => setOfficerSearchQuery(e.target.value)}
                    className="pl-9 h-9 text-sm"
                  />
                </div>
              </div>

              {/* Officer Selection List */}
              <div className="space-y-1.5">
                <div className="flex justify-between items-center text-xs text-muted-foreground">
                  <span>Available Personnel ({filteredOfficers.length})</span>
                  <span>Click an officer to select</span>
                </div>

                <div className="max-h-56 overflow-y-auto space-y-1.5 border rounded-lg p-2 bg-muted/20">
                  {loadingAvailableOfficers ? (
                    <div className="p-6 text-center text-xs text-muted-foreground space-y-2">
                      <RefreshCw className="w-4 h-4 animate-spin mx-auto text-primary" />
                      <span>Loading from Redis cache...</span>
                    </div>
                  ) : filteredOfficers.length === 0 ? (
                    <div className="p-6 text-center text-xs text-muted-foreground">
                      No matching officers found for &quot;{officerSearchQuery}&quot;
                    </div>
                  ) : (
                    filteredOfficers.map((officer) => {
                      const isSelected = newOfficerId === officer.officerId
                      return (
                        <div
                          key={officer.officerId}
                          onClick={() => setNewOfficerId(officer.officerId)}
                          className={`p-2.5 rounded-md border cursor-pointer transition-all flex items-center justify-between text-xs select-none ${
                            isSelected
                              ? "border-primary bg-primary/10 shadow-sm"
                              : "border-border/60 hover:bg-muted/60"
                          }`}
                        >
                          <div className="space-y-1">
                            <div className="font-semibold text-foreground flex items-center gap-1.5">
                              <Shield className="w-3.5 h-3.5 text-primary shrink-0" />
                              <span className="text-sm">{officer.name}</span>
                              <Badge variant="outline" className="text-[10px] py-0 px-1.5">
                                {officer.designation}
                              </Badge>
                            </div>
                            <div className="flex items-center gap-2 text-muted-foreground flex-wrap">
                              <span className="flex items-center gap-1">
                                <MapPin className="w-3 h-3 text-rose-500 shrink-0" />
                                Police Post: <strong className="text-foreground">{officer.policePost}</strong>
                              </span>
                              <span>•</span>
                              <span>{officer.email}</span>
                            </div>
                          </div>

                          <div className="shrink-0 pl-2">
                            {isSelected ? (
                              <Badge variant="default" className="text-[10px] gap-1 bg-primary text-primary-foreground">
                                <Check className="w-3 h-3" /> Selected
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="text-[10px] text-muted-foreground">
                                Select
                              </Badge>
                            )}
                          </div>
                        </div>
                      )
                    })
                  )}
                </div>
              </div>

              {/* Selected Officer Summary */}
              {selectedOfficer && (
                <div className="p-2.5 rounded-lg border border-primary/40 bg-primary/5 text-xs flex justify-between items-center">
                  <div className="space-y-0.5">
                    <div className="font-semibold text-foreground flex items-center gap-1">
                      <UserCheck className="w-3.5 h-3.5 text-primary" /> {selectedOfficer.name}
                    </div>
                    <div className="text-muted-foreground">
                      Police Post: <span className="font-medium text-foreground">{selectedOfficer.policePost}</span> ({selectedOfficer.designation})
                    </div>
                  </div>
                  <Badge variant="secondary" className="text-[10px]">Selected</Badge>
                </div>
              )}

              {/* Role in Team */}
              <div className="space-y-1.5">
                <Label htmlFor="m-role">Assigned Role in this Unit</Label>
                <select
                  id="m-role"
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm"
                  value={newTeamRole}
                  onChange={(e) => setNewTeamRole(e.target.value)}
                >
                  <option value="FIELD_OPERATOR">Field Operator (Ground / Beat)</option>
                  <option value="CONTROL_OPERATOR">Control Operator (Tactical Map & Dispatch)</option>
                  <option value="TEAM_LEAD">Team Lead (Field Command & Task Assignment)</option>
                  <option value="SENIOR_PLANNER">Senior Planner (Task Creation & Review)</option>
                  <option value="ADMINISTRATOR">Administrator (Unit Oversight)</option>
                </select>
              </div>
            </div>

            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setOpenAddMemberModal(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={!newOfficerId} className="w-full sm:w-auto">
                Add Officer to Unit
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* --- CREATE OPERATION MODAL --- */}
      <Dialog open={openOperationModal} onOpenChange={setOpenOperationModal}>
        <DialogContent className="w-[95vw] sm:max-w-md max-h-[90vh] overflow-y-auto">
          <form onSubmit={handleCreateOperation}>
            <DialogHeader>
              <DialogTitle>Create Operation</DialogTitle>
              <DialogDescription>Establish a high-priority tactical operation.</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="op-title">Operation Title</Label>
                <Input id="op-title" value={opTitle} onChange={(e) => setOpTitle(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="op-type">Operation Type</Label>
                <select
                  id="op-type"
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm"
                  value={opType}
                  onChange={(e) => setOpType(e.target.value)}
                >
                  <option value="BANDOBAST">BANDOBAST</option>
                  <option value="PATROLLING">PATROLLING</option>
                  <option value="NAKABANDI">NAKABANDI</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="op-desc">Briefing</Label>
                <Textarea id="op-desc" value={opDesc} onChange={(e) => setOpDesc(e.target.value)} />
              </div>
            </div>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setOpenOperationModal(false)}>Cancel</Button>
              <Button type="submit" className="w-full sm:w-auto">Establish Operation</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* --- OFFICER LIVE LOCATION MAP MODAL --- */}
      <OfficerMapModal
        open={mapModalOpen}
        onOpenChange={setMapModalOpen}
        location={selectedOfficerLocation}
        officerRole={selectedOfficerRole}
        onDownloadReport={
          canDownloadReport && selectedOfficerLocation
            ? () => {
                const member = members.find((m) => m.officerId === selectedOfficerLocation.officerId) || {
                  id: selectedOfficerLocation.officerId,
                  teamId: team?.id || "",
                  officerId: selectedOfficerLocation.officerId,
                  officerName: selectedOfficerLocation.officerName,
                  designation: selectedOfficerLocation.designation,
                  teamRole: (selectedOfficerRole || "FIELD_OPERATOR") as any,
                  joinedAt: selectedOfficerLocation.recordedAt
                }
                handleDownloadOfficerReport(member)
              }
            : undefined
        }
      />
    </div>
  )
}
