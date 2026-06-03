/**
 * 预设自动注册 — 将所有预设组件注册到 PresetRegistry
 * 导出为显式函数，防止 Webpack Tree Shaking 剔除副作用模块
 */
import { registerPreset } from './PresetRegistry';
import { MeshGradientBackground } from './background/MeshGradientBackground';
import { TechGridBackground } from './background/TechGridBackground';
import { NoiseGrainBackground } from './background/NoiseGrainBackground';
import { VideoClip } from './media/VideoClip';
import { ImageLayer } from './media/ImageLayer';
import { AudioTrack } from './media/AudioTrack';
import { KenBurns } from './motion/KenBurns';
import { FadeTitle } from './text/FadeTitle';
import { KineticPopText } from './text/KineticPopText';
import { HeroBillboardText } from './text/HeroBillboardText';
import { TypewriterTitle } from './text/TypewriterTitle';
import { MaskRevealText } from './text/MaskRevealText';
import { WordHighlightText } from './text/WordHighlightText';
import { CounterNumber } from './text/CounterNumber';
import { LabelChip } from './text/LabelChip';
import { Subtitle } from './caption/Subtitle';
import { LightLeakWrapper } from './overlay/LightLeakWrapper';
import { FlashOverlay } from './overlay/FlashOverlay';
import { BadgePopOverlay } from './overlay/BadgePopOverlay';
import { GlowFrameOverlay } from './overlay/GlowFrameOverlay';
import { SolidPlate } from './backing/SolidPlate';
import { CapsuleBacking } from './backing/CapsuleBacking';
import { GlassPlate } from './backing/GlassPlate';

let _registered = false;

/** 注册所有预设组件。幂等调用，多次调用不会重复注册。 */
export function registerAllPresets(): void {
  if (_registered) return;
  _registered = true;

  // 媒体类
  registerPreset({ id: 'bg.mesh_gradient', component: MeshGradientBackground });
  registerPreset({ id: 'bg.tech_grid', component: TechGridBackground });
  registerPreset({ id: 'bg.noise_grain', component: NoiseGrainBackground });

  // 媒体类
  registerPreset({ id: 'media.video', component: VideoClip });
  registerPreset({ id: 'media.image', component: ImageLayer });
  registerPreset({ id: 'media.audio', component: AudioTrack });

  // 运镜类
  registerPreset({ id: 'motion.ken_burns', component: KenBurns });

  // 文字类
  registerPreset({ id: 'text.fade_title', component: FadeTitle });
  registerPreset({ id: 'text.kinetic_pop', component: KineticPopText });
  registerPreset({ id: 'text.hero_billboard', component: HeroBillboardText });
  registerPreset({ id: 'text.typewriter', component: TypewriterTitle });
  registerPreset({ id: 'text.mask_reveal', component: MaskRevealText });
  registerPreset({ id: 'text.word_highlight', component: WordHighlightText });
  registerPreset({ id: 'text.counter_number', component: CounterNumber });
  registerPreset({ id: 'text.label_chip', component: LabelChip });

  // 字幕类
  registerPreset({ id: 'caption.subtitle', component: Subtitle });

  // 叠加特效类
  registerPreset({ id: 'overlay.light_leak', component: LightLeakWrapper });
  registerPreset({ id: 'overlay.flash', component: FlashOverlay });
  registerPreset({ id: 'overlay.badge_pop', component: BadgePopOverlay });
  registerPreset({ id: 'overlay.glow_frame', component: GlowFrameOverlay });

  // 底板类
  registerPreset({ id: 'backing.solid_plate', component: SolidPlate });
  registerPreset({ id: 'backing.capsule', component: CapsuleBacking });
  registerPreset({ id: 'backing.glass_plate', component: GlassPlate });
}
