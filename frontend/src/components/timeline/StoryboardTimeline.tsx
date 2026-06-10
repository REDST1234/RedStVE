import React from 'react';
import { TransitionNode } from './TransitionNode';
import { LayerRenderer } from '../../pages/create/LayerRenderer';
import { Clapperboard, Film, Music, Clock } from 'lucide-react';

interface StoryboardTimelineProps {
  localScript: any;
  activeSceneIndex: number;
  setActiveSceneIndex: (index: number) => void;
  updateSceneDuration: (index: number, duration: number) => void;
  updateLayerParam: (sceneIdx: number, layerIdx: number, key: string, value: any) => void;
  updateLayerText: (sceneIdx: number, layerIdx: number, text: string) => void;
  updateLayerBaseProp: (sceneIdx: number, layerIdx: number, propKey: string, value: number) => void;
  updateTransition: (fromIdx: number, toIdx: number, preset: string) => void;
  updateTransitionParam: (fromIdx: number, toIdx: number, key: string, value: any) => void;
}

export function StoryboardTimeline({
  localScript,
  activeSceneIndex,
  setActiveSceneIndex,
  updateSceneDuration,
  updateLayerParam,
  updateLayerText,
  updateLayerBaseProp,
  updateTransition,
  updateTransitionParam
}: StoryboardTimelineProps) {
  const fps = 30;

  if (!localScript?.scenes) return null;

  return (
    <div className="bg-white rounded-xl shadow-sm border border-slate-200 w-full max-w-6xl mx-auto overflow-hidden flex flex-col h-[600px]">
      <div className="flex items-center gap-2 px-6 py-4 border-b border-slate-200 bg-slate-50/50 shrink-0">
        <Film className="text-blue-500" size={20} />
        <h4 className="font-semibold text-slate-800">分镜时间线 (Storyboard)</h4>
        <span className="text-xs text-slate-500 ml-2">主从视图：左侧选择分镜，右侧精细调节</span>
      </div>

      <div className="flex flex-1 min-h-0">
        {/* Left Pane: Scene Navigator */}
        <div className="w-[280px] border-r border-slate-200 bg-slate-50 overflow-y-auto flex flex-col custom-scrollbar shrink-0">
          {localScript.scenes.map((scene: any, sceneIdx: number) => {
            const isActive = activeSceneIndex === sceneIdx;
            // 尝试提取一个文本片段作为预览摘要
            const previewText = scene.layers?.find((l: any) => l.preset?.startsWith('text.'))?.params?.text 
              || scene.layers?.find((l: any) => l.preset === 'caption.subtitle')?.params?.text
              || '无文本或仅素材';

            return (
              <React.Fragment key={`nav-${sceneIdx}`}>
                <div 
                  onClick={() => setActiveSceneIndex(sceneIdx)}
                  className={`p-4 border-b border-slate-100 cursor-pointer transition-colors relative ${
                    isActive ? 'bg-white border-l-4 border-l-blue-500' : 'hover:bg-slate-100 border-l-4 border-l-transparent'
                  }`}
                >
                  <div className="flex justify-between items-center mb-1">
                    <span className="font-medium text-slate-700 text-sm flex items-center gap-1.5">
                      <Clapperboard size={14} className={isActive ? 'text-blue-500' : 'text-slate-400'} />
                      分镜 {sceneIdx + 1}
                    </span>
                    <span className="text-xs font-mono text-slate-500">{(scene.durationInFrames / fps).toFixed(1)}s</span>
                  </div>
                  <div className="text-xs text-slate-400 truncate mt-1">
                    {previewText}
                  </div>
                </div>

                {/* Vertically placed Transition Node */}
                {sceneIdx < localScript.scenes.length - 1 && (
                  <div className="flex justify-center -my-3.5 z-10 relative pointer-events-none">
                    <div className="pointer-events-auto">
                      <TransitionNode
                        sceneIdx={sceneIdx}
                        transition={localScript.transitions?.find((t: any) => t.fromSceneIndex === sceneIdx && t.toSceneIndex === sceneIdx + 1)}
                        updateTransition={updateTransition}
                        updateTransitionParam={updateTransitionParam}
                      />
                    </div>
                  </div>
                )}
              </React.Fragment>
            );
          })}
        </div>

        {/* Right Pane: Active Scene Details */}
        <div className="flex-1 overflow-y-auto bg-white p-6 custom-scrollbar relative">
          {(() => {
            const scene = localScript.scenes[activeSceneIndex];
            if (!scene) return <div className="flex h-full items-center justify-center text-slate-400">选择分镜以查看详情</div>;
            
            const visualLayers = scene.layers?.filter((l: any) => 
              l.preset?.startsWith('text.') || l.preset?.startsWith('media.') || 
              l.preset?.startsWith('bg.') || l.preset?.startsWith('backing.') || 
              l.preset?.startsWith('overlay.') || l.preset?.startsWith('caption.') || 
              l.preset?.startsWith('motion.')
            ) || [];
            
            const audioLayers = scene.layers?.filter((l: any) => l.preset === 'media.audio') || [];

            return (
              <div className="max-w-3xl mx-auto space-y-8 pb-10">
                {/* Header & Duration */}
                <div className="flex flex-col gap-5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-100">
                      <span className="font-bold text-lg">{activeSceneIndex + 1}</span>
                    </div>
                    <div>
                      <h3 className="text-lg font-semibold text-slate-800">分镜 {activeSceneIndex + 1} 详细配置</h3>
                      <p className="text-xs text-slate-500">在此面板中调整画面内容与时间节奏</p>
                    </div>
                  </div>

                  <div className="bg-slate-50 p-5 rounded-xl border border-slate-200">
                    <div className="flex justify-between text-sm font-medium text-slate-600 mb-4">
                      <span className="flex items-center gap-1.5"><Clock size={16} className="text-slate-400"/> 镜头时长</span>
                      <span className="text-blue-600 bg-blue-100 px-2 py-0.5 rounded-md font-mono">{scene.durationInFrames} 帧 ({(scene.durationInFrames / fps).toFixed(1)}s)</span>
                    </div>
                    <input
                      type="range"
                      min="30" max="300" step="10"
                      value={scene.durationInFrames || 90}
                      onChange={(e) => updateSceneDuration(activeSceneIndex, parseInt(e.target.value))}
                      className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-500 hover:accent-blue-600"
                    />
                  </div>
                </div>

                {/* Visual Tracks */}
                {visualLayers.length > 0 && (
                  <div className="space-y-4">
                    <h4 className="flex items-center gap-2 text-sm font-semibold text-slate-700 border-b border-slate-100 pb-2">
                      <Film size={16} className="text-blue-500"/> 视觉图层 (Visuals)
                    </h4>
                    <div className="grid grid-cols-1 gap-4">
                      {visualLayers.map((layer: any) => {
                        const layerIdx = scene.layers.indexOf(layer);
                        return (
                          <div key={`visual-${layerIdx}`} className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden hover:border-blue-200 transition-colors">
                            <LayerRenderer
                              layer={layer}
                              sceneIdx={activeSceneIndex}
                              layerIdx={layerIdx}
                              updateLayerParam={updateLayerParam}
                              updateLayerText={updateLayerText}
                              updateLayerBaseProp={updateLayerBaseProp}
                            />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Audio Tracks */}
                {audioLayers.length > 0 && (
                  <div className="space-y-4">
                    <h4 className="flex items-center gap-2 text-sm font-semibold text-slate-700 border-b border-slate-100 pb-2">
                      <Music size={16} className="text-emerald-500"/> 声音效果 (Audio)
                    </h4>
                    <div className="flex flex-col gap-3">
                      {audioLayers.map((layer: any) => {
                        const layerIdx = scene.layers.indexOf(layer);
                        return (
                          <div key={`audio-${layerIdx}`} className="bg-emerald-50 border border-emerald-100 px-4 py-3 rounded-xl flex items-center gap-3">
                            <div className="bg-emerald-100 p-2 rounded-lg text-emerald-600">
                              <Music size={16} />
                            </div>
                            <div className="flex-1 flex flex-col">
                              <span className="font-medium text-emerald-800 text-sm">
                                {layer.params?.audioRole || '未定义角色'}
                              </span>
                              <span className="text-xs text-emerald-600 mt-0.5">
                                音效类型: {layer.params?.cueType || '默认音效'}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            );
          })()}
        </div>
      </div>
    </div>
  );
}
