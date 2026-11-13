import React, { useEffect, useRef, useState } from "react"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import type { OfficerLiveLocation } from "@/services/trackingService"
import { getFastHeuristicArea, getAreaName } from "@/services/geoService"
import { Button } from "@/components/ui/button"
import { Crosshair, Layers } from "lucide-react"

interface TacticalMapViewProps {
  locations: OfficerLiveLocation[]
  selectedOfficerId?: string | null
  onSelectOfficer?: (loc: OfficerLiveLocation) => void
  height?: string
}

export const TacticalMapView: React.FC<TacticalMapViewProps> = ({
  locations,
  selectedOfficerId,
  onSelectOfficer,
  height = "460px"
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapInstanceRef = useRef<L.Map | null>(null)
  const markersRef = useRef<Map<string, L.Marker>>(new Map())
  const circlesRef = useRef<Map<string, L.Circle>>(new Map())
  const hasAutoCenteredRef = useRef(false)
  const [areaNames, setAreaNames] = useState<Record<string, string>>({})

  // Resolve human-readable areas for all locations
  useEffect(() => {
    locations.forEach(async (loc) => {
      const area = await getAreaName(loc.latitude, loc.longitude)
      setAreaNames((prev) => ({ ...prev, [loc.officerId]: area }))
    })
  }, [locations])

  // Initialize Leaflet map
  useEffect(() => {
    if (!mapContainerRef.current) return

    // Prevent duplicate map initialization
    if (!mapInstanceRef.current) {
      const initialLat = locations[0]?.latitude || 18.6319
      const initialLon = locations[0]?.longitude || 73.7986

      const map = L.map(mapContainerRef.current, {
        center: [initialLat, initialLon],
        zoom: 15,
        zoomControl: true,
        attributionControl: true
      })

      // Modern OpenStreetMap Carto style tiles
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(map)

      mapInstanceRef.current = map

      setTimeout(() => {
        map.invalidateSize()
      }, 100)
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove()
        mapInstanceRef.current = null
      }
    }
  }, [])

  // Invalidate size on locations update or tab change
  useEffect(() => {
    const timer = setTimeout(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize()
      }
    }, 200)
    return () => clearTimeout(timer)
  }, [locations])

  // Create custom police tactical HTML marker
  const createOfficerMarker = (loc: OfficerLiveLocation) => {
    const isFresh = loc.freshnessStatus === "FRESH"
    const isDegraded = loc.freshnessStatus === "DEGRADED"

    const borderColor = isFresh ? "border-emerald-500" : isDegraded ? "border-amber-500" : "border-rose-500"
    const bgColor = isFresh ? "bg-emerald-600" : isDegraded ? "bg-amber-600" : "bg-rose-600"
    const pulseRing = isFresh
      ? `<span class="absolute -inset-2 rounded-full bg-emerald-500 opacity-60 animate-ping"></span>`
      : ""

    const name = loc.officerName || "Officer"
    const initials = name
      .split(" ")
      .map((n) => n[0])
      .slice(0, 2)
      .join("")
      .toUpperCase()

    const iconHtml = `
      <div class="relative flex flex-col items-center group cursor-pointer" style="transform: translate(-50%, -50%);">
        ${pulseRing}
        <div class="relative z-10 w-9 h-9 rounded-full ${bgColor} ${borderColor} border-2 text-white flex items-center justify-center font-bold text-xs shadow-xl ring-2 ring-black/40">
          ${initials}
        </div>
        <div class="mt-1 px-2 py-0.5 rounded bg-zinc-950/90 text-white text-[11px] font-medium whitespace-nowrap shadow border border-zinc-700 pointer-events-none">
          ${name}
        </div>
      </div>
    `

    return L.divIcon({
      className: "custom-officer-div-icon",
      html: iconHtml,
      iconSize: [36, 36],
      iconAnchor: [18, 18]
    })
  }

  // Update markers when locations or area names change
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map) return

    const currentMarkerMap = markersRef.current
    const currentCircleMap = circlesRef.current
    const activeIds = new Set<string>()

    locations.forEach((loc) => {
      activeIds.add(loc.officerId)
      const area = areaNames[loc.officerId] || getFastHeuristicArea(loc.latitude, loc.longitude)
      const customIcon = createOfficerMarker(loc)

      const popupContent = `
        <div style="min-width: 220px; font-family: inherit; padding: 4px;">
          <div style="font-weight: 700; font-size: 14px; color: #111;">
            ${loc.officerName || "Officer"}
          </div>
          <div style="font-size: 12px; color: #555; margin-bottom: 6px;">
            ${loc.designation} &bull; <span style="font-weight: 600; color: ${loc.freshnessStatus === "FRESH" ? "#059669" : "#d97706"};">${loc.freshnessStatus}</span>
          </div>
          <div style="background: #f4f4f5; padding: 6px 8px; border-radius: 6px; font-size: 12px; margin-bottom: 6px; border: 1px solid #e4e4e7;">
            <div style="font-weight: 600; color: #27272a;">📍 ${area}</div>
            <div style="font-family: monospace; font-size: 11px; color: #71717a; margin-top: 2px;">
              ${loc.latitude.toFixed(5)}, ${loc.longitude.toFixed(5)} (&plusmn;${loc.accuracyMeters || 5}m)
            </div>
          </div>
          <div style="font-size: 11px; color: #71717a;">
            Updated: ${new Date(loc.recordedAt).toLocaleTimeString()}
          </div>
        </div>
      `

      if (currentMarkerMap.has(loc.officerId)) {
        const marker = currentMarkerMap.get(loc.officerId)!
        marker.setLatLng([loc.latitude, loc.longitude])
        marker.setIcon(customIcon)
        marker.setPopupContent(popupContent)

        // Update accuracy circle
        if (currentCircleMap.has(loc.officerId)) {
          const circle = currentCircleMap.get(loc.officerId)!
          circle.setLatLng([loc.latitude, loc.longitude])
          circle.setRadius(loc.accuracyMeters || 15)
        }
      } else {
        // Create new marker
        const marker = L.marker([loc.latitude, loc.longitude], { icon: customIcon })
        marker.bindPopup(popupContent)
        marker.on("click", () => {
          onSelectOfficer?.(loc)
        })
        marker.addTo(map)
        currentMarkerMap.set(loc.officerId, marker)

        // Create accuracy circle
        const circle = L.circle([loc.latitude, loc.longitude], {
          radius: loc.accuracyMeters || 15,
          color: loc.freshnessStatus === "FRESH" ? "#10b981" : "#f59e0b",
          fillColor: loc.freshnessStatus === "FRESH" ? "#10b981" : "#f59e0b",
          fillOpacity: 0.15,
          weight: 1
        }).addTo(map)
        currentCircleMap.set(loc.officerId, circle)
      }
    })

    // Remove markers that are no longer in locations list
    currentMarkerMap.forEach((marker, id) => {
      if (!activeIds.has(id)) {
        map.removeLayer(marker)
        currentMarkerMap.delete(id)
        if (currentCircleMap.has(id)) {
          map.removeLayer(currentCircleMap.get(id)!)
          currentCircleMap.delete(id)
        }
      }
    })

    // Auto-fit / center on locations on initial load or when locations become available
    if (!hasAutoCenteredRef.current && locations.length > 0) {
      if (locations.length === 1) {
        map.setView([locations[0].latitude, locations[0].longitude], 15, { animate: true })
      } else {
        const bounds = L.latLngBounds(locations.map((l) => [l.latitude, l.longitude]))
        map.fitBounds(bounds, { padding: [50, 50], animate: true })
      }
      hasAutoCenteredRef.current = true
    }

    // If an officer was selected, center on them
    if (selectedOfficerId && currentMarkerMap.has(selectedOfficerId)) {
      const targetLoc = locations.find((l) => l.officerId === selectedOfficerId)
      if (targetLoc) {
        map.setView([targetLoc.latitude, targetLoc.longitude], 16, { animate: true })
        currentMarkerMap.get(selectedOfficerId)?.openPopup()
      }
    }
  }, [locations, areaNames, selectedOfficerId, onSelectOfficer])

  const handleCenterAll = () => {
    const map = mapInstanceRef.current
    if (!map || locations.length === 0) return

    if (locations.length === 1) {
      map.setView([locations[0].latitude, locations[0].longitude], 15, { animate: true })
    } else {
      const bounds = L.latLngBounds(locations.map((l) => [l.latitude, l.longitude]))
      map.fitBounds(bounds, { padding: [50, 50], animate: true })
    }
  }

  return (
    <div className="relative rounded-xl overflow-hidden border border-border shadow-md bg-card">
      {/* Top Map Tactical Bar */}
      <div className="absolute top-3 right-3 z-[1000] flex gap-2">
        <Button
          size="sm"
          variant="secondary"
          onClick={handleCenterAll}
          className="gap-1.5 bg-background/90 backdrop-blur shadow-md hover:bg-background text-xs font-semibold"
        >
          <Crosshair className="w-3.5 h-3.5 text-primary" />
          Center Team ({locations.length})
        </Button>
      </div>

      {/* Map Legend */}
      <div className="absolute bottom-3 left-3 z-[1000] bg-background/90 backdrop-blur border border-border/80 px-3 py-1.5 rounded-lg shadow-md flex items-center gap-3 text-xs">
        <span className="font-semibold text-muted-foreground flex items-center gap-1">
          <Layers className="w-3 h-3" /> Status:
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse inline-block" />
          <span className="text-emerald-500 font-medium">Live</span>
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 rounded-full bg-amber-500 inline-block" />
          <span className="text-amber-500 font-medium">Degraded</span>
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 rounded-full bg-rose-500 inline-block" />
          <span className="text-rose-500 font-medium">Stale</span>
        </span>
      </div>

      {/* Leaflet Container */}
      <div ref={mapContainerRef} style={{ height, width: "100%", zIndex: 1 }} />
    </div>
  )
}
