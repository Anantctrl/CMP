import apiClient from "@/config/apiClient"

export interface Task {
  id: string
  teamId?: string
  title: string
  actionType: string
  description?: string
  locationName?: string
  latitude?: number
  longitude?: number
  createdBy: string
  status: string
  assignedOfficerNames?: string[]
}

export interface TaskAssignment {
  id: string
  taskId: string
  officerId: string
  officerName?: string
  officerDesignation?: string
  responsibility: string
  instructions?: string
  status: "ASSIGNED" | "IN_PROGRESS" | "SUBMITTED" | "COMPLETED" | "CANCELLED"
  assignedAt: string
  startedAt?: string
  submittedAt?: string
  completedAt?: string
  completionReport?: string
  reviewRemarks?: string
}

export async function getTasks(teamId?: string): Promise<Task[]> {
  const res = await apiClient.get<Task[]>("/tasks", {
    params: teamId ? { teamId } : {}
  })
  return res.data
}

export async function createTask(data: {
  teamId?: string
  title: string
  actionType: string
  description?: string
  locationName?: string
  latitude?: number
  longitude?: number
}): Promise<Task> {
  const res = await apiClient.post<Task>("/tasks", data)
  return res.data
}

export async function getTaskAssignments(taskId: string): Promise<TaskAssignment[]> {
  const res = await apiClient.get<TaskAssignment[]>(`/assignments/tasks/${taskId}`)
  return res.data
}

export async function assignOfficer(taskId: string, data: {
  officerId: string
  responsibility: string
  instructions?: string
}): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/tasks/${taskId}/assign`, data)
  return res.data
}

export async function getMyAssignments(): Promise<TaskAssignment[]> {
  const res = await apiClient.get<TaskAssignment[]>("/assignments/my-assignments")
  return res.data
}

export async function reassignOfficer(assignmentId: string, data: {
  officerId: string
  responsibility: string
  instructions?: string
  reason: string
}): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/${assignmentId}/reassign`, data)
  return res.data
}

export async function startAssignment(assignmentId: string): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/${assignmentId}/start`)
  return res.data
}

export async function submitAssignment(assignmentId: string, completionReport: string): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/${assignmentId}/submit`, {
    completionReport
  })
  return res.data
}

export async function approveAssignment(assignmentId: string, remarks?: string): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/${assignmentId}/approve`, {
    remarks: remarks || "Approved"
  })
  return res.data
}

export async function requestCorrection(assignmentId: string, remarks?: string): Promise<TaskAssignment> {
  const res = await apiClient.post<TaskAssignment>(`/assignments/${assignmentId}/request-correction`, {
    remarks: remarks || "Correction requested"
  })
  return res.data
}
