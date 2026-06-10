const fs = require('fs');
const file = 'd:/Develop/code/bytedance-ai-video/frontend/src/pages/create/ProjectGeneration.tsx';
let content = fs.readFileSync(file, 'utf8');

const returnStart = content.indexOf('  return (\n');
if (returnStart === -1) {
  console.log('Could not find return statement');
  process.exit(1);
}

// Add ProjectCreationTabs back to imports if it is missing
if (!content.includes('import { ProjectCreationTabs }')) {
  content = content.replace("import { ScenePreviewer }", "import { ProjectCreationTabs } from '../../components/ProjectCreationTabs';\nimport { ScenePreviewer }");
}

const newReturn = `  return (
    <div className="flex flex-col h-screen overflow-hidden bg-slate-50">
      {/* Header */}
      <header className="flex-none bg-white border-b border-slate-200 z-20">
        <div className="flex items-center justify-between px-6 py-4">
          <div className="flex items-center gap-4">
            <button 
              onClick={() => navigate(\`/create/detail/\${projectId}/gap-detection\`)}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors text-slate-500 hover:text-slate-800"
              title="返回适配阶段"
            >
              <ArrowLeft size={20} />
            </button>
            <h2 className="text-xl font-bold text-slate-800 flex items-center gap-2">
              创作工作流 <span className="text-slate-400 font-normal">/</span> 智能生成 <span className="text-slate-400 font-normal">/</span> {projectTitle || '未命名项目'}
            </h2>
          </div>
          <div className="flex items-center gap-4">
            <div className="px-3 py-1 bg-slate-100 rounded-full text-xs font-medium text-slate-600 border border-slate-200">
              状态: {projectStatus}
            </div>
          </div>
        </div>
        <ProjectCreationTabs projectId={projectId} activeTab="generation" />
      </header>

      {/* Main Workspace */}
      <main className="flex-1 flex overflow-hidden relative max-w-[1600px] mx-auto w-full">
        
        {/* === Left Column: Editor Workspace === */}
        <div className="flex-1 bg-white border-r border-slate-200 flex flex-col z-10 shadow-sm overflow-hidden">
          
          {/* Global Config Compact Bar */}
          <div className="p-4 border-b border-slate-200 bg-slate-50 flex flex-wrap items-center gap-x-8 gap-y-2">
             <h3 className="flex items-center gap-2 font-semibold text-slate-800 text-sm">
               <Settings2 size={16} className="text-slate-500" />
               全局基调策略
             </h3>
             <div className="flex items-center gap-6">
                <div className="flex items-center gap-2">
                   <label className="text-xs font-medium text-slate-600 flex items-center gap-1.5"><Palette size={14} className="text-slate-400"/> 视觉色彩</label>
                   <div className="flex items-center bg-white border border-slate-200 rounded p-0.5 shadow-sm">
                     <input
                        type="color"
                        value={localScript?.globalStyle?.backgroundColor || '#050F1E'}
                        onChange={e => updateLocalScript('backgroundColor', e.target.value)}
                        className="w-5 h-5 rounded cursor-pointer border-0 p-0 bg-transparent"
                     />
                   </div>
                </div>
                <div className="flex items-center gap-2">
                   <label className="text-xs font-medium text-slate-600 flex items-center gap-1.5"><Volume2 size={14} className="text-slate-400"/> 听觉策略</label>
                   <select
                      className="bg-white border border-slate-200 text-slate-700 rounded py-1 px-2 focus:outline-none focus:border-blue-500 text-xs w-24 shadow-sm"
                      value={localScript?.bgm?.mixLevel || 'BALANCED'}
                      onChange={e => updateLocalScript('mixLevel', e.target.value)}
                   >
                      <option value="QUIET">舒缓</option>
                      <option value="BALANCED">均衡</option>
                      <option value="DRIVE">强烈</option>
                   </select>
                </div>
             </div>
          </div>

          {/* Master-Detail Timeline Body */}
          <div className="flex-1 flex overflow-hidden">
            {/* Left Nav */}
            <div className="w-[280px] border-r border-slate-200 bg-slate-50 overflow-y-auto custom-scrollbar p-4 space-y-3 shrink-0">
             {localScript?.scenes?.map((scene: any, sceneIdx: number) => {
                const isSceneActive = activeSelection.type === 'scene' && activeSelection.index === sceneIdx;
                const previewText = scene.layers?.find((l: any) => l.preset?.startsWith('text.'))?.params?.text 
                  || scene.layers?.find((l: any) => l.preset === 'caption.subtitle')?.params?.text
                  || '无文本或仅素材';
                
                const transition = localScript.transitions?.find((t: any) => t.fromSceneIndex === sceneIdx && t.toSceneIndex === sceneIdx + 1);
                const isTransitionActive = activeSelection.type === 'transition' && activeSelection.index === sceneIdx;

                return (
                  <React.Fragment key={\`nav-\${sceneIdx}\`}>
                    <div 
                      onClick={() => setActiveSelection({type: 'scene', index: sceneIdx})}
                      className={\`p-4 rounded-xl cursor-pointer transition-all border \${
                        isSceneActive ? 'bg-blue-50 border-blue-200 shadow-sm ring-1 ring-blue-500/20' : 'bg-white border-slate-200 hover:border-blue-300 hover:shadow-sm'
                      }\`}
                    >
                      <div className="flex justify-between items-center mb-2">
                        <span className={\`font-bold text-sm flex items-center gap-1.5 \${isSceneActive ? 'text-blue-700' : 'text-slate-700'}\`}>
                          <Clapperboard size={15} className={isSceneActive ? 'text-blue-500' : 'text-slate-400'} />
                          分镜 {sceneIdx + 1}
                        </span>
                        <span className="text-xs font-mono bg-white px-2 py-0.5 rounded border border-slate-100 text-slate-500 shadow-sm">{(scene.durationInFrames / fps).toFixed(1)}s</span>
                      </div>
                      <div className="text-xs text-slate-500 truncate mt-1 bg-slate-50/80 rounded px-2 py-1.5">{previewText}</div>
                    </div>

                    {sceneIdx < localScript.scenes.length - 1 && (
                      <div className="flex justify-center -my-2.5 z-10 relative">
                        <button
                          onClick={() => setActiveSelection({type: 'transition', index: sceneIdx})}
                          className={\`w-8 h-8 rounded-full flex items-center justify-center border transition-all shadow-sm z-20 \${
                            isTransitionActive
                              ? 'bg-blue-600 border-blue-700 text-white scale-110 shadow-blue-500/30'
                              : transition?.preset && transition.preset !== 'none'
                                ? 'bg-blue-50 border-blue-200 text-blue-600 hover:bg-blue-100'
                                : 'bg-white border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-slate-600'
                          }\`}
                          title="设置转场"
                        >
                          {(!transition?.preset || transition.preset === 'none') ? <Scissors size={14} /> : <Link size={14} />}
                        </button>
                      </div>
                    )}
                  </React.Fragment>
                );
             })}
            </div>

            {/* Right Details (Inspector) */}
            <div className="flex-1 overflow-y-auto custom-scrollbar bg-white p-8 relative">
             {localScript && activeSelection.type === 'scene' && (
                <SceneInspector 
                   scene={localScript.scenes[activeSelection.index]} 
                   sceneIndex={activeSelection.index}
                   updateSceneDuration={updateSceneDuration}
                   updateLayerParam={updateLayerParam}
                   updateLayerText={updateLayerText}
                />
             )}
             {localScript && activeSelection.type === 'transition' && (
                <TransitionInspector 
                   sceneIdx={activeSelection.index}
                   transition={localScript.transitions?.find((t: any) => t.fromSceneIndex === activeSelection.index && t.toSceneIndex === activeSelection.index + 1)}
                   updateTransition={updateTransition}
                   updateTransitionParam={updateTransitionParam}
                />
             )}
            </div>
          </div>
        </div>

        {/* === Right Column: Auxiliary Previews === */}
        <div className="w-[480px] flex-none bg-slate-100 flex flex-col z-10 shadow-[-2px_0_10px_-5px_rgba(0,0,0,0.1)] border-l border-slate-200 overflow-y-auto custom-scrollbar">
           
           {/* CSS Previewer */}
           <div className="p-5 flex flex-col gap-3 bg-white border-b border-slate-200">
             <div className="flex items-center gap-2 text-sm font-bold text-slate-800">
                <MonitorPlay size={16} className="text-blue-500" />
                效果预览 (Scene {activeSelection.index + 1})
             </div>
             {/* Use aspect-video to maintain accurate proportions! */}
             <div className="w-full aspect-video bg-slate-900 rounded-xl shadow-inner border border-slate-800 overflow-hidden relative flex items-center justify-center">
                {localScript ? (
                   <ScenePreviewer script={localScript} sceneIndex={activeSelection.index} aspectRatio={aspectRatio} />
                ) : (
                   <span className="text-slate-500 text-xs">加载中...</span>
                )}
             </div>
           </div>

           {/* Render Operations */}
           <div className="p-5 bg-white border-b border-slate-200">
             <div className="flex justify-between items-center mb-3">
               <div className="flex items-center gap-2 font-bold text-slate-800 text-sm">
                 <Activity size={16} className={isOvertime ? 'text-red-500' : 'text-emerald-500'} />
                 时轴健康度
               </div>
               <span className={\`text-xs font-bold \${isOvertime ? 'text-red-500' : 'text-emerald-600'}\`}>
                 {totalSeconds}s / {safeSecondsLimit}s
               </span>
             </div>
             <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden mb-3">
               <div className={\`h-full transition-all duration-300 \${isOvertime ? 'bg-red-500' : 'bg-emerald-500'}\`}
                    style={{ width: \`\${Math.min(100, (parseFloat(totalSeconds) / safeSecondsLimit) * 100)}%\` }} />
             </div>
             {isOvertime && (
               <p className="text-[11px] text-red-500 mb-4 font-medium">总时长超出限制，音乐将发生硬切断。</p>
             )}
             <button
               className={\`w-full py-3 rounded-xl font-bold flex items-center justify-center gap-2 transition-all shadow-sm text-sm \${
                 isOvertime 
                   ? 'bg-slate-100 text-slate-400 cursor-not-allowed shadow-none' 
                   : 'bg-slate-900 hover:bg-black text-white hover:shadow-md hover:-translate-y-0.5'
               }\`}
               onClick={startRender}
               disabled={isOvertime || isRendering}
             >
               {isRendering ? <Loader2 size={18} className="animate-spin" /> : <MonitorPlay size={18} />}
               {isRendering ? '引擎极速渲染中...' : '确认编排并生成视频'}
             </button>
           </div>

           {/* Final Video Output */}
           <div className="p-5 flex-1 flex flex-col gap-3">
             <div className="flex justify-between items-center">
                <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
                   <CheckCircle2 size={16} className="text-emerald-500"/> 最终出片
                </h3>
             </div>
             <div className="w-full aspect-video bg-black rounded-xl overflow-hidden shadow-inner border-2 border-slate-200 flex items-center justify-center relative">
               {(isRendering || (projectStatus === 'DONE' && videoUrl)) ? (
                 isRendering ? (
                   <div className="text-center w-full px-4">
                     <Loader2 size={32} className="text-blue-500 animate-spin mx-auto mb-3" />
                     <div className="text-blue-400 text-xs font-medium mb-2">排队渲染中... {Math.floor(renderProgress * 100)}%</div>
                     <div className="h-1 bg-slate-800 rounded-full overflow-hidden">
                       <div className="h-full bg-blue-500" style={{ width: \`\${Math.max(5, renderProgress * 100)}%\` }} />
                     </div>
                   </div>
                 ) : (
                   <video src={videoUrl!} controls autoPlay className="w-full h-full object-contain" />
                 )
               ) : (
                 <div className="text-slate-500 text-xs flex flex-col items-center gap-2">
                    <Film size={28} className="opacity-30"/>
                    <span className="font-medium">等待生成视频</span>
                 </div>
               )}
             </div>
             {projectStatus === 'DONE' && (
                <button 
                  className="w-full py-2.5 mt-2 text-slate-600 hover:bg-white border border-slate-300 rounded-xl text-sm font-semibold transition-colors shadow-sm"
                  onClick={() => setProjectStatus('DRAFT')}
                >
                  退回修改重渲
                </button>
             )}
           </div>
        </div>

      </main>

      {/* Gen Script Overlay */}
      {(isGeneratingScript || (projectStatus !== 'SCRIPT_DONE' && projectStatus !== 'DONE' && !isRendering && !localScript)) && (
        <div className="absolute inset-0 z-[100] bg-white/90 backdrop-blur-md flex flex-col items-center justify-center p-6">
           <div className="max-w-md w-full bg-white p-8 rounded-2xl border border-slate-200 shadow-2xl text-center">
             <div className="w-16 h-16 bg-blue-50 rounded-2xl flex items-center justify-center mx-auto mb-6 text-blue-500 shadow-inner">
               <Wand2 size={32} />
             </div>
             <h3 className="text-2xl font-bold text-slate-800 mb-4">大模型结构编排</h3>
             
             {!isGeneratingScript ? (
               <>
                 <p className="text-slate-500 mb-6 text-sm font-medium">选择多版本策略，AI 将自动规划画面与节奏。</p>
                 <select
                   className="w-full bg-slate-50 border border-slate-300 text-slate-800 font-medium rounded-xl py-3.5 px-4 mb-6 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500"
                   value={versionStrategy}
                   onChange={e => setVersionStrategy(e.target.value)}
                 >
                   <option value="balanced">【均衡原版】自然节奏，视听平衡</option>
                   <option value="fast_paced">【高频卡点版】快节奏，快速转场</option>
                   <option value="brand_quality">【品牌质感版】慢镜头，舒缓质感</option>
                 </select>
                 <button
                   className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 rounded-xl shadow-lg shadow-blue-500/20 transition-all hover:-translate-y-0.5 active:translate-y-0 flex items-center justify-center gap-2"
                   onClick={startGenerateScript}
                 >
                   <Wand2 size={18} /> 开始生成底层代码
                 </button>
               </>
             ) : (
               <>
                 <div className="flex items-center justify-between text-sm font-bold text-blue-600 mb-3">
                   <span className="flex items-center gap-2">
                     <Loader2 size={16} className="animate-spin" /> {getProgressText(scriptProgress)}
                   </span>
                   <span>{Math.floor(scriptProgress * 100)}%</span>
                 </div>
                 <div className="h-2.5 bg-slate-100 rounded-full overflow-hidden">
                   <div className="h-full bg-blue-500 rounded-full relative" style={{ width: \`\${Math.max(5, scriptProgress * 100)}%\` }}>
                     <div className="absolute inset-0 bg-white/20 w-full animate-[shimmer_2s_infinite]" />
                   </div>
                 </div>
               </>
             )}
             {errorMsg && !isGeneratingScript && (
                <div className="mt-6 p-4 bg-red-50 text-red-600 rounded-xl border border-red-100 text-left text-sm">
                  <strong>生成失败:</strong> {errorMsg}
                </div>
             )}
           </div>
        </div>
      )}
    </div>
  );
}

// ---------------- Inspector Components ----------------

function SceneInspector({ scene, sceneIndex, updateSceneDuration, updateLayerParam, updateLayerText }: any) {
  const visualLayers = scene.layers?.filter((l: any) => 
    l.preset?.startsWith('text.') || l.preset?.startsWith('media.') || 
    l.preset?.startsWith('bg.') || l.preset?.startsWith('backing.') || 
    l.preset?.startsWith('overlay.') || l.preset?.startsWith('caption.') || 
    l.preset?.startsWith('motion.')
  ) || [];
  const audioLayers = scene.layers?.filter((l: any) => l.preset === 'media.audio') || [];

  return (
    <div className="max-w-3xl mx-auto space-y-8 pb-10 animate-in fade-in slide-in-from-bottom-4 duration-300">
      <div className="flex items-center gap-4 mb-6 border-b border-slate-100 pb-6">
        <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-100 shadow-sm">
          <span className="font-bold text-xl">{sceneIndex + 1}</span>
        </div>
        <div>
          <h3 className="text-xl font-bold text-slate-800">分镜 {sceneIndex + 1} 详细参数</h3>
          <p className="text-sm text-slate-500">调整当前镜头的画面结构与视觉持续时间</p>
        </div>
      </div>

      <div className="bg-slate-50/50 p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div className="flex justify-between items-center text-sm font-semibold text-slate-700 mb-4">
          <span className="flex items-center gap-2"><Clock size={16} className="text-slate-400"/> 镜头时长设定</span>
          <span className="text-blue-600 bg-blue-100 px-3 py-1 rounded-lg font-mono tracking-wide">{scene.durationInFrames} 帧 ({(scene.durationInFrames/30).toFixed(1)}s)</span>
        </div>
        <input
          type="range" min="30" max="300" step="10"
          value={scene.durationInFrames || 90}
          onChange={(e) => updateSceneDuration(sceneIndex, parseInt(e.target.value))}
          className="w-full h-2.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-500 hover:accent-blue-600 transition-all"
        />
      </div>

      {visualLayers.length > 0 && (
        <div className="space-y-4">
          <h4 className="flex items-center gap-2 text-sm font-bold text-slate-800">
            <Film size={18} className="text-blue-500"/> 视觉图层体系 (Visuals)
          </h4>
          <div className="grid grid-cols-1 gap-4">
            {visualLayers.map((layer: any) => {
              const layerIdx = scene.layers.indexOf(layer);
              return (
                <div key={\`visual-\${layerIdx}\`} className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden hover:border-blue-200 transition-colors">
                  <LayerRenderer layer={layer} sceneIdx={sceneIndex} layerIdx={layerIdx} updateLayerParam={updateLayerParam} updateLayerText={updateLayerText} />
                </div>
              );
            })}
          </div>
        </div>
      )}

      {audioLayers.length > 0 && (
        <div className="space-y-4">
          <h4 className="flex items-center gap-2 text-sm font-bold text-slate-800">
            <Music size={18} className="text-emerald-500"/> 独立声音效果 (Audio)
          </h4>
          <div className="flex flex-col gap-3">
            {audioLayers.map((layer: any) => {
              const layerIdx = scene.layers.indexOf(layer);
              return (
                <div key={\`audio-\${layerIdx}\`} className="bg-emerald-50/50 border border-emerald-100 px-5 py-4 rounded-2xl flex items-center gap-4">
                  <div className="bg-emerald-100 p-2.5 rounded-xl text-emerald-600 shadow-sm"><Music size={18} /></div>
                  <div className="flex-1 flex flex-col">
                    <span className="font-bold text-emerald-900 text-sm">{layer.params?.audioRole || '未定义角色'}</span>
                    <span className="text-xs text-emerald-600 mt-1 font-medium">类型标识: {layer.params?.cueType || '默认音效'}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function TransitionInspector({ sceneIdx, transition, updateTransition, updateTransitionParam }: any) {
  const currentPreset = transition?.preset || 'none';
  return (
    <div className="max-w-2xl mx-auto space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-300">
      <div className="flex items-center gap-4 mb-6 border-b border-slate-100 pb-6">
        <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0 border border-indigo-100 shadow-sm">
          <Link size={24} />
        </div>
        <div>
          <h3 className="text-xl font-bold text-slate-800">转场节点设置</h3>
          <p className="text-sm text-slate-500">连接分镜 {sceneIdx + 1} 到 分镜 {sceneIdx + 2} 的动画过渡</p>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-6">
        <div>
          <label className="block text-sm font-bold text-slate-700 mb-3">选择转场效果</label>
          <select
            value={currentPreset}
            onChange={e => updateTransition(sceneIdx, sceneIdx + 1, e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-slate-800 font-medium rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 appearance-none"
          >
            <option value="none">无转场 (直接硬切)</option>
            <option value="transition.fade">淡入淡出 (Fade)</option>
            <option value="transition.slide">滑动推入 (Slide)</option>
            <option value="transition.wipe">线性擦除 (Wipe)</option>
          </select>
        </div>

        {currentPreset !== 'none' && (
          <div className="bg-slate-50/80 p-5 rounded-xl border border-slate-200 space-y-5">
            <div className="flex items-center justify-between gap-4">
              <label className="text-sm font-semibold text-slate-700 shrink-0">持续帧数 (Duration)</label>
              <div className="flex items-center gap-2">
                <input
                  type="number" value={transition?.params?.durationInFrames || 15} min={5} max={60}
                  onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'durationInFrames', parseInt(e.target.value))}
                  className="w-24 bg-white border border-slate-200 text-center font-mono text-slate-800 rounded-lg py-2 px-3 focus:outline-none focus:border-indigo-500 shadow-sm"
                />
                <span className="text-sm font-medium text-slate-500">帧</span>
              </div>
            </div>

            <div className="flex items-center justify-between gap-4">
              <label className="text-sm font-semibold text-slate-700 shrink-0">缓动曲线 (Easing)</label>
              <select
                value={transition?.params?.timing || 'linear'}
                onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'timing', e.target.value)}
                className="w-48 bg-white border border-slate-200 text-slate-800 font-medium rounded-lg py-2 px-3 focus:outline-none focus:border-indigo-500 shadow-sm"
              >
                <option value="linear">线性平滑 (Linear)</option>
                <option value="spring">物理弹簧 (Spring)</option>
              </select>
            </div>

            {currentPreset === 'transition.slide' && (
              <div className="flex items-center justify-between gap-4 pt-2 border-t border-slate-200/60">
                <label className="text-sm font-semibold text-slate-700 shrink-0">推入方向 (Direction)</label>
                <select
                  value={transition?.params?.direction || 'from-left'}
                  onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'direction', e.target.value)}
                  className="w-48 bg-white border border-slate-200 text-slate-800 font-medium rounded-lg py-2 px-3 focus:outline-none focus:border-indigo-500 shadow-sm"
                >
                  <option value="from-left">← 从左侧推入</option>
                  <option value="from-right">→ 从右侧推入</option>
                  <option value="from-top">↑ 从上方落下</option>
                  <option value="from-bottom">↓ 从下方弹起</option>
                </select>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
`;

const head = content.substring(0, returnStart);
const finalContent = head + newReturn;

fs.writeFileSync(file, finalContent);
console.log('Successfully replaced ProjectGeneration.tsx with 2-column layout');
