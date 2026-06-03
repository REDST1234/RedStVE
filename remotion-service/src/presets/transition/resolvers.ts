/**
 * 转场预设解析器 — 将编排 JSON 的转场配置映射到 Remotion 转场组件
 */
import { fade } from '@remotion/transitions/fade';
import { slide } from '@remotion/transitions/slide';
import { wipe } from '@remotion/transitions/wipe';
import { linearTiming, springTiming } from '@remotion/transitions';
import type { TransitionPresentation } from '@remotion/transitions';
import type { Transition } from '../../schemas/CompositionScript';

type SlideDirection = 'from-left' | 'from-right' | 'from-top' | 'from-bottom';

/** 解析转场展现方式 */
export function resolvePresentation(trans: Transition): TransitionPresentation<Record<string, unknown>> {
  switch (trans.preset) {
    case 'transition.slide':
      return slide({ direction: (trans.params.direction as SlideDirection) || 'from-left' });
    case 'transition.wipe':
      return wipe();
    case 'transition.fade':
    default:
      return fade();
  }
}

/** 解析转场时序 */
export function resolveTiming(trans: Transition) {
  const dur = trans.params.durationInFrames || 15;
  if (trans.params.timing === 'spring') {
    return springTiming({ config: { damping: 200 }, durationInFrames: dur });
  }
  return linearTiming({ durationInFrames: dur });
}
