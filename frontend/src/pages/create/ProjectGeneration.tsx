import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { ProjectCreationTabs } from '../../components/ProjectCreationTabs';
import { LayerRenderer } from './LayerRenderer';
import { ScenePreviewer } from './ScenePreviewer';

export default function CreateProjectGeneration() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const projectId = id || '';

  const [projectTitle, setProjectTitle] = useState('');
  const [projectStatus, setProjectStatus] = useState('DRAFT');
  const [aspectRatio, setAspectRatio] = useState<string>('9:16');

  // Script generation states
  const [versionStrategy, setVersionStrategy] = useState<string>('balanced');
  const [isGeneratingScript, setIsGeneratingScript] = useState(false);
  const [scriptProgress, setScriptProgress] = useState(0);
  const [localScript, setLocalScript] = useState<any>(null);
  const [activeSceneIndex, setActiveSceneIndex] = useState(0);

  // Render states
  const [isRendering, setIsRendering] = useState(false);
  const [renderProgress, setRenderProgress] = useState(0);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const scriptTimerRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!projectId || projectId === 'new') {
      navigate('/create', { replace: true });
      return;
    }
    loadProject();
    checkInitialStatus();

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (scriptTimerRef.current) clearInterval(scriptTimerRef.current);
    };
  }, [projectId]);

  const loadProject = async () => {
    if (!projectId) return;
    try {
      const resp = await creationApi.getProject(projectId);
      setProjectTitle(resp.data?.title || '');
      const status = resp.data?.status || 'DRAFT';
      setProjectStatus(status);

      const savedAspectRatio = resp.data?.aspectRatio || resp.data?.renderAspectRatio;
      if (savedAspectRatio) {
        setAspectRatio(savedAspectRatio);
      }
      if ((status === 'SCRIPT_DONE' || status === 'DONE') && resp.data?.draftScriptJson) {
        setLocalScript(JSON.parse(resp.data.draftScriptJson));
      }
    } catch (error: any) {
      showToast(error?.message || '加载项目失败', 'error');
    }
  };

  const checkInitialStatus = async () => {
    try {
      const resp = await creationApi.getRenderStatus(projectId);
      if (resp.data) {
        handleStatusResponse(resp.data);
      }
    } catch (error) {
      console.warn("Failed to get initial status", error);
    }
  };

  const startGenerateScript = async () => {
    if (!projectId) return;
    setIsGeneratingScript(true);
    setScriptProgress(0);
    setErrorMsg(null);
    setVideoUrl(null);
    try {
      await creationApi.generateScript(projectId, { versionStrategy, aspectRatio });
      showToast('大模型已开始编排剧本，请稍候...', 'success');
      startPolling();
      startPseudoProgress();
    } catch (error: any) {
      showToast(error?.message || '请求生成剧本失败', 'error');
      setIsGeneratingScript(false);
    }
  };

  const startPseudoProgress = () => {
    if (scriptTimerRef.current) clearInterval(scriptTimerRef.current);
    let current = 0;
    scriptTimerRef.current = setInterval(() => {
      current += (0.95 - current) * 0.05; // 渐进式放缓到 95%
      setScriptProgress(current);
    }, 1000);
  };

  const startRender = async () => {
    if (!projectId || !localScript) return;
    setIsRendering(true);
    setRenderProgress(0);
    setErrorMsg(null);
    try {
      await creationApi.renderScript(projectId, { compositionScript: localScript, aspectRatio });
      showToast('已提交渲染任务！', 'success');
      startPolling();
    } catch (error: any) {
      showToast(error?.message || '提交渲染失败', 'error');
      setIsRendering(false);
    }
  };

  const startPolling = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = setInterval(async () => {
      try {
        const resp = await creationApi.getRenderStatus(projectId);
        if (resp.data) {
          handleStatusResponse(resp.data);
        }
      } catch (error) {
        console.error("Polling error", error);
      }
    }, 1500);
  };

  const handleStatusResponse = async (data: { status: string; progress: number; outputPath?: string; error?: string; renderId?: string }) => {
    if (data.status === 'GENERATING_SCRIPT') {
      setIsGeneratingScript(true);
      if (!scriptTimerRef.current) startPseudoProgress();
    } else if (data.status === 'SCRIPT_DONE') {
      if (scriptTimerRef.current) clearInterval(scriptTimerRef.current);
      setScriptProgress(1);
      setTimeout(() => setIsGeneratingScript(false), 500);
      setProjectStatus('SCRIPT_DONE');
      await loadProject(); // Reload to get draftScriptJson
      if (timerRef.current) clearInterval(timerRef.current);
    } else if (data.status === 'RENDERING') {
      setIsRendering(true);
      setIsGeneratingScript(false);
      setRenderProgress(data.progress || 0.1);
    } else if (data.status === 'DONE') {
      setIsRendering(false);
      setIsGeneratingScript(false);
      setRenderProgress(1);
      if (timerRef.current) clearInterval(timerRef.current);
      const rid = data.renderId || projectId;
      setVideoUrl(`http://localhost:3001/out/${rid}.mp4`);
      setProjectStatus('DONE');
      showToast('视频渲染完成！', 'success');
    } else if (data.status === 'FAILED') {
      setIsRendering(false);
      setIsGeneratingScript(false);
      setErrorMsg(data.error || '任务处理失败');
      if (timerRef.current) clearInterval(timerRef.current);
      if (scriptTimerRef.current) clearInterval(scriptTimerRef.current);
      setProjectStatus('FAILED');
      showToast('任务失败', 'error');
    } else if (data.status === 'QUEUED' || data.status === 'GENERATING') {
      setIsRendering(true);
      if (renderProgress === 0) setRenderProgress(0.05);
    }
  };

  const updateLocalScript = (key: string, value: string) => {
    if (!localScript) return;
    const newScript = { ...localScript };

    if (key === 'backgroundColor') {
      newScript.globalStyle = { ...newScript.globalStyle, backgroundColor: value };
    } else if (key === 'fontTier') {
      newScript.globalStyle = { ...newScript.globalStyle, fontTier: value };
    } else if (key === 'mixLevel') {
      newScript.bgm = { ...newScript.bgm, mixLevel: value };
    }

    setLocalScript(newScript);
  };

  const updateSceneDuration = (index: number, durationInFrames: number) => {
    if (!localScript || !localScript.scenes) return;
    const newScript = { ...localScript };
    newScript.scenes = [...newScript.scenes];
    newScript.scenes[index] = { ...newScript.scenes[index], durationInFrames };
    setLocalScript(newScript);
  };

  const updateLayerText = (sceneIndex: number, layerIndex: number, text: string) => {
    if (!localScript || !localScript.scenes) return;
    const newScript = { ...localScript };
    newScript.scenes = [...newScript.scenes];
    const newScene = { ...newScript.scenes[sceneIndex] };
    newScene.layers = [...newScene.layers];
    const newLayer = { ...newScene.layers[layerIndex] };
    newLayer.params = { ...newLayer.params, text };
    newScene.layers[layerIndex] = newLayer;
    newScript.scenes[sceneIndex] = newScene;
    setLocalScript(newScript);
  };

  const updateLayerParam = (sceneIndex: number, layerIndex: number, paramKey: string, value: string) => {
    if (!localScript || !localScript.scenes) return;
    const newScript = { ...localScript };
    newScript.scenes = [...newScript.scenes];
    const newScene = { ...newScript.scenes[sceneIndex] };
    newScene.layers = [...newScene.layers];
    const newLayer = { ...newScene.layers[layerIndex] };
    newLayer.params = { ...newLayer.params, [paramKey]: value };
    newScene.layers[layerIndex] = newLayer;
    newScript.scenes[sceneIndex] = newScene;
    setLocalScript(newScript);
  };

  const updateTransition = (fromIdx: number, toIdx: number, preset: string) => {
    if (!localScript) return;
    const newScript = { ...localScript };
    const transitions = [...(newScript.transitions || [])];
    const idx = transitions.findIndex((t: any) => t.fromSceneIndex === fromIdx && t.toSceneIndex === toIdx);
    if (preset === 'none') {
      // 移除转场
      if (idx >= 0) transitions.splice(idx, 1);
    } else if (idx >= 0) {
      transitions[idx] = { ...transitions[idx], preset, params: { ...transitions[idx].params, durationInFrames: transitions[idx].params?.durationInFrames || 15, timing: transitions[idx].params?.timing || 'linear' } };
      // slide 需要 direction，其他不需要
      if (preset !== 'transition.slide') { const p = { ...transitions[idx].params }; delete p.direction; transitions[idx].params = p; }
      else if (!transitions[idx].params?.direction) { transitions[idx] = { ...transitions[idx], params: { ...transitions[idx].params, direction: 'from-left' } }; }
    } else {
      const newT: any = { fromSceneIndex: fromIdx, toSceneIndex: toIdx, preset, params: { durationInFrames: 15, timing: 'linear' } };
      if (preset === 'transition.slide') newT.params.direction = 'from-left';
      transitions.push(newT);
    }
    newScript.transitions = transitions;
    setLocalScript(newScript);
  };

  const updateTransitionParam = (fromIdx: number, toIdx: number, key: string, value: any) => {
    if (!localScript) return;
    const newScript = { ...localScript };
    const transitions = [...(newScript.transitions || [])];
    const idx = transitions.findIndex((t: any) => t.fromSceneIndex === fromIdx && t.toSceneIndex === toIdx);
    if (idx < 0) return;
    transitions[idx] = { ...transitions[idx], params: { ...transitions[idx].params, [key]: value } };
    newScript.transitions = transitions;
    setLocalScript(newScript);
  };

  const fps = 30;
  const totalFrames = localScript?.scenes?.reduce((acc: number, scene: any) => acc + (scene.durationInFrames || 0), 0) || 0;
  const totalSeconds = (totalFrames / fps).toFixed(1);
  const safeSecondsLimit = 20.0; // Simulated safe BGM length limit
  const isOvertime = parseFloat(totalSeconds) > safeSecondsLimit;

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between', borderBottom: 'none', paddingBottom: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate(`/create/detail/${projectId}/gap-detection`)} title="返回适配阶段">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作工作流 · 智能生成与全局调优</h2>
        </div>
      </div>
      <ProjectCreationTabs projectId={projectId} activeTab="generation" />

      <div style={{ padding: '14px 32px 0', color: '#64748b', fontSize: '0.9rem' }}>
        项目：<strong style={{ color: '#0f172a' }}>{projectTitle || '未命名项目'}</strong> · 状态：{projectStatus}
      </div>

      <div className="detail-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: '20px' }}>

        {/* Step 1: Script Generation */}
        {projectStatus !== 'SCRIPT_DONE' && projectStatus !== 'DONE' && !isRendering && (
          <div className="result-card" style={{ width: '80%', maxWidth: '800px', padding: '40px', textAlign: 'center' }}>
            <h3 style={{ marginBottom: '20px', color: '#1e293b' }}>
              🎬 第 1 步：大模型结构编排
            </h3>
            <p style={{ color: '#64748b', marginBottom: '30px', fontSize: '0.95rem' }}>
              请选择多版本生成策略。大模型将根据策略自动规划场景时长、音乐混音强度与视觉节奏。
            </p>

            {!isGeneratingScript && (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
                <div style={{ textAlign: 'left', width: '100%', maxWidth: '400px' }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontWeight: 500, color: '#334155' }}>多版本策略</label>
                  <select
                    className="input-field"
                    value={versionStrategy}
                    onChange={e => setVersionStrategy(e.target.value)}
                    style={{ width: '100%', cursor: 'pointer' }}
                  >
                    <option value="balanced">【均衡原版】自然节奏，视听平衡</option>
                    <option value="fast_paced">【高频卡点版】快节奏，快速转场，强听觉冲击</option>
                    <option value="brand_quality">【品牌质感版】慢镜头，少字幕，舒缓质感淡入</option>
                  </select>
                </div>

                <button
                  className="btn-primary"
                  style={{ padding: '12px 24px', fontSize: '1.1rem', borderRadius: '8px', marginTop: '10px' }}
                  onClick={startGenerateScript}
                >
                  开始生成剧本
                </button>
              </div>
            )}

            {isGeneratingScript && (
              <div style={{ marginTop: '20px', width: '100%' }}>
                <div style={{ fontSize: '0.9rem', color: '#3b82f6', marginBottom: '10px', fontWeight: 500 }}>
                  大模型正在深入思考剧本节奏... 由于提示词极长，通常需要 2-3 分钟，请耐心等待
                  {Math.floor(scriptProgress * 100)}%
                </div>
                <div style={{ width: '100%', height: '12px', background: '#e2e8f0', borderRadius: '6px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      background: '#3b82f6',
                      width: `${Math.max(5, scriptProgress * 100)}%`,
                      transition: 'width 1s linear'
                    }}
                  />
                </div>
              </div>
            )}

            {errorMsg && !isGeneratingScript && (
              <div style={{ marginTop: '20px', padding: '16px', background: '#fef2f2', color: '#ef4444', borderRadius: '8px', border: '1px solid #fca5a5' }}>
                <strong>生成失败: </strong> {errorMsg}
                <button className="btn-outline" style={{ marginTop: '10px', display: 'block', margin: '10px auto 0' }} onClick={startGenerateScript}>重试生成</button>
              </div>
            )}
          </div>
        )}

        {/* Step 2: Global Adjustment & Rendering */}
        {(projectStatus === 'SCRIPT_DONE' || isRendering || projectStatus === 'DONE') && (
          <div style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '24px' }}>
            <div style={{ width: '100%', maxWidth: '1200px', display: 'flex', gap: '24px', alignItems: 'stretch', justifyContent: 'center', flexWrap: 'wrap' }}>

              {/* Left Column: Global Configs & Health */}
              {!isRendering && localScript && (
                <div style={{ flex: '0 0 300px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  {/* Timeline Health Bar */}
                  <div className="result-card" style={{ padding: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ fontWeight: 600, color: '#1e293b' }}>⏱️ 时轴健康度</span>
                      <span style={{ color: isOvertime ? '#ef4444' : '#059669', fontWeight: 500 }}>
                        总时长: {totalSeconds}秒 / 推荐: {safeSecondsLimit}秒
                      </span>
                    </div>
                    <div style={{ width: '100%', height: '8px', background: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                      <div style={{
                        height: '100%',
                        background: isOvertime ? '#ef4444' : '#10b981',
                        width: `${Math.min(100, (parseFloat(totalSeconds) / safeSecondsLimit) * 100)}%`,
                        transition: 'all 0.3s'
                      }} />
                    </div>
                    {isOvertime && (
                      <div style={{ fontSize: '0.85rem', color: '#ef4444', marginTop: '8px' }}>
                        ⚠️ 警告：总时长超出推荐安全长度，可能导致背景音乐突然切断。
                      </div>
                    )}
                  </div>

                  {/* Global Setup */}
                  <div className="result-card" style={{ padding: '20px' }}>
                    <h3 style={{ marginBottom: '16px', color: '#1e293b', fontSize: '1rem' }}>🎛️ 全局策略</h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                      <div>
                        <label style={{ display: 'block', marginBottom: '6px', fontWeight: 500, color: '#334155', fontSize: '0.85rem' }}>🎨 视觉基调色彩</label>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                          <input
                            type="color"
                            value={localScript.globalStyle?.backgroundColor || '#050F1E'}
                            onChange={e => updateLocalScript('backgroundColor', e.target.value)}
                            style={{ width: '36px', height: '36px', padding: 0, border: 'none', borderRadius: '6px', cursor: 'pointer' }}
                          />
                          <span style={{ color: '#64748b', fontSize: '0.85rem' }}>{localScript.globalStyle?.backgroundColor || '#050F1E'}</span>
                        </div>
                      </div>
                      <div>
                        <label style={{ display: 'block', marginBottom: '6px', fontWeight: 500, color: '#334155', fontSize: '0.85rem' }}>🎵 听觉混音策略</label>
                        <select
                          className="input-field"
                          value={localScript.bgm?.mixLevel || 'BALANCED'}
                          onChange={e => updateLocalScript('mixLevel', e.target.value)}
                          style={{ width: '100%', cursor: 'pointer', fontSize: '0.85rem', padding: '6px 10px' }}
                        >
                          <option value="QUIET">舒缓 (Quiet)</option>
                          <option value="BALANCED">均衡 (Balanced)</option>
                          <option value="DRIVE">强烈 (Drive)</option>
                        </select>
                      </div>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="result-card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    <button
                      className="btn-primary"
                      style={{ padding: '12px', fontSize: '1rem', borderRadius: '8px' }}
                      onClick={startRender}
                      disabled={isOvertime}
                    >
                      {isOvertime ? '时长超限' : (projectStatus === 'DONE' ? '重新渲染当前修改' : '确认编排，生成视频')}
                    </button>
                    {projectStatus === 'DONE' && (
                      <button className="btn-outline" style={{ padding: '10px' }} onClick={() => setProjectStatus('DRAFT')}>退回重新编排</button>
                    )}
                  </div>
                </div>
              )}

              {/* Right/Center Area: Monitors */}
              {(!isRendering || localScript) && (
                <div style={{ flex: 1, minWidth: '400px', display: 'flex', gap: '20px', alignItems: 'stretch' }}>

                  {/* CSS Previewer Monitor */}
                  {localScript && (
                    <div className="result-card" style={{ flex: 1, padding: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '400px', background: '#f8fafc' }}>
                      <h4 style={{ marginBottom: '16px', color: '#334155', fontSize: '0.9rem' }}>👁️ 实时效果预览</h4>
                      <ScenePreviewer script={localScript} sceneIndex={activeSceneIndex} aspectRatio={aspectRatio} />
                    </div>
                  )}

                  {/* Rendered Video Monitor */}
                  {(isRendering || (projectStatus === 'DONE' && videoUrl)) && (
                    <div className="result-card" style={{ flex: 1, padding: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '400px', background: '#f8fafc' }}>
                      {isRendering ? (
                        <div style={{ width: '100%', textAlign: 'center' }}>
                          <div className="spinner" style={{ width: '40px', height: '40px', margin: '0 auto 20px', border: '4px solid #f3f3f3', borderTop: '4px solid #3b82f6', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
                          <div style={{ fontSize: '1.1rem', color: '#3b82f6', marginBottom: '10px', fontWeight: 500 }}>
                            引擎极速渲染中... {Math.floor(renderProgress * 100)}%
                          </div>
                          <div style={{ width: '80%', margin: '0 auto', height: '12px', background: '#e2e8f0', borderRadius: '6px', overflow: 'hidden' }}>
                            <div style={{ height: '100%', background: '#3b82f6', width: `${Math.max(5, renderProgress * 100)}%`, transition: 'width 0.5s ease-out' }} />
                          </div>
                        </div>
                      ) : (
                        <>
                          <h4 style={{ marginBottom: '16px', color: '#059669', fontSize: '0.9rem' }}>🎬 渲染出片</h4>
                          <div style={{
                            border: '4px solid #1e293b',
                            borderRadius: '12px',
                            overflow: 'hidden',
                            display: 'inline-block',
                            background: '#000',
                            boxShadow: '0 10px 25px rgba(0,0,0,0.2)'
                          }}>
                            <video src={videoUrl} controls autoPlay style={{ width: '100%', maxWidth: '360px', maxHeight: '640px', display: 'block' }} />
                          </div>
                        </>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Bottom Timeline: Horizontal Storyboard */}
            {!isRendering && localScript && (
              <div className="result-card" style={{ width: '100%', maxWidth: '1200px', padding: '24px' }}>
                <h4 style={{ marginBottom: '20px', color: '#1e293b', borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
                  🎞️ 分镜时间线 (Timeline)
                </h4>

                {/* Horizontal Scroll Container */}
                <div style={{ display: 'flex', overflowX: 'auto', gap: '12px', paddingBottom: '16px', alignItems: 'stretch' }}>
                  {localScript.scenes?.map((scene: any, sceneIdx: number) => {
                    const visualLayers = scene.layers?.filter((l: any) => l.preset?.startsWith('text.') || l.preset?.startsWith('media.') || l.preset?.startsWith('bg.') || l.preset?.startsWith('backing.') || l.preset?.startsWith('overlay.') || l.preset?.startsWith('caption.') || l.preset?.startsWith('motion.')) || [];
                    const audioLayers = scene.layers?.filter((l: any) => l.preset === 'media.audio') || [];

                    return (
                      <div key={sceneIdx} style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>

                        {/* Scene Card */}
                        <div
                          onClick={() => setActiveSceneIndex(sceneIdx)}
                          style={{
                            minWidth: '340px',
                            maxWidth: '340px',
                            border: `2px solid ${activeSceneIndex === sceneIdx ? '#3b82f6' : '#e2e8f0'}`,
                            borderRadius: '8px',
                            padding: '16px',
                            cursor: 'pointer',
                            background: activeSceneIndex === sceneIdx ? '#f0f9ff' : '#fff',
                            transition: 'all 0.2s',
                            boxShadow: activeSceneIndex === sceneIdx ? '0 4px 12px rgba(59,130,246,0.1)' : 'none',
                            display: 'flex',
                            flexDirection: 'column',
                            alignSelf: 'stretch' // make all cards same height
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', alignItems: 'center', borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
                            <span style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>分镜 {sceneIdx + 1}: {scene.role || '场景'}</span>
                            <span style={{ fontSize: '0.85rem', color: '#64748b', background: '#e2e8f0', padding: '4px 10px', borderRadius: '12px', fontWeight: 500 }}>
                              {(scene.durationInFrames / fps).toFixed(1)} s ({scene.durationInFrames}帧)
                            </span>
                          </div>

                          {/* Duration Slider */}
                          <div style={{ marginBottom: '20px' }}>
                            <input
                              type="range"
                              min="30"
                              max="300"
                              step="10"
                              value={scene.durationInFrames || 90}
                              onChange={(e) => updateSceneDuration(sceneIdx, parseInt(e.target.value))}
                              style={{ width: '100%', cursor: 'grab' }}
                            />
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: '#94a3b8' }}>
                              <span>1s</span>
                              <span>拖拽调整该分镜停留时长</span>
                              <span>10s</span>
                            </div>
                          </div>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
                            {/* 🎬 Visual Track */}
                            {visualLayers.length > 0 && (
                              <div>
                                <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  🎬 视觉轨道
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', paddingLeft: '8px', borderLeft: '2px solid #cbd5e1' }}>
                                  {visualLayers.map((layer: any, layerIdx: number) => (
                                    <LayerRenderer
                                      key={`visual-${layerIdx}`}
                                      layer={layer}
                                      sceneIdx={sceneIdx}
                                      layerIdx={scene.layers.indexOf(layer)}
                                      updateLayerParam={updateLayerParam}
                                      updateLayerText={updateLayerText}
                                    />
                                  ))}
                                </div>
                              </div>
                            )}

                            {/* 🔈 Audio Track */}
                            {audioLayers.length > 0 && (
                              <div>
                                <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  🔈 声音轨道
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', paddingLeft: '8px', borderLeft: '2px solid #cbd5e1' }}>
                                  {audioLayers.map((layer: any, layerIdx: number) => {
                                    return (
                                      <div key={`audio-${layerIdx}`} style={{ background: '#f8fafc', padding: '8px 10px', borderRadius: '6px', fontSize: '0.8rem', color: '#10b981', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                                        <span>🔊 角色: {layer.params?.audioRole || '未定义'}</span>
                                        <span style={{ background: '#d1fae5', padding: '2px 8px', borderRadius: '4px', fontWeight: 500 }}>
                                          {layer.params?.cueType || '默认音效'}
                                        </span>
                                        {layer.params?.syncMode && <span style={{ color: '#64748b' }}>同步: {layer.params.syncMode}</span>}
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Transition Node */}
                        {sceneIdx < localScript.scenes.length - 1 && (
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            {(() => {
                              const transition = localScript.transitions?.find((t: any) => t.fromSceneIndex === sceneIdx && t.toSceneIndex === sceneIdx + 1);
                              const currentPreset = transition?.preset || 'none';
                              return (
                                <div style={{ background: '#f1f5f9', border: '1px solid #cbd5e1', color: '#475569', fontSize: '0.75rem', padding: '8px', borderRadius: '12px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', boxShadow: '0 2px 4px rgba(0,0,0,0.05)', minWidth: '80px' }}>
                                  <span style={{ fontSize: '1rem' }}>🔗</span>
                                  <select value={currentPreset} onChange={e => updateTransition(sceneIdx, sceneIdx + 1, e.target.value)} style={{ fontSize: '0.7rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', maxWidth: '80px' }}>
                                    <option value="none">无(硬切)</option>
                                    <option value="transition.fade">淡入淡出</option>
                                    <option value="transition.slide">滑动推入</option>
                                    <option value="transition.wipe">擦除</option>
                                  </select>
                                  {transition && (
                                    <>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
                                        <input type="number" value={transition.params?.durationInFrames || 15} min={5} max={60} onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'durationInFrames', parseInt(e.target.value))} style={{ width: '40px', fontSize: '0.7rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', textAlign: 'center' }} />
                                        <span style={{ color: '#94a3b8', fontSize: '0.65rem' }}>帧</span>
                                      </div>
                                      <select value={transition.params?.timing || 'linear'} onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'timing', e.target.value)} style={{ fontSize: '0.7rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff', maxWidth: '80px' }}>
                                        <option value="linear">线性</option>
                                        <option value="spring">弹簧</option>
                                      </select>
                                      {currentPreset === 'transition.slide' && (
                                        <select value={transition.params?.direction || 'from-left'} onChange={e => updateTransitionParam(sceneIdx, sceneIdx + 1, 'direction', e.target.value)} style={{ fontSize: '0.7rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff', maxWidth: '80px' }}>
                                          <option value="from-left">← 左</option>
                                          <option value="from-right">→ 右</option>
                                          <option value="from-top">↑ 上</option>
                                          <option value="from-bottom">↓ 下</option>
                                        </select>
                                      )}
                                    </>
                                  )}
                                </div>
                              );
                            })()}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
            {/* Bottom: JSON Code Viewer */}
            {!isRendering && localScript && (
              <details style={{ width: '100%', maxWidth: '1000px', cursor: 'pointer' }}>
                <summary style={{ padding: '16px', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', color: '#475569', fontWeight: 500, userSelect: 'none' }}>
                  👨‍💻 开发者模式：底层编排 JSON (点击展开)
                </summary>
                <div className="result-card" style={{ marginTop: '12px', padding: '24px', textAlign: 'left', borderTop: 'none' }}>
                  <div style={{ background: '#0f172a', borderRadius: '8px', padding: '16px', overflowX: 'auto' }}>
                    <pre style={{
                      color: '#e2e8f0',
                      fontSize: '13px',
                      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                      margin: 0,
                      maxHeight: '400px',
                      overflowY: 'auto'
                    }}>
                      {JSON.stringify(localScript, null, 2)}
                    </pre>
                  </div>
                </div>
              </details>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
