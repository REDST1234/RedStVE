import {
  AbsoluteFill,
  Sequence,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion'
import type { FC } from 'react'
import type { TimelineCompositionProps } from '../types'

const fallbackColors = ['#0F172A', '#143868', '#1D4ED8', '#0F766E', '#9A3412']

const SegmentCard: FC<{
  label: string
  description?: string
  color: string
}> = ({ label, description, color }) => {
  const frame = useCurrentFrame()
  const { fps } = useVideoConfig()
  const entrance = spring({
    fps,
    frame,
    config: {
      damping: 16,
      stiffness: 120,
      mass: 0.8,
    },
  })
  const translateY = interpolate(entrance, [0, 1], [48, 0])
  const opacity = interpolate(entrance, [0, 1], [0, 1])

  return (
    <AbsoluteFill
      style={{
        justifyContent: 'center',
        alignItems: 'center',
        padding: 96,
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 900,
          borderRadius: 40,
          padding: '56px 52px',
          background: `linear-gradient(135deg, ${color}, rgba(255,255,255,0.08))`,
          color: 'white',
          boxShadow: '0 32px 80px rgba(15, 23, 42, 0.35)',
          transform: `translateY(${translateY}px)`,
          opacity,
          border: '1px solid rgba(255,255,255,0.12)',
          backdropFilter: 'blur(12px)',
        }}
      >
        <div
          style={{
            fontSize: 24,
            letterSpacing: 4,
            textTransform: 'uppercase',
            opacity: 0.72,
            marginBottom: 20,
          }}
        >
          Timeline Segment
        </div>
        <div
          style={{
            fontSize: 74,
            fontWeight: 800,
            lineHeight: 1.08,
            marginBottom: 20,
          }}
        >
          {label}
        </div>
        <div
          style={{
            fontSize: 30,
            lineHeight: 1.45,
            opacity: 0.92,
          }}
        >
          {description ?? '待接入后端编排时间线 JSON。当前为 Remotion 最小运行骨架。'}
        </div>
      </div>
    </AbsoluteFill>
  )
}

export const TimelineComposition: FC<TimelineCompositionProps> = ({
  title,
  subtitle,
  backgroundColor = '#020617',
  segments,
}) => {
  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(circle at top, rgba(37, 99, 235, 0.28), transparent 42%), ${backgroundColor}`,
        fontFamily:
          '"SF Pro Display", "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
      }}
    >
      <AbsoluteFill
        style={{
          padding: '72px 80px',
          color: 'white',
        }}
      >
        <div style={{ fontSize: 28, opacity: 0.7, marginBottom: 16 }}>bytedance-ai-video</div>
        <div style={{ fontSize: 72, fontWeight: 900, lineHeight: 1.05 }}>{title}</div>
        {subtitle ? (
          <div style={{ marginTop: 18, fontSize: 28, opacity: 0.8, maxWidth: 840 }}>
            {subtitle}
          </div>
        ) : null}
      </AbsoluteFill>

      {segments.map((segment, index) => (
        <Sequence
          key={segment.id}
          from={segment.startFrame}
          durationInFrames={segment.durationInFrames}
        >
          <SegmentCard
            label={segment.label}
            description={segment.description}
            color={segment.color ?? fallbackColors[index % fallbackColors.length]}
          />
        </Sequence>
      ))}
    </AbsoluteFill>
  )
}
