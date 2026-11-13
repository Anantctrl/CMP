// Service to resolve GPS coordinates into human-readable tactical areas and localities

export interface DeviceCoordinates {
  latitude: number
  longitude: number
  accuracyMeters: number
  recordedAt: string
  source: "GPS" | "NETWORK"
}

const areaCache = new Map<string, string>()

// Pre-load from localStorage if available
try {
  const stored = localStorage.getItem("copmap_area_cache")
  if (stored) {
    const parsed = JSON.parse(stored)
    Object.entries(parsed).forEach(([k, v]) => areaCache.set(k, v as string))
  }
} catch {
  // Ignore localStorage read errors
}

function saveCache() {
  try {
    const obj = Object.fromEntries(areaCache.entries())
    localStorage.setItem("copmap_area_cache", JSON.stringify(obj))
  } catch {
    // Ignore cache persistence errors
  }
}

/**
 * Obtains the user's ACTUAL physical location.
 * Uses high-accuracy device GPS (navigator.geolocation).
 * If browser GPS is denied or unavailable, seamlessly falls back to IP network geolocation.
 */
export async function getCurrentDeviceLocation(): Promise<DeviceCoordinates> {
  // 1. Try Browser Hardware / Sensor GPS
  if (typeof window !== "undefined" && "geolocation" in navigator) {
    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 5000
        })
      })

      return {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMeters: Math.round(position.coords.accuracy || 8),
        recordedAt: new Date(position.timestamp || Date.now()).toISOString(),
        source: "GPS"
      }
    } catch (gpsError: unknown) {
      const err = gpsError as GeolocationPositionError
      console.warn("Browser GPS not available or denied, trying network IP fallback:", err.message)
    }
  }

  // 2. Fallback to real IP-based geolocation if browser GPS is blocked/unsupported
  try {
    const res = await fetch("https://ipapi.co/json/", { cache: "no-store" })
    if (res.ok) {
      const data = await res.json()
      if (data.latitude && data.longitude) {
        return {
          latitude: Number(data.latitude),
          longitude: Number(data.longitude),
          accuracyMeters: 500, // IP range estimation
          recordedAt: new Date().toISOString(),
          source: "NETWORK"
        }
      }
    }
  } catch (netErr) {
    console.warn("Network IP geolocation failed:", netErr)
  }

  throw new Error("Unable to obtain your actual location. Please allow browser location access.")
}

/**
 * Fast offline heuristic for common police test coordinates
 */
export function getFastHeuristicArea(lat: number, lon: number): string {
  // Pune Metropolitan Region
  if (lat >= 18.45 && lat <= 18.65 && lon >= 73.75 && lon <= 73.98) {
    if (Math.abs(lat - 18.5204) < 0.02 && Math.abs(lon - 73.8567) < 0.02) {
      return "Shivajinagar / Central Pune, Maharashtra"
    }
    if (lat > 18.55 && lon < 73.80) {
      return "Baner / Balewadi Sector, Pune"
    }
    if (lat < 18.51 && lon > 73.90) {
      return "Hadapsar / Magarpatta Zone, Pune"
    }
    if (lat < 18.51 && lon <= 73.86) {
      return "Swargate / Deccan Gymkhana, Pune"
    }
    if (lat >= 18.53 && lon >= 73.82) {
      return "Model Colony / University Circle, Pune"
    }
    return "Pune Metropolitan Police Sector, Maharashtra"
  }

  // Delhi NCR Region
  if (lat >= 28.40 && lat <= 28.80 && lon >= 77.00 && lon <= 77.40) {
    if (Math.abs(lat - 28.6139) < 0.02 && Math.abs(lon - 77.2090) < 0.02) {
      return "Central Vista / Janpath, New Delhi"
    }
    if (lat >= 28.625 && lon >= 77.21) {
      return "Connaught Place / Parliament St, New Delhi"
    }
    if (lat >= 28.65 && lon >= 77.22) {
      return "Chandni Chowk / Red Fort, Old Delhi"
    }
    return "Delhi Police Central Command Sector, New Delhi"
  }

  // Mumbai Metropolitan
  if (lat >= 18.85 && lat <= 19.30 && lon >= 72.75 && lon <= 73.05) {
    if (lat <= 18.94 && lon <= 72.84) {
      return "Colaba / Fort / Marine Drive, Mumbai"
    }
    if (lat >= 19.05 && lat <= 19.08 && lon >= 72.84 && lon <= 72.88) {
      return "Bandra Kurla Complex (BKC), Mumbai"
    }
    return "Greater Mumbai Police Zone, Maharashtra"
  }

  return `Sector (${lat.toFixed(3)}°N, ${lon.toFixed(3)}°E)`
}

/**
 * Reverse geocode latitude/longitude to a human-readable area name.
 * Uses cache first, queries OpenStreetMap Nominatim for exact real-world localities,
 * and falls back to heuristic.
 */
export async function getAreaName(lat: number, lon: number): Promise<string> {
  const cacheKey = `${lat.toFixed(3)},${lon.toFixed(3)}`

  // If already cached with a real human area name (not a generic sector string)
  if (areaCache.has(cacheKey)) {
    const cached = areaCache.get(cacheKey)!
    if (!cached.startsWith("Sector (")) {
      return cached
    }
  }

  // Query Nominatim to fetch fine-grained street, village, suburb, town & city
  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=16&addressdetails=1`
    const res = await fetch(url, {
      headers: {
        Accept: "application/json"
      }
    })

    if (res.ok) {
      const data = await res.json()
      const addr = data.address || {}

      const locality =
        addr.village ||
        addr.suburb ||
        addr.neighbourhood ||
        addr.residential ||
        addr.road ||
        addr.hamlet ||
        addr.quarter ||
        addr.commercial

      const city =
        addr.town ||
        addr.city ||
        addr.county ||
        addr.municipality ||
        addr.city_district

      const district = addr.state_district
      const state = addr.state

      const parts: string[] = []
      if (locality) parts.push(locality)
      if (city && city !== locality) parts.push(city)
      if (district && district !== city && district !== locality) parts.push(district)
      if (state && parts.length < 2) parts.push(state)

      let formattedArea = parts.slice(0, 3).join(", ")

      if (!formattedArea && data.display_name) {
        formattedArea = data.display_name.split(",").slice(0, 3).join(",").trim()
      }

      if (formattedArea) {
        areaCache.set(cacheKey, formattedArea)
        saveCache()
        return formattedArea
      }
    }
  } catch {
    // Keep fallback on network error or offline mode
  }

  const fallback = getFastHeuristicArea(lat, lon)
  areaCache.set(cacheKey, fallback)
  return fallback
}
