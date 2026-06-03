import { Composition } from 'remotion'
import type { ComponentType, FC } from 'react'
import { TimelineComposition } from './compositions/TimelineComposition'
import type { TimelineCompositionProps } from './types'

const defaultProps: TimelineCompositionProps = {
  title: '创作编排最小骨架',
  subtitle: '后续这里将接入后端 Composition Timeline JSON。',
  backgroundColor: '#020617',
  segments: [
    {
      id: 'seg-hook',
      label: 'Hook / 开场抓取',
      startFrame: 0,
      durationInFrames: 90,
      color: '#1D4ED8',
      description: '用于快速验证 Remotion 组合、字体、层叠与时间轴是否工作正常。',
    },
    {
      id: 'seg-body',
      label: 'Body / 内容展开',
      startFrame: 90,
      durationInFrames: 120,
      color: '#0F766E',
      description: '后续这里会映射素材适配后的片段、字幕和动态包装。',
    },
    {
      id: 'seg-outro',
      label: 'Outro / 收尾',
      startFrame: 210,
      durationInFrames: 90,
      color: '#9A3412',
      description: '预留给收尾卡、CTA、品牌露出等 Remotion 渲染元素。',
    },
  ],
}

export const RemotionRoot: FC = () => {
  return (
    <>
      <Composition
        id="TimelineComposition"
        component={TimelineComposition as unknown as ComponentType<Record<string, unknown>>}
        durationInFrames={300}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={defaultProps}
      />
    </>
  )
}
