import apiClient from "@/config/apiClient"

export interface Team {
  id: string
  name: string
  description?: string
  createdAt: string
  memberCount: number
  userRoleInTeam: string
  activeOperationsCount: number
}

export interface TeamMember {
  id: string
  teamId: string
  officerId: string
  officerName?: string
  designation: string
  teamRole: "SENIOR_PLANNER" | "TEAM_LEAD" | "CONTROL_OPERATOR" | "ADMINISTRATOR" | "FIELD_OPERATOR"
  joinedAt: string
}

export interface TeamAudit {
  id: string
  teamId: string
  officerId?: string
  changedById: string
  action: string
  oldValues?: Record<string, any>
  newValues?: Record<string, any>
  remarks?: string
  createdAt: string
}

export async function getAuthorizedTeams(): Promise<Team[]> {
  const res = await apiClient.get<Team[]>("/teams")
  return res.data
}

export async function getTeamDetail(teamId: string): Promise<Team> {
  const res = await apiClient.get<Team>(`/teams/${teamId}`)
  return res.data
}

export async function createTeam(data: { name: string; description?: string }): Promise<Team> {
  const res = await apiClient.post<Team>("/teams", data)
  return res.data
}

export async function getTeamMembers(teamId: string): Promise<TeamMember[]> {
  const res = await apiClient.get<TeamMember[]>(`/teams/${teamId}/members`)
  return res.data
}

export async function addTeamMember(teamId: string, data: { officerId: string; teamRole: string }): Promise<TeamMember> {
  const res = await apiClient.post<TeamMember>(`/teams/${teamId}/members`, data)
  return res.data
}

export async function removeTeamMember(teamId: string, officerId: string, remarks: string): Promise<void> {
  await apiClient.delete(`/teams/${teamId}/members/${officerId}`, {
    data: { remarks }
  })
}

export async function getTeamAudit(teamId: string): Promise<TeamAudit[]> {
  const res = await apiClient.get<TeamAudit[]>(`/teams/${teamId}/audit`)
  return res.data
}
