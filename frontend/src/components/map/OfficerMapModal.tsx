import React, { useEffect, useRef, useState } from "react"
import L from "leaflet"
import "leaflet/dist/leaflet.css"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { OfficerLiveLocation } from "@/services/trackingService"
import { getAreaName, getFastHeuristicArea } from "@/services/geoService"
import { MapPin, Navigation, ExternalLink, Copy, Check, Radio, Shield, LocateFixed } from "lucide-react"
import toast from "react-hot-toast"

interface OfficerMapModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  location: OfficerLiveLocation | null
  officerRole?: string
  onTransmitActualLocation?: () => Promise<void>
}

export const OfficerMapModal: React.FC<OfficerMapModalProps> = ({
  open,
  onOpenChange,
  location,
  officerRole,
  onTransmitActualLocation
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapInstanceRef = useRef<L.Map | null>(null)
  const markerRef = useRef<L.Marker | null>(null)
  const circleRef = useRef<L.Circle | null>(null)

  const [areaName, setAreaName] = useState<string>("")
  const [copied, setCopied] = useState<boolean>(false)
  const [updatingActual, setUpdatingActual] = useState<boolean>(false)

  // Resolve human-readable area name whenever location changes
  useEffect(() => {
    if (!location) {
      setAreaName("")
      return
    }

    // Instant heuristic display
    setAreaName(getFastHeuristicArea(location.latitude, location.longitude))

    // Asynchronously resolve fine-grained area via OpenStreetMap Nominatim
    let active = true
    getAreaName(location.latitude, location.longitude).then((resolved) => {
      if (active && resolved) {
        setAreaName(resolved)
      }
    })

    return () => {
      active = false
    }
  }, [location])

  // Mount/recenter Leaflet map inside dialog
  useEffect(() => {
    if (!open || !location) return

    // Slight timeout allows Dialog transition animation to complete so container dimensions are ready
    const timer = setTimeout(() => {
      if (!mapContainerRef.current) return

      if (!mapInstanceRef.current) {
        const map = L.map(mapContainerRef.current, {
          center: [location.latitude, location.longitude],
          zoom: 16,
          zoomControl: true,
          attributionControl: true
        })

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          maxZoom: 19,
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
        }).addTo(map)

        mapInstanceRef.current = map
      } else {
        mapInstanceRef.current.invalidateSize()
        mapInstanceRef.current.setView([location.latitude, location.longitude], 16, {
          animate: true
        })
      }

      const map = mapInstanceRef.current

      // Remove previous marker & circle
      if (markerRef.current) {
        map.removeLayer(markerRef.current)
        markerRef.current = null
      }
      if (circleRef.current) {
        map.removeLayer(circleRef.current)
        circleRef.current = null
      }

      // Create tactical pin
      const isFresh = location.freshnessStatus === "FRESH"
      const isDegraded = location.freshnessStatus === "DEGRADED"

      const borderColor = isFresh ? "border-emerald-500" : isDegraded ? "border-amber-500" : "border-rose-500"
      const bgColor = isFresh ? "bg-emerald-600" : isDegraded ? "bg-amber-600" : "bg-rose-600"
      const pulseRing = isFresh
        ? `<span class="absolute -inset-2.5 rounded-full bg-emerald-500 opacity-60 animate-ping"></span>`
        : ""

      const name = location.officerName || "Officer"
      const initials = name
        .split(" ")
        .map((n) => n[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()

      const iconHtml = `
        <div class="relative flex flex-col items-center cursor-pointer" style="transform: translate(-50%, -50%);">
          ${pulseRing}
          <div class="relative z-10 w-11 h-11 rounded-full ${bgColor} ${borderColor} border-2 text-white flex items-center justify-center font-bold text-sm shadow-2xl ring-4 ring-black/50">
            ${initials}
          </div>
          <div class="mt-1 px-2.5 py-0.5 rounded bg-zinc-950/95 text-white text-xs font-semibold whitespace-nowrap shadow-md border border-zinc-700">
            ${name}
          </div>
        </div>
      `

      const customIcon = L.divIcon({
        className: "officer-modal-pin",
        html: iconHtml,
        iconSize: [44, 44],
        iconAnchor: [22, 22]
      })

      const marker = L.marker([location.latitude, location.longitude], { icon: customIcon }).addTo(map)
      markerRef.current = marker

      const circle = L.circle([location.latitude, location.longitude], {
        radius: location.accuracyMeters || 12,
        color: isFresh ? "#10b981" : "#f59e0b",
        fillColor: isFresh ? "#10b981" : "#f59e0b",
        fillOpacity: 0.18,
        weight: 1.5
      }).addTo(map)
      circleRef.current = circle
    }, 150)

    return () => {
      clearTimeout(timer)
    }
  }, [open, location])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove()
        mapInstanceRef.current = null
      }
    }
  }, [])

  const handleCopyCoords = () => {
    if (!location) return
    const text = `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`
    navigator.clipboard.writeText(text)
    setCopied(true)
    toast.success("GPS coordinates copied to clipboard")
    setTimeout(() => setCopied(false), 2000)
  }

  const handleOpenGoogleMaps = () => {
    if (!location) return
    const url = `https://www.google.com/maps?q=${location.latitude},${location.longitude}`
    window.open(url, "_blank")
  }

  const getFreshnessBadge = (status: string) => {
    switch (status) {
      case "FRESH":
        return <Badge variant="success">🟢 Live (Fresh Signal)</Badge>
      case "DEGRADED":
        return <Badge variant="warning">🟡 Degraded Signal</Badge>
      default:
        return <Badge variant="destructive">🔴 Stale Signal</Badge>
    }
  }

  if (!location) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl p-0 overflow-hidden sm:rounded-2xl">
        <DialogHeader className="p-6 pb-4 border-b border-border bg-card/60">
          <div className="flex justify-between items-start gap-4">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary">
                <Shield className="w-6 h-6" />
              </div>
              <div>
                <DialogTitle className="text-xl font-bold flex items-center gap-2">
                  {location.officerName || `Officer ${location.officerId.substring(0, 8)}`}
                </DialogTitle>
                <DialogDescription className="text-sm font-medium text-muted-foreground flex items-center gap-2 mt-0.5">
                  <span>{location.designation}</span>
                  {officerRole && <span>&bull;</span>}
                  {officerRole && (
                    <Badge variant="outline" className="text-xs">
                      {officerRole.replace("_", " ")}
                    </Badge>
                  )}
                </DialogDescription>
              </div>
            </div>
            <div>{getFreshnessBadge(location.freshnessStatus)}</div>
          </div>

          {/* Area & GPS Detail Card */}
          <div className="mt-4 p-3.5 rounded-xl bg-muted/60 border border-border/80 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
            <div className="space-y-1">
              <div className="flex items-center gap-1.5 text-sm font-bold text-foreground">
                <MapPin className="w-4 h-4 text-rose-500 shrink-0" />
                <span>Detected Area:</span>
                <span className="text-primary font-semibold">{areaName || "Detecting area..."}</span>
              </div>
              <div className="flex items-center gap-3 text-xs text-muted-foreground font-mono">
                <span>{location.latitude.toFixed(5)}° N, {location.longitude.toFixed(5)}° E</span>
                <span>&bull;</span>
                <span>Accuracy &plusmn;{location.accuracyMeters || 5}m</span>
              </div>
            </div>

            <div className="text-right text-xs text-muted-foreground">
              <div className="flex items-center gap-1 sm:justify-end font-medium">
                <Radio className="w-3.5 h-3.5 text-emerald-500 animate-pulse" />
                <span>Last Telemetry</span>
              </div>
              <div>{new Date(location.recordedAt).toLocaleTimeString()}</div>
            </div>
          </div>
        </DialogHeader>

        {/* Tactical Interactive Leaflet Map */}
        <div className="relative w-full h-80 sm:h-96 bg-zinc-900">
          <div ref={mapContainerRef} className="w-full h-full" style={{ zIndex: 10 }} />
          <div className="absolute top-3 left-3 z-[1000] bg-background/90 backdrop-blur px-2.5 py-1 rounded-md text-xs font-semibold border border-border flex items-center gap-1.5 shadow">
            <Navigation className="w-3.5 h-3.5 text-primary animate-spin" />
            Live Tactical Telemetry Feed
          </div>
        </div>

        {/* Modal Footer Controls */}
        <DialogFooter className="p-4 bg-card/60 border-t border-border flex sm:justify-between items-center gap-2">
          <div className="text-xs text-muted-foreground font-medium hidden sm:block">
            Target UUID: {location.officerId.substring(0, 16)}...
          </div>
          <div className="flex items-center gap-2 w-full sm:w-auto justify-end flex-wrap">
            {onTransmitActualLocation && (
              <Button
                size="sm"
                onClick={async () => {
                  try {
                    setUpdatingActual(true)
                    await onTransmitActualLocation()
                  } finally {
                    setUpdatingActual(false)
                  }
                }}
                disabled={updatingActual}
                className="gap-1.5 text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <LocateFixed className={`w-3.5 h-3.5 ${updatingActual ? "animate-spin" : ""}`} />
                {updatingActual ? "Pinging..." : "Send My Actual GPS"}
              </Button>
            )}
            <Button size="sm" variant="outline" onClick={handleCopyCoords} className="gap-1.5 text-xs">
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? "Copied" : "Copy GPS"}
            </Button>
            <Button size="sm" variant="secondary" onClick={handleOpenGoogleMaps} className="gap-1.5 text-xs">
              <ExternalLink className="w-3.5 h-3.5" />
              Open in Maps
            </Button>
            <Button size="sm" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
