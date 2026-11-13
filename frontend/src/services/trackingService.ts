import apiClient from "@/config/apiClient"

export interface OfficerLiveLocation {
  officerId: string
  officerName?: string
  designation: string
  latitude: number
  longitude: number
  accuracyMeters?: number
  recordedAt: string
  receivedAt: string
  freshnessStatus: "FRESH" | "DEGRADED" | "STALE"
}

export async function sendLocationUpdate(data: {
  latitude: number
  longitude: number
  accuracyMeters?: number
  recordedAt?: string
}): Promise<void> {
  const payload = {
    ...data,
    recordedAt: data.recordedAt || new Date().toISOString()
  }
  await apiClient.post("/tracking/update", payload)
}

export async function getTeamLiveLocations(teamId: string): Promise<OfficerLiveLocation[]> {
  const res = await apiClient.get<OfficerLiveLocation[]>(`/tracking/teams/${teamId}/live`)
  return res.data
}
