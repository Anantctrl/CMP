import jsPDF from "jspdf"
import autoTable from "jspdf-autotable"
import type { Team, TeamMember } from "@/services/teamService"
import type { Task, TaskAssignment } from "@/services/taskService"
import type { Operation } from "@/services/operationService"
import type { OfficerLiveLocation } from "@/services/trackingService"
import { getFastHeuristicArea } from "@/services/geoService"

export interface TeamReportData {
  team: Team
  members: TeamMember[]
  tasks: Task[]
  taskAssignmentsMap: Record<string, TaskAssignment[]>
  operations: Operation[]
  liveLocations: OfficerLiveLocation[]
  trackingAreaNames?: Record<string, string>
  generatedBy?: {
    name?: string
    email?: string
    role?: string
  }
}

export interface OfficerReportData {
  officer: TeamMember
  team?: Team | null
  tasks: Task[]
  taskAssignmentsMap: Record<string, TaskAssignment[]>
  liveLocation?: OfficerLiveLocation | null
  policePost?: string
  detectedArea?: string
  generatedBy?: {
    name?: string
    email?: string
    role?: string
  }
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return "N/A"
  try {
    const d = new Date(dateStr)
    return isNaN(d.getTime()) ? "N/A" : d.toLocaleDateString()
  } catch {
    return "N/A"
  }
}

function formatDateTime(dateStr?: string | null): string {
  if (!dateStr) return "N/A"
  try {
    const d = new Date(dateStr)
    return isNaN(d.getTime()) ? "N/A" : d.toLocaleString()
  } catch {
    return "N/A"
  }
}

/**
 * Generate Comprehensive Team Tactical Dossier (PDF)
 */
export function generateTeamReportPDF(data: TeamReportData): void {
  const {
    team,
    members,
    tasks,
    taskAssignmentsMap,
    operations,
    liveLocations,
    trackingAreaNames = {},
    generatedBy
  } = data

  const doc = new jsPDF({
    orientation: "portrait",
    unit: "mm",
    format: "a4"
  })

  // 1. Top Bar & Header
  doc.setFillColor(15, 23, 42) // Slate 900
  doc.rect(0, 0, 210, 24, "F")

  doc.setFillColor(2, 132, 199) // Sky 600 accent stripe
  doc.rect(0, 0, 210, 2.5, "F")

  doc.setFont("helvetica", "bold")
  doc.setFontSize(8.5)
  doc.setTextColor(224, 242, 254) // Sky 100
  doc.text("POLICE DEPARTMENT • TACTICAL OPERATIONS COMMAND", 14, 9)

  doc.setFontSize(7.5)
  doc.setTextColor(252, 165, 165) // Light red
  doc.text("CONFIDENTIAL // LAW ENFORCEMENT SENSITIVE (LES)", 196, 9, { align: "right" })

  doc.setFontSize(13)
  doc.setTextColor(255, 255, 255)
  doc.text("TACTICAL UNIT OPERATIONAL DOSSIER", 14, 18)

  // 2. Unit Identity Block
  let currentY = 32

  doc.setFont("helvetica", "bold")
  doc.setFontSize(15)
  doc.setTextColor(15, 23, 42)
  doc.text(team.name.toUpperCase(), 14, currentY)

  currentY += 5.5
  doc.setFont("helvetica", "normal")
  doc.setFontSize(8.5)
  doc.setTextColor(100, 116, 139)
  doc.text(
    `Unit ID: ${team.id}  •  Role: ${(team.userRoleInTeam || "N/A").replace("_", " ")}  •  Created: ${formatDate(team.createdAt)}`,
    14,
    currentY
  )

  if (team.description) {
    currentY += 4.5
    doc.text(`Description: ${team.description}`, 14, currentY)
  }

  currentY += 4.5
  doc.text(
    `Generated: ${new Date().toLocaleString()}  •  Authorized By: ${generatedBy?.name || generatedBy?.email || "Command Staff"} (${generatedBy?.role || "Authorized Role"})`,
    14,
    currentY
  )

  // 3. Executive KPI Snapshot Cards
  currentY += 7
  const cardWidth = 43
  const cardHeight = 15
  const cardGap = 3.3
  const startX = 14

  const totalAssignments = Object.values(taskAssignmentsMap).reduce((acc, arr) => acc + arr.length, 0)
  const completedAssignments = Object.values(taskAssignmentsMap).reduce(
    (acc, arr) => acc + arr.filter((a) => a.status === "COMPLETED").length,
    0
  )
  const activeOps = operations.filter((op) => op.status === "ACTIVE" || op.status === "PLANNED").length
  const liveOfficersCount = liveLocations.length

  const kpis = [
    { label: "FORCE ENROLLED", val: `${members.length} Officers` },
    { label: "ACTIVE OPERATIONS", val: `${activeOps} / ${operations.length}` },
    { label: "TASKS DISPATCHED", val: `${tasks.length} (${totalAssignments} Asg)` },
    { label: "COMPLETED / LIVE", val: `${completedAssignments} Done • ${liveOfficersCount} GPS` }
  ]

  kpis.forEach((kpi, idx) => {
    const x = startX + idx * (cardWidth + cardGap)
    doc.setFillColor(248, 250, 252)
    doc.setDrawColor(226, 232, 240)
    doc.roundedRect(x, currentY, cardWidth, cardHeight, 1.5, 1.5, "FD")

    doc.setFont("helvetica", "bold")
    doc.setFontSize(6.5)
    doc.setTextColor(100, 116, 139)
    doc.text(kpi.label, x + 3, currentY + 4.5)

    doc.setFontSize(9.5)
    doc.setTextColor(15, 23, 42)
    doc.text(kpi.val, x + 3, currentY + 11.5)
  })

  currentY += cardHeight + 8

  // 4. SECTION 1: HIGH-PRIORITY OPERATIONS
  doc.setFont("helvetica", "bold")
  doc.setFontSize(10.5)
  doc.setTextColor(15, 23, 42)
  doc.text(`1. HIGH-PRIORITY OPERATIONS (${operations.length})`, 14, currentY)
  currentY += 3

  if (operations.length === 0) {
    doc.setFont("helvetica", "italic")
    doc.setFontSize(8)
    doc.setTextColor(148, 163, 184)
    doc.text("No operations recorded for this unit.", 14, currentY + 4)
    currentY += 10
  } else {
    const opRows = operations.map((op) => [
      op.title,
      op.type,
      op.status,
      `${formatDate(op.plannedStartAt || op.actualStartAt)} - ${formatDate(op.plannedEndAt || op.actualEndAt)}`,
      op.description || "N/A"
    ])

    autoTable(doc, {
      startY: currentY,
      head: [["Operation Title", "Type", "Status", "Schedule / Timeline", "Briefing / Scope"]],
      body: opRows,
      margin: { left: 14, right: 14 },
      theme: "grid",
      headStyles: {
        fillColor: [30, 41, 59],
        textColor: [255, 255, 255],
        fontStyle: "bold",
        fontSize: 7.5
      },
      styles: {
        fontSize: 7.5,
        cellPadding: 2,
        overflow: "linebreak"
      },
      columnStyles: {
        0: { cellWidth: 40, fontStyle: "bold" },
        1: { cellWidth: 26 },
        2: { cellWidth: 24 },
        3: { cellWidth: 32 },
        4: { cellWidth: "auto" }
      }
    })

    currentY = (doc as any).lastAutoTable.finalY + 8
  }

  // 5. SECTION 2: TACTICAL TASKS & OFFICER ASSIGNMENTS
  if (currentY > 230) {
    doc.addPage()
    currentY = 20
  }

  doc.setFont("helvetica", "bold")
  doc.setFontSize(10.5)
  doc.setTextColor(15, 23, 42)
  doc.text(`2. TACTICAL TASKS & PERSONNEL DEPLOYMENT (${tasks.length})`, 14, currentY)
  currentY += 4

  if (tasks.length === 0) {
    doc.setFont("helvetica", "italic")
    doc.setFontSize(8)
    doc.setTextColor(148, 163, 184)
    doc.text("No tactical tasks currently dispatched for this unit.", 14, currentY + 4)
    currentY += 10
  } else {
    // Flatten tasks and sub-assignments into a clean unified operational action matrix
    const taskRows: (string | number)[][] = []

    tasks.forEach((task) => {
      const assigns = taskAssignmentsMap[task.id] || []
      if (assigns.length === 0) {
        taskRows.push([
          task.title,
          task.actionType,
          task.locationName || "Landmark Unset",
          task.status,
          "Unassigned",
          "Pending officer assignment",
          task.description || "N/A"
        ])
      } else {
        assigns.forEach((a, aIdx) => {
          const officerDisplay = a.officerName
            ? `${a.officerName} (${a.officerDesignation || "Officer"})`
            : `Officer ${a.officerId.substring(0, 8)}`

          const notes = a.completionReport
            ? `Report: ${a.completionReport}`
            : a.instructions
            ? `Orders: ${a.instructions}`
            : "No field report"

          taskRows.push([
            aIdx === 0 ? task.title : `↳ (Cont.) ${task.title}`,
            task.actionType,
            task.locationName || "-",
            a.status,
            officerDisplay,
            a.responsibility,
            notes
          ])
        })
      }
    })

    autoTable(doc, {
      startY: currentY,
      head: [["Task Title", "Action", "Location", "Duty Status", "Deployed Officer", "Responsibility", "Field Notes / Orders"]],
      body: taskRows,
      margin: { left: 14, right: 14 },
      theme: "grid",
      headStyles: {
        fillColor: [30, 41, 59],
        textColor: [255, 255, 255],
        fontStyle: "bold",
        fontSize: 7
      },
      styles: {
        fontSize: 7,
        cellPadding: 2,
        overflow: "linebreak"
      },
      columnStyles: {
        0: { cellWidth: 32, fontStyle: "bold" },
        1: { cellWidth: 22 },
        2: { cellWidth: 22 },
        3: { cellWidth: 20 },
        4: { cellWidth: 30 },
        5: { cellWidth: 26 },
        6: { cellWidth: "auto" }
      }
    })

    currentY = (doc as any).lastAutoTable.finalY + 8
  }

  // 6. SECTION 3: PERSONNEL ROSTER & LIVE TELEMETRY
  if (currentY > 220) {
    doc.addPage()
    currentY = 20
  }

  doc.setFont("helvetica", "bold")
  doc.setFontSize(10.5)
  doc.setTextColor(15, 23, 42)
  doc.text(`3. UNIT PERSONNEL ROSTER & TELEMETRY (${members.length})`, 14, currentY)
  currentY += 4

  const memberRows = members.map((m) => {
    const loc = liveLocations.find((l) => l.officerId === m.officerId)
    const detectedArea = loc
      ? trackingAreaNames[loc.officerId] || getFastHeuristicArea(loc.latitude, loc.longitude)
      : "Offline"
    const coords = loc ? `${loc.latitude.toFixed(4)}, ${loc.longitude.toFixed(4)}` : "No GPS"
    const freshness = loc ? loc.freshnessStatus : "OFFLINE"

    return [
      m.officerName || `Officer ${m.officerId.substring(0, 8)}`,
      m.designation,
      m.teamRole.replace("_", " "),
      freshness,
      detectedArea,
      coords,
      formatDate(m.joinedAt)
    ]
  })

  autoTable(doc, {
    startY: currentY,
    head: [["Officer Name", "Designation", "Unit Role", "Signal Status", "Detected Sector / Area", "GPS Coordinates", "Joined Date"]],
    body: memberRows,
    margin: { left: 14, right: 14 },
    theme: "grid",
    headStyles: {
      fillColor: [30, 41, 59],
      textColor: [255, 255, 255],
      fontStyle: "bold",
      fontSize: 7.5
    },
    styles: {
      fontSize: 7.5,
      cellPadding: 2,
      overflow: "linebreak"
    },
    columnStyles: {
      0: { cellWidth: 32, fontStyle: "bold" },
      1: { cellWidth: 25 },
      2: { cellWidth: 25 },
      3: { cellWidth: 22 },
      4: { cellWidth: 32 },
      5: { cellWidth: 26 },
      6: { cellWidth: 20 }
    }
  })

  currentY = (doc as any).lastAutoTable.finalY + 12

  // 7. Verification & Sign-off Block
  if (currentY > 240) {
    doc.addPage()
    currentY = 25
  }

  doc.setFont("helvetica", "bold")
  doc.setFontSize(8)
  doc.setTextColor(100, 116, 139)
  doc.text("COMMAND VERIFICATION & AUDIT SIGN-OFF", 14, currentY)
  currentY += 2
  doc.setDrawColor(203, 213, 225)
  doc.line(14, currentY, 196, currentY)
  currentY += 10

  doc.setFont("helvetica", "normal")
  doc.setFontSize(7.5)
  doc.text("Lead / Senior Planner Signature: _______________________________", 14, currentY)
  doc.text("Supervisor Endorsement: _______________________________", 110, currentY)

  currentY += 6
  doc.text(`Authenticated Date: ${new Date().toLocaleDateString()}`, 14, currentY)
  doc.text("Official Seal: [ COPMAP TACTICAL COMMAND VERIFIED ]", 110, currentY)

  // 8. Running Headers & Footers on all pages
  const totalPages = doc.getNumberOfPages()
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i)

    // Running header on page 2 onwards
    if (i > 1) {
      doc.setFont("helvetica", "bold")
      doc.setFontSize(7)
      doc.setTextColor(148, 163, 184)
      doc.text(`COPMAP TACTICAL REPORT • ${team.name.toUpperCase()}`, 14, 10)
      doc.text("CONFIDENTIAL // LAW ENFORCEMENT SENSITIVE", 196, 10, { align: "right" })
      doc.setDrawColor(226, 232, 240)
      doc.line(14, 12, 196, 12)
    }

    // Running footer on every page
    doc.setFont("helvetica", "normal")
    doc.setFontSize(7)
    doc.setTextColor(148, 163, 184)
    doc.setDrawColor(226, 232, 240)
    doc.line(14, 285, 196, 285)
    doc.text("COPMAP LAW ENFORCEMENT INTELLIGENCE SYSTEM • AUTOMATED OPERATIONAL AUDIT", 14, 290)
    doc.text(`Page ${i} of ${totalPages}`, 196, 290, { align: "right" })
  }

  // 9. Save PDF file
  const sanitizedTeamName = team.name.replace(/[^a-zA-Z0-9_-]/g, "_")
  const dateStr = new Date().toISOString().slice(0, 10)
  doc.save(`${sanitizedTeamName}_Tactical_Dossier_${dateStr}.pdf`)
}

/**
 * Generate Individual Officer Dossier & Assigned Tasks Report (PDF)
 */
export function generateOfficerReportPDF(data: OfficerReportData): void {
  const {
    officer,
    team,
    tasks,
    taskAssignmentsMap,
    liveLocation,
    policePost,
    detectedArea,
    generatedBy
  } = data

  const doc = new jsPDF({
    orientation: "portrait",
    unit: "mm",
    format: "a4"
  })

  // 1. Top Bar & Header
  doc.setFillColor(15, 23, 42) // Slate 900
  doc.rect(0, 0, 210, 24, "F")

  doc.setFillColor(37, 99, 235) // Blue 600 accent stripe
  doc.rect(0, 0, 210, 2.5, "F")

  doc.setFont("helvetica", "bold")
  doc.setFontSize(8.5)
  doc.setTextColor(219, 234, 254) // Blue 100
  doc.text("POLICE FORCE PERSONNEL DOSSIER • ASSIGNED TACTICAL DUTIES", 14, 9)

  doc.setFontSize(7.5)
  doc.setTextColor(252, 165, 165) // Light red
  doc.text("OFFICIAL USE ONLY // LAW ENFORCEMENT SENSITIVE (LES)", 196, 9, { align: "right" })

  doc.setFontSize(13)
  doc.setTextColor(255, 255, 255)
  doc.text("OFFICER PERFORMANCE & ASSIGNMENT AUDIT", 14, 18)

  // 2. Officer Details Identity Box
  let currentY = 32

  doc.setFont("helvetica", "bold")
  doc.setFontSize(15)
  doc.setTextColor(15, 23, 42)
  const officerNameDisplay = officer.officerName || `Officer (${officer.officerId.substring(0, 8)})`
  doc.text(officerNameDisplay.toUpperCase(), 14, currentY)

  currentY += 5.5
  doc.setFont("helvetica", "normal")
  doc.setFontSize(8.5)
  doc.setTextColor(100, 116, 139)
  doc.text(
    `Designation: ${officer.designation}  •  Unit: ${team?.name || "Tactical Unit"} (${officer.teamRole.replace("_", " ")})`,
    14,
    currentY
  )

  currentY += 4.5
  doc.text(
    `Police Post / Base: ${policePost || "Central Police Post"}  •  Officer ID: ${officer.officerId}`,
    14,
    currentY
  )

  currentY += 4.5
  doc.text(
    `Enrolled Since: ${formatDate(officer.joinedAt)}  •  Generated: ${new Date().toLocaleString()} by ${generatedBy?.name || generatedBy?.email || "Command Staff"}`,
    14,
    currentY
  )

  // 3. Live Telemetry & GPS Sector Box
  currentY += 6
  doc.setFillColor(241, 245, 249)
  doc.setDrawColor(203, 213, 225)
  doc.roundedRect(14, currentY, 182, 14, 1.5, 1.5, "FD")

  const gpsStatus = liveLocation ? liveLocation.freshnessStatus : "OFFLINE / NO FIX"
  const areaDisplay = detectedArea || (liveLocation ? `${liveLocation.latitude.toFixed(4)}, ${liveLocation.longitude.toFixed(4)}` : "No GPS Fix Reported")
  const lastPing = liveLocation?.recordedAt ? formatDateTime(liveLocation.recordedAt) : "N/A"

  doc.setFont("helvetica", "bold")
  doc.setFontSize(7.5)
  doc.setTextColor(30, 41, 59)
  doc.text(`GPS STATUS: ${gpsStatus}`, 18, currentY + 5.5)

  doc.setFont("helvetica", "normal")
  doc.text(`Detected Area: ${areaDisplay}`, 75, currentY + 5.5)

  doc.text(`Last Fix: ${lastPing} ${liveLocation?.accuracyMeters ? `(±${liveLocation.accuracyMeters}m)` : ""}`, 18, currentY + 10.5)

  currentY += 19

  // 4. Calculate Officer-Specific Assignment Metrics
  const officerAssignments: {
    assignment: TaskAssignment
    task?: Task
  }[] = []

  Object.entries(taskAssignmentsMap).forEach(([taskId, assigns]) => {
    const parentTask = tasks.find((t) => t.id === taskId)
    assigns.forEach((a) => {
      if (a.officerId === officer.officerId) {
        officerAssignments.push({ assignment: a, task: parentTask })
      }
    })
  })

  const totalAssigned = officerAssignments.length
  const completedCount = officerAssignments.filter((item) => item.assignment.status === "COMPLETED").length
  const inProgressCount = officerAssignments.filter((item) => item.assignment.status === "IN_PROGRESS").length
  const pendingCount = officerAssignments.filter((item) => item.assignment.status === "ASSIGNED" || item.assignment.status === "SUBMITTED").length

  // KPI Summary Cards
  const cardWidth = 43
  const cardHeight = 14
  const cardGap = 3.3
  const startX = 14

  const kpis = [
    { label: "TOTAL DUTIES ASSIGNED", val: `${totalAssigned} Duties` },
    { label: "COMPLETED & VERIFIED", val: `${completedCount} Tasks` },
    { label: "ACTIVE IN-PROGRESS", val: `${inProgressCount} Tasks` },
    { label: "PENDING REVIEW / QUEUE", val: `${pendingCount} Tasks` }
  ]

  kpis.forEach((kpi, idx) => {
    const x = startX + idx * (cardWidth + cardGap)
    doc.setFillColor(248, 250, 252)
    doc.setDrawColor(226, 232, 240)
    doc.roundedRect(x, currentY, cardWidth, cardHeight, 1.5, 1.5, "FD")

    doc.setFont("helvetica", "bold")
    doc.setFontSize(6.5)
    doc.setTextColor(100, 116, 139)
    doc.text(kpi.label, x + 3, currentY + 4)

    doc.setFontSize(9)
    doc.setTextColor(15, 23, 42)
    doc.text(kpi.val, x + 3, currentY + 10.5)
  })

  currentY += cardHeight + 8

  // 5. Assigned Duties & Field Dispatches Table
  doc.setFont("helvetica", "bold")
  doc.setFontSize(10.5)
  doc.setTextColor(15, 23, 42)
  doc.text(`ASSIGNED TACTICAL TASKS & FIELD DISPATCHES (${totalAssigned})`, 14, currentY)
  currentY += 4

  if (officerAssignments.length === 0) {
    doc.setFont("helvetica", "italic")
    doc.setFontSize(8)
    doc.setTextColor(148, 163, 184)
    doc.text("No tactical tasks or duties currently assigned to this officer.", 14, currentY + 4)
    currentY += 12
  } else {
    const dutyRows = officerAssignments.map(({ assignment: a, task: t }) => {
      const taskTitle = t ? t.title : "Tactical Task"
      const actionType = t ? t.actionType : "DUTY"
      const loc = t?.locationName || "Landmark Unset"

      const timeline = [
        `Assigned: ${formatDate(a.assignedAt)}`,
        a.startedAt ? `Started: ${formatDate(a.startedAt)}` : null,
        a.completedAt ? `Done: ${formatDate(a.completedAt)}` : null
      ]
        .filter(Boolean)
        .join("\n")

      const report = a.completionReport
        ? `Report: ${a.completionReport}${a.reviewRemarks ? `\nReview: ${a.reviewRemarks}` : ""}`
        : a.instructions
        ? `Orders: ${a.instructions}`
        : "Pending report submission"

      return [
        taskTitle,
        actionType,
        loc,
        a.responsibility,
        a.status,
        timeline,
        report
      ]
    })

    autoTable(doc, {
      startY: currentY,
      head: [["Task Title", "Action Type", "Location Landmark", "Assigned Responsibility", "Status", "Duty Timeline", "Field Completion Report / Orders"]],
      body: dutyRows,
      margin: { left: 14, right: 14 },
      theme: "grid",
      headStyles: {
        fillColor: [30, 41, 59],
        textColor: [255, 255, 255],
        fontStyle: "bold",
        fontSize: 7.5
      },
      styles: {
        fontSize: 7.5,
        cellPadding: 2,
        overflow: "linebreak"
      },
      columnStyles: {
        0: { cellWidth: 32, fontStyle: "bold" },
        1: { cellWidth: 22 },
        2: { cellWidth: 22 },
        3: { cellWidth: 26 },
        4: { cellWidth: 20 },
        5: { cellWidth: 25 },
        6: { cellWidth: "auto" }
      }
    })

    currentY = (doc as any).lastAutoTable.finalY + 12
  }

  // 6. Sign-off & Verification Section
  if (currentY > 240) {
    doc.addPage()
    currentY = 25
  }

  doc.setFont("helvetica", "bold")
  doc.setFontSize(8)
  doc.setTextColor(100, 116, 139)
  doc.text("FIELD PERSONNEL VERIFICATION & ENDORSEMENT", 14, currentY)
  currentY += 2
  doc.setDrawColor(203, 213, 225)
  doc.line(14, currentY, 196, currentY)
  currentY += 10

  doc.setFont("helvetica", "normal")
  doc.setFontSize(7.5)
  doc.text("Officer Signature: _______________________________", 14, currentY)
  doc.text("Commanding Supervisor: _______________________________", 110, currentY)

  currentY += 6
  doc.text(`Audit Date: ${new Date().toLocaleDateString()}`, 14, currentY)
  doc.text("Official Status: [ CERTIFIED TACTICAL DOSSIER ]", 110, currentY)

  // 7. Running Headers & Footers on all pages
  const totalPages = doc.getNumberOfPages()
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i)

    if (i > 1) {
      doc.setFont("helvetica", "bold")
      doc.setFontSize(7)
      doc.setTextColor(148, 163, 184)
      doc.text(`OFFICER DOSSIER • ${officerNameDisplay.toUpperCase()}`, 14, 10)
      doc.text("OFFICIAL USE ONLY // LAW ENFORCEMENT SENSITIVE", 196, 10, { align: "right" })
      doc.setDrawColor(226, 232, 240)
      doc.line(14, 12, 196, 12)
    }

    doc.setFont("helvetica", "normal")
    doc.setFontSize(7)
    doc.setTextColor(148, 163, 184)
    doc.setDrawColor(226, 232, 240)
    doc.line(14, 285, 196, 285)
    doc.text("COPMAP LAW ENFORCEMENT INTELLIGENCE SYSTEM • OFFICER DUTY AUDIT", 14, 290)
    doc.text(`Page ${i} of ${totalPages}`, 196, 290, { align: "right" })
  }

  // 8. Save PDF file
  const sanitizedOfficerName = (officer.officerName || officer.officerId).replace(/[^a-zA-Z0-9_-]/g, "_")
  const dateStr = new Date().toISOString().slice(0, 10)
  doc.save(`Officer_${sanitizedOfficerName}_Dossier_${dateStr}.pdf`)
}
