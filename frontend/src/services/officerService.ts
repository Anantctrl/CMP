import apiClient from "@/config/apiClient"

export interface OfficerDirectoryEntry {
  officerId: string
  mysqlUserId: string
  name: string
  policePost: string
  designation: string
  email: string
  role: string
}

export async function getAllOfficers(): Promise<OfficerDirectoryEntry[]> {
  const res = await apiClient.get<OfficerDirectoryEntry[]>("/officers")
  return res.data
}

export async function getAvailableOfficers(teamId?: string): Promise<OfficerDirectoryEntry[]> {
  const res = await apiClient.get<OfficerDirectoryEntry[]>("/officers/available", {
    params: teamId ? { teamId } : {}
  })
  return res.data
}

export async function refreshOfficerCache(): Promise<void> {
  await apiClient.post("/officers/cache/evict")
}
