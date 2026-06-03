export interface TimelineSegment {
  id: string
  label: string
  startFrame: number
  durationInFrames: number
  color?: string
  description?: string
}

export interface TimelineCompositionProps {
  title: string
  subtitle?: string
  backgroundColor?: string
  segments: TimelineSegment[]
}
