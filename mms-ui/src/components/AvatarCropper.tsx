import { useCallback, useState } from 'react'
import Cropper from 'react-easy-crop'
import type { Area } from 'react-easy-crop'
import { X, ZoomIn, ZoomOut, RotateCw, Check, Loader2 } from 'lucide-react'

interface Props {
  imageSrc: string
  onCancel: () => void
  onSave: (blob: Blob) => Promise<void>
}

/**
 * Full-screen avatar crop/reposition modal. User can drag to reposition,
 * pinch/scroll to zoom, and rotate. Outputs a square cropped Blob.
 */
export default function AvatarCropper({ imageSrc, onCancel, onSave }: Props) {
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [croppedArea, setCroppedArea] = useState<Area | null>(null)
  const [saving, setSaving] = useState(false)

  const onCropComplete = useCallback((_: Area, croppedPixels: Area) => {
    setCroppedArea(croppedPixels)
  }, [])

  const handleSave = async () => {
    if (!croppedArea) return
    setSaving(true)
    try {
      const blob = await getCroppedImg(imageSrc, croppedArea, rotation)
      await onSave(blob)
    } catch (_) {
      // parent handles error
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-black/60" onClick={() => !saving && onCancel()} />
      <div className="relative w-full max-w-sm bg-gray-900 rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800">
          <button type="button" onClick={onCancel} disabled={saving}
            className="text-sm text-gray-400 hover:text-white transition-colors flex items-center gap-1.5">
            <X size={15} /> Cancel
          </button>
          <h3 className="text-sm font-semibold text-white">Adjust Photo</h3>
          <button type="button" onClick={handleSave} disabled={saving}
            className="btn-primary text-xs px-3 py-1.5 disabled:opacity-50">
            {saving ? <Loader2 size={13} className="animate-spin" /> : <Check size={13} />}
            {saving ? 'Saving…' : 'Apply'}
          </button>
        </div>

        {/* Crop area */}
        <div className="relative h-64 bg-gray-950">
          <Cropper
            image={imageSrc}
            crop={crop}
            zoom={zoom}
            rotation={rotation}
            aspect={1}
            cropShape="round"
            cropSize={{ width: 180, height: 180 }}
            showGrid={false}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onRotationChange={setRotation}
            onCropComplete={onCropComplete}
          />
        </div>

        {/* Controls */}
        <div className="px-4 py-3 border-t border-gray-800">
          <div className="flex items-center gap-3">
            <button type="button" onClick={() => setZoom((z) => Math.max(1, z - 0.2))}
              className="p-1.5 rounded text-gray-400 hover:text-white transition-colors" title="Zoom out">
              <ZoomOut size={15} />
            </button>
            <input type="range" min={1} max={3} step={0.05} value={zoom}
              onChange={(e) => setZoom(Number(e.target.value))}
              className="flex-1 h-1 bg-gray-700 rounded-full appearance-none cursor-pointer accent-brand-500"
              aria-label="Zoom" />
            <button type="button" onClick={() => setZoom((z) => Math.min(3, z + 0.2))}
              className="p-1.5 rounded text-gray-400 hover:text-white transition-colors" title="Zoom in">
              <ZoomIn size={15} />
            </button>
            <div className="w-px h-4 bg-gray-700" />
            <button type="button" onClick={() => setRotation((r) => (r + 90) % 360)}
              className="p-1.5 rounded text-gray-400 hover:text-white transition-colors" title="Rotate">
              <RotateCw size={15} />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Canvas-based crop helper ────────────────────────────────────────

async function getCroppedImg(src: string, crop: Area, rotation: number): Promise<Blob> {
  const image = await loadImage(src)
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!

  const maxSize = Math.max(image.width, image.height)
  const safeArea = 2 * ((maxSize / 2) * Math.sqrt(2))

  canvas.width = safeArea
  canvas.height = safeArea

  ctx.translate(safeArea / 2, safeArea / 2)
  ctx.rotate((rotation * Math.PI) / 180)
  ctx.translate(-safeArea / 2, -safeArea / 2)
  ctx.drawImage(image, safeArea / 2 - image.width / 2, safeArea / 2 - image.height / 2)

  const data = ctx.getImageData(0, 0, safeArea, safeArea)

  canvas.width = crop.width
  canvas.height = crop.height
  ctx.putImageData(data, -safeArea / 2 + image.width / 2 - crop.x, -safeArea / 2 + image.height / 2 - crop.y)

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('Canvas crop failed'))
    }, 'image/jpeg', 0.92)
  })
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}
