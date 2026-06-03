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

/** 根据编排 JSON 动态计算视频元数据 */
const calculateMetadata: CalculateMetadataFunction<CompositionScript> = async ({ props }) => {
  const totalSceneFrames = props.scenes.reduce((sum, s) => sum + s.durationInFrames, 0);
  const transitionOverlap = props.transitions.reduce(
    (sum, t) => sum + (t.params.durationInFrames || 0),
    0,
  );

  return {
    durationInFrames: Math.max(1, totalSceneFrames - transitionOverlap),
    width: props.canvas.width,
    height: props.canvas.height,
    fps: props.canvas.fps,
  };
};

/** 全面展示预设库能力的测试脚本 */
const showcaseScript: CompositionScript = {
  canvas: { width: 1080, height: 1920, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', backgroundColor: '#111111' },
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
      sceneId: "scene_2_product_card",
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
          layerId: "title_2",
          preset: "text.word_highlight",
          enterAtFrame: 20,
          durationInFrames: 100,
          params: {
            tokens: ["Ken", "Burns", "运镜", "演示"],
            highlightWords: ["运镜", "演示"],
            fontSize: 58,
            color: "#FFFFFF",
            highlightColor: "#111111",
            highlightBackground: "#F8C630",
            position: { x: "center", y: "45%" },
            wordDurationInFrames: 12
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

const showcaseLandscapeScript: CompositionScript = {
  canvas: { width: 1920, height: 1080, fps: 30 },
  globalStyle: { fontFamily: 'Noto Sans SC', backgroundColor: '#0B1120' },
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
          layerId: "landscape_words",
          preset: "text.word_highlight",
          enterAtFrame: 14,
          durationInFrames: 80,
          params: {
            tokens: ["mesh", "gradient", "tech", "grid", "noise"],
            highlightWords: ["tech", "grid"],
            fontSize: 64,
            color: "#E2E8F0",
            highlightColor: "#111111",
            highlightBackground: "#F8C630",
            layoutMode: "landscape",
            positionPreset: "hero_center",
            wordDurationInFrames: 12
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
    </>
  );
};
