import apiClient from "@/config/apiClient"

export interface Operation {
  id: string
  type: "BANDOBAST" | "PATROLLING" | "NAKABANDI"
  title: string
  description?: string
  plannedStartAt?: string
  plannedEndAt?: string
  actualStartAt?: string
  actualEndAt?: string
  status: "DRAFT" | "PLANNED" | "ACTIVE" | "PAUSED" | "COMPLETED" | "CANCELLED"
  createdByOfficerId: string
  participatingTeamIds: string[]
  pendingApproval: boolean
  createdAt: string
  updatedAt: string
}

export interface OperationApprovalRequest {
  id: string
  operationId: string
  operationTitle: string
  operationType: string
  requestedById: string
  targetTeamId: string
  targetTeamName: string
  status: "PENDING" | "APPROVED" | "REJECTED"
  reviewedById?: string
  reviewedAt?: string
  requestRemarks?: string
  reviewRemarks?: string
  createdAt: string
}

export interface OperationLocation {
  id: string
  operationId: string
  name: string
  description?: string
  address?: string
  latitude: number
  longitude: number
  requiredManpower: number
  sequenceOrder: number
  createdAt: string
}

export async function getOperations(type?: string, status?: string): Promise<Operation[]> {
  const res = await apiClient.get<Operation[]>("/operations", {
    params: { type, status }
  })
  return res.data
}

export async function getOperation(operationId: string): Promise<Operation> {
  const res = await apiClient.get<Operation>(`/operations/${operationId}`)
  return res.data
}

export async function createOperation(data: {
  type: string
  title: string
  description?: string
  teamIds?: string[]
  requestRemarks?: string
}): Promise<Operation> {
  const res = await apiClient.post<Operation>("/operations", data)
  return res.data
}

export async function scheduleOperation(
  operationId: string,
  plannedStartAt: string,
  plannedEndAt: string,
  remarks?: string
): Promise<Operation> {
  const res = await apiClient.put<Operation>(`/operations/${operationId}/schedule`, {
    plannedStartAt,
    plannedEndAt,
    remarks
  })
  return res.data
}

export async function changeOperationStatus(
  operationId: string,
  status: string,
  remarks?: string
): Promise<Operation> {
  const res = await apiClient.patch<Operation>(`/operations/${operationId}/status`, {
    status,
    remarks
  })
  return res.data
}

export async function getApprovalRequests(status?: string): Promise<OperationApprovalRequest[]> {
  const res = await apiClient.get<OperationApprovalRequest[]>("/operations/approval-requests", {
    params: status ? { status } : {}
  })
  return res.data
}

export async function approveApprovalRequest(id: string, remarks?: string): Promise<OperationApprovalRequest> {
  const res = await apiClient.post<OperationApprovalRequest>(`/operations/approval-requests/${id}/approve`, {
    remarks
  })
  return res.data
}

export async function rejectApprovalRequest(id: string, remarks?: string): Promise<OperationApprovalRequest> {
  const res = await apiClient.post<OperationApprovalRequest>(`/operations/approval-requests/${id}/reject`, {
    remarks
  })
  return res.data
}

export async function getOperationLocations(operationId: string): Promise<OperationLocation[]> {
  const res = await apiClient.get<OperationLocation[]>(`/operations/${operationId}/locations`)
  return res.data
}

export async function createOperationLocation(
  operationId: string,
  data: {
    name: string
    description?: string
    address?: string
    latitude: number
    longitude: number
    requiredManpower: number
    sequenceOrder: number
  }
): Promise<OperationLocation> {
  const res = await apiClient.post<OperationLocation>(`/operations/${operationId}/locations`, data)
  return res.data
}
