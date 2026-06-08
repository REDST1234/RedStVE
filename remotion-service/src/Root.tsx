/**
 * Root.tsx — Remotion Composition 注册入口
 * 注册 DynamicVideo 组件，支持通过 inputProps 传入 Composition Script JSON
 */
import { Composition, CalculateMetadataFunction } from 'remotion';
import { DynamicVideoRenderer } from './DynamicVideoRenderer';
import { CompositionScriptSchema, type CompositionScript } from './schemas/CompositionScript';
import { registerAllPresets } from './presets/registerAll';

// 确保在任何组件挂载前注册预设
registerAllPresets();

/** 尾音效最多延长的帧数（3秒@30fps），与 DynamicVideoRenderer 保持一致 */
const MAX_TRAILING_FRAMES = 90;

/** 根据编排 JSON 动态计算视频元数据 */
const calculateMetadata: CalculateMetadataFunction<CompositionScript> = async ({ props }) => {
  const totalSceneFrames = props.scenes.reduce((sum, s) => sum + s.durationInFrames, 0);
  const transitionOverlap = props.transitions.reduce(
    (sum, t) => sum + (t.params.durationInFrames || 0),
    0,
  );
  const baseDuration = Math.max(1, totalSceneFrames - transitionOverlap);

  // 计算最后一场景尾音效溢出量，延长视频以播完音效
  let audioOverflow = 0;
  const lastScene = props.scenes[props.scenes.length - 1];
  if (lastScene) {
    let maxEnd = lastScene.durationInFrames;
    for (const layer of lastScene.layers) {
      if (layer.preset !== 'media.audio') continue;
      const params = layer.params as Record<string, unknown> | undefined;
      const ownDur = layer.durationInFrames
        ?? (typeof params?.totalDurationFrames === 'number' ? Math.round(params.totalDurationFrames as number) : 0)
        ?? lastScene.durationInFrames;
      let effectiveFrom = layer.enterAtFrame ?? 0;
      if (params?.syncWithLayerId) {
        const syncMode = (params.syncMode as string) ?? 'match_layer';
        const targetLayer = lastScene.layers.find((c) => c.layerId === params.syncWithLayerId);
        if (targetLayer) {
          const targetDur = targetLayer.durationInFrames ?? lastScene.durationInFrames;
          const targetFrom = targetLayer.enterAtFrame ?? 0;
          if (syncMode === 'trigger_on_end' || syncMode === 'trigger_on_typing_end') {
            effectiveFrom = targetFrom + targetDur;
          } else {
            effectiveFrom = targetFrom;
          }
        }
      }
      const end = effectiveFrom + ownDur;
      if (end > maxEnd) maxEnd = end;
    }
    audioOverflow = Math.max(0, Math.min(MAX_TRAILING_FRAMES, maxEnd - lastScene.durationInFrames));
  }

  return {
    durationInFrames: baseDuration + audioOverflow,
    width: props.canvas.width,
    height: props.canvas.height,
    fps: props.canvas.fps,
  };
};

/** 全面展示预设库能力的测试脚本 */
const showcaseScript: CompositionScript = {
  canvas: { width: 1080, height: 1920, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', fontTier: 'subtitle' as const, backgroundColor: '#111111' },
  scenes: [
    {
      sceneId: "scene_1_mg_hook",
      sceneIndex: 0,
      role: "hook",
      durationInFrames: 90,
      layers: [
        {
          layerId: "bg_mesh",
          preset: "bg.mesh_gradient",
          params: {
            colors: ["#0F2027", "#203A43", "#2C5364", "#7C3AED"],
            intensity: 0.95
          }
        },
        {
          layerId: "bg_noise",
          preset: "bg.noise_grain",
          params: {
            backgroundColor: "transparent",
            grainOpacity: 0.12,
            scale: 1
          }
        },
        {
          layerId: "title_0_pop",
          preset: "text.kinetic_pop",
          enterAtFrame: 6,
          durationInFrames: 30,
          params: {
            text: "极简积分",
            fontSize: 122,
            color: "#FFFFFF",
            fontWeight: 900,
            position: { x: "center", y: "30%" },
            scaleFrom: 1.95,
            rotationFrom: -8,
            enterFrames: 14,
            settleFrames: 24,
            textShadow: "0 10px 34px rgba(0,0,0,0.35)"
          }
        },
        {
          layerId: "title_1",
          preset: "text.typewriter",
          enterAtFrame: 34,
          durationInFrames: 28,
          params: {
            text: "扫码一键核销",
            fontSize: 64,
            color: "#7DD3FC",
            fontWeight: 800,
            position: { x: "center", y: "48%" },
            charIntervalFrames: 2,
            cursor: "|",
            cursorColor: "#7DD3FC",
            cursorScale: 0.65
          }
        },
        {
          layerId: "title_2_reveal",
          preset: "text.mask_reveal",
          enterAtFrame: 62,
          durationInFrames: 22,
          params: {
            text: "安全防刷",
            fontSize: 78,
            color: "#F8FAFC",
            position: { x: "center", y: "66%" },
            revealDirection: "bottom_to_top",
            revealFrames: 18,
            textShadow: "0 4px 20px rgba(0,0,0,0.32)"
          }
        }
      ]
    },
    {
      sceneId: "scene_2_billboard_layouts",
      sceneIndex: 1,
      role: "body",
      durationInFrames: 120,
      layers: [
        {
          layerId: "bg_grid",
          preset: "bg.tech_grid",
          params: {
            backgroundColor: "#06111F",
            lineColor: "#67E8F9",
            accentColor: "#22D3EE",
            gridSize: 76,
            lineOpacity: 0.22,
            driftSpeed: 18
          }
        },
        {
          layerId: "bg_noise",
          preset: "bg.noise_grain",
          params: {
            backgroundColor: "transparent",
            grainOpacity: 0.1,
            scale: 0.9
          }
        },
        {
          layerId: "title_2_billboard",
          preset: "text.hero_billboard",
          enterAtFrame: 20,
          durationInFrames: 100,
          params: {
            texts: ["拉新促活更直接", "转化复购更自动"],
            layoutPattern: "top_bottom_split",
            animationMode: "whole_pop",
            easingPreset: "expo_out",
            fontSize: 66,
            color: "#F8FAFC",
            accentColor: "#67E8F9",
            layoutMode: "portrait",
            letterSpacing: "0.1em",
            strokeEnabled: true,
            strokeColor: "#06111F",
            strokeWidth: 2.4,
            glowColor: "#22D3EE",
            glowBlur: 28,
            glowOpacity: 0.32
          }
        }
      ]
    },
    {
      sceneId: "scene_3_outro",
      sceneIndex: 2,
      role: "outro",
      durationInFrames: 60,
      layers: [
        {
          layerId: "bg_mesh_outro",
          preset: "bg.mesh_gradient",
          params: {
            colors: ["#1F2937", "#1D4ED8", "#7C3AED", "#22D3EE"],
            intensity: 0.88
          }
        },
        {
          layerId: "bg_noise_outro",
          preset: "bg.noise_grain",
          params: {
            backgroundColor: "transparent",
            grainOpacity: 0.08,
            scale: 1.1
          }
        },
        {
          layerId: "title_3",
          preset: "overlay.badge_pop",
          enterAtFrame: 10,
          durationInFrames: 44,
          params: {
            text: "NEW LOOK",
            bgColor: "#FF6B35",
            color: "#FFFFFF",
            position: { x: "8%", y: "14%" }
          }
        },
        {
          layerId: "title_3_text",
          preset: "text.fade_title",
          enterAtFrame: 18,
          params: {
            text: "AI 视频，一触即达",
            fontSize: 80,
            color: "#FFFFFF",
            textShadow: "0 4px 20px rgba(0,0,0,0.8)"
          }
        },
        {
          layerId: "glow_frame",
          preset: "overlay.glow_frame",
          enterAtFrame: 8,
          durationInFrames: 40,
          params: {
            color: "#55E6C1",
            thickness: 10,
            glowBlur: 24,
            opacity: 0.72,
            borderRadius: 30,
            inset: 28
          }
        }
      ]
    }
  ],
  transitions: [
    {
      fromSceneIndex: 0,
      toSceneIndex: 1,
      preset: "transition.wipe",
      params: {
        durationInFrames: 20,
        timing: "linear"
      },
      overlay: {
        preset: "overlay.flash",
        params: { color: "#FFFFFF", maxOpacity: 0.8, enterFrames: 2, holdFrames: 1, exitFrames: 10 }
      }
    },
    {
      fromSceneIndex: 1,
      toSceneIndex: 2,
      preset: "transition.slide",
      params: {
        direction: "from-bottom",
        durationInFrames: 15,
        timing: "spring"
      }
    }
  ]
};

/** 新增组件演示脚本 — text.counter_number / text.label_chip / backing.* */
const newComponentsShowcaseScript: CompositionScript = {
  canvas: { width: 1080, height: 1920, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', fontTier: 'subtitle' as const, backgroundColor: '#0B1120' },
  scenes: [
    // ═══ Scene 1: 数据看板 — glass_plate + counter_number + label_chip ═══
    {
      sceneId: 'scene_1_data_dashboard',
      sceneIndex: 0,
      role: 'hook',
      durationInFrames: 96,
      layers: [
        // 背景
        {
          layerId: 'bg_mesh',
          preset: 'bg.mesh_gradient',
          params: {
            colors: ['#0A1628', '#0F2B4C', '#112240', '#3B82F6'],
            intensity: 0.9,
          },
        },
        {
          layerId: 'bg_noise',
          preset: 'bg.noise_grain',
          params: {
            backgroundColor: 'transparent',
            grainOpacity: 0.09,
            scale: 1,
          },
        },
        // 玻璃底板
        {
          layerId: 'dashboard_glass',
          preset: 'backing.glass_plate',
          enterAtFrame: 8,
          durationInFrames: 84,
          params: {
            width: '84%',
            height: '58%',
            blurAmount: 22,
            tintColor: 'rgba(255,255,255,0.10)',
            borderRadius: 32,
            borderColor: 'rgba(255,255,255,0.15)',
            borderWidth: 1,
            position: { x: 'center', y: 'center' },
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.25)',
          },
        },
        // 标题 chip
        {
          layerId: 'label_dashboard_title',
          preset: 'text.label_chip',
          enterAtFrame: 14,
          durationInFrames: 74,
          params: {
            text: '📊 实时数据',
            variant: 'filled',
            bgColor: '#3B82F6',
            color: '#FFFFFF',
            fontSize: 26,
            fontWeight: 700,
            position: { x: 'center', y: '24%' },
            icon: '📊',
          },
        },
        // 数字计数器 - GMV
        {
          layerId: 'counter_gmv',
          preset: 'text.counter_number',
          enterAtFrame: 22,
          durationInFrames: 66,
          params: {
            value: 1280,
            prefix: '¥',
            suffix: '万',
            decimals: 0,
            fontSize: 84,
            color: '#F8FAFC',
            fontWeight: 900,
            scrollFrames: 34,
            digitGap: 4,
            position: { x: 'center', y: '38%' },
          },
        },
        // 数字计数器 - 增长
        {
          layerId: 'counter_growth',
          preset: 'text.counter_number',
          enterAtFrame: 28,
          durationInFrames: 60,
          params: {
            value: 34.8,
            suffix: '%',
            decimals: 1,
            fontSize: 64,
            color: '#67E8F9',
            fontWeight: 800,
            scrollFrames: 28,
            digitGap: 2,
            position: { x: 'center', y: '56%' },
          },
        },
        // 底部标签
        {
          layerId: 'label_outlined',
          preset: 'text.label_chip',
          enterAtFrame: 38,
          durationInFrames: 50,
          params: {
            text: 'GMV 环比增长',
            variant: 'outlined',
            color: '#94A3B8',
            borderColor: 'rgba(148,163,184,0.5)',
            fontSize: 22,
            position: { x: 'center', y: '70%' },
          },
        },
      ],
    },
    // ═══ Scene 2: 信息卡片 — solid_plate + capsule + text ═══
    {
      sceneId: 'scene_2_info_cards',
      sceneIndex: 1,
      role: 'body',
      durationInFrames: 90,
      layers: [
        {
          layerId: 'bg_tech',
          preset: 'bg.tech_grid',
          params: {
            backgroundColor: '#06111F',
            lineColor: '#475569',
            accentColor: '#F8C630',
            gridSize: 68,
            lineOpacity: 0.18,
            driftSpeed: 14,
          },
        },
        {
          layerId: 'bg_noise',
          preset: 'bg.noise_grain',
          params: {
            backgroundColor: 'transparent',
            grainOpacity: 0.07,
            scale: 0.9,
          },
        },
        // 实色底板 - 上方卡片
        {
          layerId: 'solid_plate_top',
          preset: 'backing.solid_plate',
          enterAtFrame: 6,
          durationInFrames: 78,
          params: {
            width: '78%',
            height: 'auto',
            color: '#1E293B',
            opacity: 0.8,
            borderRadius: 24,
            padding: 36,
            position: { x: 'center', y: '32%' },
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.35)',
          },
        },
        // 胶囊底板 + 文字 — 衬在关键词后面
        {
          layerId: 'capsule_keyword',
          preset: 'backing.capsule',
          enterAtFrame: 10,
          durationInFrames: 72,
          params: {
            color: '#F8C630',
            opacity: 0.78,
            borderRadius: 999,
            paddingX: 32,
            paddingY: 16,
            position: { x: 'center', y: '28%' },
            shadowEnabled: true,
            shadowColor: 'rgba(248,198,48,0.28)',
          },
        },
        {
          layerId: 'text_on_capsule',
          preset: 'text.kinetic_pop',
          enterAtFrame: 12,
          durationInFrames: 72,
          params: {
            text: '高转化',
            fontSize: 56,
            color: '#0F172A',
            fontWeight: 900,
            position: { x: 'center', y: '28%' },
            scaleFrom: 1.2,
            rotationFrom: 0,
            enterFrames: 12,
            settleFrames: 14,
          },
        },
        // 实色底板 - 下方卡片
        {
          layerId: 'solid_plate_bottom',
          preset: 'backing.solid_plate',
          enterAtFrame: 22,
          durationInFrames: 62,
          params: {
            width: '72%',
            height: 'auto',
            color: '#312E81',
            opacity: 0.7,
            borderRadius: 20,
            padding: 30,
            position: { x: 'center', y: '58%' },
            borderColor: 'rgba(99,102,241,0.35)',
            borderWidth: 1.5,
            shadowEnabled: true,
            shadowColor: 'rgba(49,46,129,0.25)',
          },
        },
        {
          layerId: 'text_reveal_on_plate',
          preset: 'text.mask_reveal',
          enterAtFrame: 26,
          durationInFrames: 56,
          params: {
            text: 'AI 编排·自动成片',
            fontSize: 52,
            color: '#E0E7FF',
            fontWeight: 800,
            position: { x: 'center', y: '58%' },
            revealDirection: 'left_to_right',
            revealFrames: 22,
            textShadow: '0 2px 12px rgba(0,0,0,0.4)',
          },
        },
        // soft 变体 chip
        {
          layerId: 'label_soft',
          preset: 'text.label_chip',
          enterAtFrame: 40,
          durationInFrames: 44,
          params: {
            text: '✨ Beta',
            variant: 'soft',
            color: '#C7D2FE',
            bgColor: '#6366F1',
            fontSize: 22,
            position: { x: 'center', y: '68%' },
          },
        },
      ],
    },
    // ═══ Scene 3: Outro — glass_plate + counter + chips 收尾 ═══
    {
      sceneId: 'scene_3_outro_new',
      sceneIndex: 2,
      role: 'outro',
      durationInFrames: 72,
      layers: [
        {
          layerId: 'bg_gradient_outro',
          preset: 'bg.mesh_gradient',
          params: {
            colors: ['#171347', '#312E81', '#4F46E5', '#22D3EE'],
            intensity: 0.92,
          },
        },
        {
          layerId: 'bg_noise_outro',
          preset: 'bg.noise_grain',
          params: {
            backgroundColor: 'transparent',
            grainOpacity: 0.08,
            scale: 1.1,
          },
        },
        // 玻璃底板
        {
          layerId: 'outro_glass',
          preset: 'backing.glass_plate',
          enterAtFrame: 6,
          durationInFrames: 62,
          params: {
            width: '82%',
            height: '48%',
            blurAmount: 18,
            tintColor: 'rgba(255,255,255,0.08)',
            borderRadius: 30,
            borderColor: 'rgba(255,255,255,0.14)',
            borderWidth: 1,
            position: { x: 'center', y: 'center' },
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.22)',
          },
        },
        // 计数器 — 处理量
        {
          layerId: 'counter_total',
          preset: 'text.counter_number',
          enterAtFrame: 12,
          durationInFrames: 52,
          params: {
            value: 9999,
            prefix: '#',
            decimals: 0,
            fontSize: 78,
            color: '#FFFFFF',
            fontWeight: 900,
            scrollFrames: 38,
            position: { x: 'center', y: '36%' },
          },
        },
        // filled chip
        {
          layerId: 'label_total',
          preset: 'text.label_chip',
          enterAtFrame: 18,
          durationInFrames: 46,
          params: {
            text: '🏆 累计服务企业',
            variant: 'filled',
            bgColor: '#0284C7',
            color: '#FFFFFF',
            fontSize: 22,
            position: { x: 'center', y: '54%' },
          },
        },
        // outlined chip
        {
          layerId: 'label_sub',
          preset: 'text.label_chip',
          enterAtFrame: 24,
          durationInFrames: 40,
          params: {
            text: 'Powered by AI Video',
            variant: 'outlined',
            color: '#94A3B8',
            borderColor: 'rgba(148,163,184,0.45)',
            fontSize: 18,
            position: { x: 'center', y: '64%' },
          },
        },
        // glow frame 收尾
        {
          layerId: 'outro_glow',
          preset: 'overlay.glow_frame',
          enterAtFrame: 8,
          durationInFrames: 56,
          params: {
            color: '#22D3EE',
            thickness: 8,
            glowBlur: 20,
            opacity: 0.55,
            borderRadius: 30,
            inset: 22,
          },
        },
      ],
    },
  ],
  transitions: [
    {
      fromSceneIndex: 0,
      toSceneIndex: 1,
      preset: 'transition.fade',
      params: {
        durationInFrames: 16,
        timing: 'linear',
      },
      overlay: {
        preset: 'overlay.flash',
        params: { color: '#FFFFFF', maxOpacity: 0.5, enterFrames: 2, holdFrames: 1, exitFrames: 8 },
      },
    },
    {
      fromSceneIndex: 1,
      toSceneIndex: 2,
      preset: 'transition.slide',
      params: {
        direction: 'from-bottom',
        durationInFrames: 14,
        timing: 'spring',
      },
    },
  ],
};
/** 2.5D 运镜动效演示 — motion.float_2d5 / motion.parallax_drift / motion.perspective_tilt */
const motionShowcaseScript: CompositionScript = {
  canvas: { width: 1080, height: 1920, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', fontTier: 'subtitle' as const, backgroundColor: '#0A0A0F' },
  scenes: [
    // ═══ Scene 1: 2.5D 漂浮卡片 — motion.float_2d5 ═══
    {
      sceneId: 'scene_1_float_card',
      sceneIndex: 0,
      role: 'hook',
      durationInFrames: 90,
      layers: [
        {
          layerId: 'bg_mesh',
          preset: 'bg.mesh_gradient',
          params: {
            colors: ['#0F0F23', '#1A1A3E', '#2D1B69', '#4F46E5'],
            intensity: 1.0,
          },
        },
        {
          layerId: 'bg_noise',
          preset: 'bg.noise_grain',
          params: {
            backgroundColor: 'transparent',
            grainOpacity: 0.08,
            scale: 1,
          },
        },
        {
          layerId: 'glass_backing',
          preset: 'backing.glass_plate',
          enterAtFrame: 4,
          durationInFrames: 82,
          params: {
            width: '78%',
            height: '62%',
            blurAmount: 20,
            tintColor: 'rgba(255,255,255,0.08)',
            borderRadius: 28,
            borderColor: 'rgba(255,255,255,0.12)',
            borderWidth: 1,
            position: { x: 'center', y: 'center' },
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.3)',
          },
        },
        {
          layerId: 'float_product',
          preset: 'motion.float_2d5',
          enterAtFrame: 6,
          durationInFrames: 78,
          params: {
            src: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80',
            floatAmplitude: 10,
            floatSpeed: 0.7,
            swayAmount: 2.5,
            scaleBreath: 0.025,
            perspective: 800,
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.3)',
            objectFit: 'contain',
            scale: 0.75,
          },
        },
        {
          layerId: 'label_tag',
          preset: 'text.label_chip',
          enterAtFrame: 12,
          durationInFrames: 68,
          params: {
            text: '🪩 2.5D Float',
            variant: 'filled',
            bgColor: '#7C3AED',
            color: '#FFFFFF',
            fontSize: 24,
            fontWeight: 700,
            position: { x: 'center', y: '80%' },
          },
        },
        {
          layerId: 'title_float',
          preset: 'text.kinetic_pop',
          enterAtFrame: 18,
          durationInFrames: 60,
          params: {
            text: '漂浮卡片',
            fontSize: 72,
            color: '#F5F3FF',
            fontWeight: 900,
            position: { x: 'center', y: '16%' },
            scaleFrom: 1.4,
            rotationFrom: 0,
            enterFrames: 14,
            settleFrames: 18,
            textShadow: '0 8px 32px rgba(124,58,237,0.4)',
          },
        },
        {
          layerId: 'desc_float',
          preset: 'text.typewriter',
          enterAtFrame: 36,
          durationInFrames: 42,
          params: {
            text: '上下浮动 · 摇摆 · 动态阴影',
            fontSize: 34,
            color: '#A5B4FC',
            fontWeight: 600,
            position: { x: 'center', y: '88%' },
            charIntervalFrames: 2,
            cursor: '',
          },
        },
      ],
    },
    // ═══ Scene 2: 视差漂移 — motion.parallax_drift ═══
    {
      sceneId: 'scene_2_parallax',
      sceneIndex: 1,
      role: 'body',
      durationInFrames: 100,
      layers: [
        {
          layerId: 'parallax_bg',
          preset: 'motion.parallax_drift',
          params: {
            src: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1080&q=80',
            driftRangeX: 28,
            driftRangeY: 18,
            driftSpeedX: 0.5,
            driftSpeedY: 0.7,
            scaleRange: 0.04,
            rotationRange: 1.2,
            objectFit: 'cover',
          },
        },
        {
          layerId: 'dark_overlay',
          preset: 'backing.solid_plate',
          params: {
            width: '100%',
            height: '100%',
            color: '#000000',
            opacity: 0.35,
            borderRadius: 0,
            padding: 0,
            position: { x: 'center', y: 'center' },
            shadowEnabled: false,
            enterFrames: 0,
          },
        },
        {
          layerId: 'title_parallax',
          preset: 'text.mask_reveal',
          enterAtFrame: 14,
          durationInFrames: 72,
          params: {
            text: '视差漂移',
            fontSize: 76,
            color: '#FFFFFF',
            fontWeight: 900,
            position: { x: 'center', y: '36%' },
            revealDirection: 'bottom_to_top',
            revealFrames: 24,
            textShadow: '0 6px 28px rgba(0,0,0,0.6)',
          },
        },
        {
          layerId: 'label_parallax',
          preset: 'text.label_chip',
          enterAtFrame: 22,
          durationInFrames: 62,
          params: {
            text: '🌊 李萨如轨迹',
            variant: 'soft',
            color: '#E0E7FF',
            bgColor: '#4F46E5',
            fontSize: 22,
            position: { x: 'center', y: '54%' },
          },
        },
        {
          layerId: 'desc_parallax',
          preset: 'text.fade_title',
          enterAtFrame: 36,
          durationInFrames: 50,
          params: {
            text: 'XY 异步正弦漂移\n深度移动感',
            fontSize: 36,
            color: '#CBD5E1',
            fontWeight: 600,
            position: { x: 'center', y: '70%' },
            textShadow: '0 2px 12px rgba(0,0,0,0.5)',
          },
        },
      ],
    },
    // ═══ Scene 3: 透视倾斜 — motion.perspective_tilt ═══
    {
      sceneId: 'scene_3_tilt',
      sceneIndex: 2,
      role: 'outro',
      durationInFrames: 90,
      layers: [
        {
          layerId: 'bg_mesh_tilt',
          preset: 'bg.mesh_gradient',
          params: {
            colors: ['#171347', '#1E1B4B', '#312E81', '#22D3EE'],
            intensity: 0.95,
          },
        },
        {
          layerId: 'bg_noise_tilt',
          preset: 'bg.noise_grain',
          params: {
            backgroundColor: 'transparent',
            grainOpacity: 0.07,
            scale: 1.1,
          },
        },
        {
          layerId: 'tilt_card',
          preset: 'motion.perspective_tilt',
          enterAtFrame: 6,
          durationInFrames: 78,
          params: {
            src: 'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=800&q=80',
            rotateX: -5,
            rotateY: 3,
            perspective: 1000,
            scale: 0.78,
            dynamicEnabled: true,
            dynamicRange: 2,
            shadowEnabled: true,
            shadowColor: 'rgba(0,0,0,0.35)',
            objectFit: 'contain',
          },
        },
        {
          layerId: 'title_tilt',
          preset: 'text.kinetic_pop',
          enterAtFrame: 14,
          durationInFrames: 62,
          params: {
            text: '透视倾斜',
            fontSize: 74,
            color: '#F8FAFC',
            fontWeight: 900,
            position: { x: 'center', y: '18%' },
            scaleFrom: 1.5,
            rotationFrom: 0,
            enterFrames: 14,
            settleFrames: 18,
            textShadow: '0 8px 32px rgba(34,211,238,0.35)',
          },
        },
        {
          layerId: 'label_tilt',
          preset: 'text.label_chip',
          enterAtFrame: 22,
          durationInFrames: 52,
          params: {
            text: '🃏 CSS 3D Perspective',
            variant: 'outlined',
            color: '#22D3EE',
            borderColor: 'rgba(34,211,238,0.6)',
            fontSize: 22,
            position: { x: 'center', y: '82%' },
          },
        },
        {
          layerId: 'glow_frame',
          preset: 'overlay.glow_frame',
          enterAtFrame: 4,
          durationInFrames: 80,
          params: {
            color: '#22D3EE',
            thickness: 8,
            glowBlur: 22,
            opacity: 0.5,
            borderRadius: 28,
            inset: 20,
          },
        },
      ],
    },
  ],
  transitions: [
    {
      fromSceneIndex: 0,
      toSceneIndex: 1,
      preset: 'transition.fade',
      params: {
        durationInFrames: 18,
        timing: 'linear',
      },
      overlay: {
        preset: 'overlay.flash',
        params: { color: '#FFFFFF', maxOpacity: 0.5, enterFrames: 2, holdFrames: 1, exitFrames: 8 },
      },
    },
    {
      fromSceneIndex: 1,
      toSceneIndex: 2,
      preset: 'transition.slide',
      params: {
        direction: 'from-bottom',
        durationInFrames: 16,
        timing: 'spring',
      },
    },
  ],
};

const showcaseLandscapeScript: CompositionScript = {
  canvas: { width: 1920, height: 1080, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', fontTier: 'subtitle' as const, backgroundColor: '#0B1120' },
  scenes: [
    {
      sceneId: "landscape_scene_1",
      sceneIndex: 0,
      role: "hook",
      durationInFrames: 96,
      layers: [
        {
          layerId: "bg_mesh_landscape",
          preset: "bg.mesh_gradient",
          params: {
            colors: ["#07111F", "#0F3D6E", "#174C75", "#4F46E5"],
            intensity: 0.94,
            layoutMode: "landscape"
          }
        },
        {
          layerId: "bg_noise_landscape",
          preset: "bg.noise_grain",
          params: {
            backgroundColor: "transparent",
            grainOpacity: 0.1,
            scale: 1,
            layoutMode: "landscape"
          }
        },
        {
          layerId: "landscape_pop",
          preset: "text.kinetic_pop",
          enterAtFrame: 8,
          durationInFrames: 34,
          params: {
            text: "横屏也能打",
            fontSize: 138,
            color: "#F8FAFC",
            layoutMode: "landscape",
            positionPreset: "hero_top",
            scaleFrom: 1.8,
            rotationFrom: -5
          }
        },
        {
          layerId: "landscape_reveal",
          preset: "text.mask_reveal",
          enterAtFrame: 42,
          durationInFrames: 28,
          params: {
            text: "纯代码背景自动适配",
            fontSize: 76,
            color: "#7DD3FC",
            layoutMode: "landscape",
            positionPreset: "hero_center",
            revealDirection: "left_to_right",
            revealFrames: 20
          }
        }
      ]
    },
    {
      sceneId: "landscape_scene_2",
      sceneIndex: 1,
      role: "body",
      durationInFrames: 108,
      layers: [
        {
          layerId: "bg_grid_landscape",
          preset: "bg.tech_grid",
          params: {
            backgroundColor: "#06111F",
            lineColor: "#67E8F9",
            accentColor: "#22D3EE",
            gridSize: 76,
            lineOpacity: 0.24,
            driftSpeed: 16,
            layoutMode: "landscape"
          }
        },
        {
          layerId: "bg_noise_landscape_2",
          preset: "bg.noise_grain",
          params: {
            backgroundColor: "transparent",
            grainOpacity: 0.08,
            scale: 0.9,
            layoutMode: "landscape"
          }
        },
        {
          layerId: "landscape_billboard",
          preset: "text.hero_billboard",
          enterAtFrame: 14,
          durationInFrames: 80,
          params: {
            texts: ["纯代码背景", "横屏也能打"],
            layoutPattern: "left_right_balance",
            animationMode: "char_stagger",
            easingPreset: "spring_bounce",
            fontSize: 70,
            color: "#E2E8F0",
            accentColor: "#F8C630",
            layoutMode: "landscape",
            letterSpacing: "0.08em",
            strokeEnabled: true,
            strokeColor: "#08111F",
            strokeWidth: 2.2,
            glowColor: "#67E8F9",
            glowBlur: 26,
            glowOpacity: 0.28
          }
        },
        {
          layerId: "landscape_badge",
          preset: "overlay.badge_pop",
          enterAtFrame: 12,
          durationInFrames: 40,
          params: {
            text: "LANDSCAPE",
            bgColor: "#FF6B35",
            color: "#FFFFFF",
            position: { x: "6%", y: "10%" }
          }
        }
      ]
    }
  ],
  transitions: [
    {
      fromSceneIndex: 0,
      toSceneIndex: 1,
      preset: "transition.fade",
      params: {
        durationInFrames: 18,
        timing: "linear"
      },
      overlay: {
        preset: "overlay.flash",
        params: { color: "#FFFFFF", maxOpacity: 0.75, enterFrames: 2, holdFrames: 1, exitFrames: 9 }
      }
    }
  ]
};

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="DynamicVideo"
        component={DynamicVideoRenderer}
        durationInFrames={270}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={showcaseScript}
        schema={CompositionScriptSchema}
        calculateMetadata={calculateMetadata}
      />
      <Composition
        id="DynamicVideoLandscape"
        component={DynamicVideoRenderer}
        durationInFrames={186}
        fps={30}
        width={1920}
        height={1080}
        defaultProps={showcaseLandscapeScript}
        schema={CompositionScriptSchema}
        calculateMetadata={calculateMetadata}
      />
      <Composition
        id="NewComponentsShowcase"
        component={DynamicVideoRenderer}
        durationInFrames={226}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={newComponentsShowcaseScript}
        schema={CompositionScriptSchema}
        calculateMetadata={calculateMetadata}
      />
      <Composition
        id="MotionShowcase"
        component={DynamicVideoRenderer}
        durationInFrames={244}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={motionShowcaseScript}
        schema={CompositionScriptSchema}
        calculateMetadata={calculateMetadata}
      />
    </>
  );
};
