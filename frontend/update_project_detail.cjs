const fs = require('fs');
const path = require('path');

const filePath = path.join('d:', 'Develop', 'code', 'bytedance-ai-video', 'frontend', 'src', 'pages', 'deconstruct', 'ProjectDetail.tsx');
let content = fs.readFileSync(filePath, 'utf-8');

// 1. Remove PROCESSING_STEPS
content = content.replace(/const PROCESSING_STEPS = \[[\s\S]*?\];\n/, '');

// 2. Remove extractProgress and extractStageIndex state
content = content.replace(/const \[extractProgress, setExtractProgress\] = useState\(0\);\n/, '');
content = content.replace(/const \[extractStageIndex, setExtractStageIndex\] = useState\(0\);\n/, '');

// 3. Remove Async extract simulation useEffect
content = content.replace(/\/\/ Async extract simulation[\s\S]*?\/\/ Polling logic for real data/, '// Polling logic for real data');

// 4. Update Polling logic to navigate to visualize
content = content.replace(
  /if \(!isDebugMode && resp\.data\.status === 'COMPLETED'\) \{\s*setDeconstructStep\('result'\);\s*\}/,
  `if (!isDebugMode && resp.data.status === 'COMPLETED') {\n                navigate('/deconstruct/detail/' + id + '/visualize');\n              }`
);

// 5. Add calculateProgress helper
const calculateProgressStr = `
  const getStageInfo = (type: string) => {
    return liveTaskResult?.stages?.find(s => s.stageType === type);
  };

  const calculateProgress = () => {
    if (!liveTaskResult || !liveTaskResult.stages) return 0;
    const asr = getStageInfo('ASR');
    const scene = getStageInfo('SCENE');
    const keyframe = getStageInfo('KEYFRAME');
    const timeline = getStageInfo('TIMELINE');
    const llm = getStageInfo('LLM');

    let total = 0;
    // Base extraction: 40%
    if (asr?.stageStatus === 'COMPLETED') total += 15;
    else if (asr?.stageStatus === 'RUNNING') total += (asr.stageProgress || 0) * 0.15;

    if (scene?.stageStatus === 'COMPLETED') total += 15;
    else if (scene?.stageStatus === 'RUNNING') total += (scene.stageProgress || 0) * 0.15;

    if (keyframe?.stageStatus === 'COMPLETED') total += 10;
    else if (keyframe?.stageStatus === 'RUNNING') total += (keyframe.stageProgress || 0) * 0.10;

    // Timeline: 30%
    if (timeline?.stageStatus === 'COMPLETED') total += 30;
    else if (timeline?.stageStatus === 'RUNNING') total += (timeline.stageProgress || 0) * 0.30;

    // LLM: 30%
    if (llm?.stageStatus === 'COMPLETED') total += 30;
    else if (llm?.stageStatus === 'RUNNING') total += (llm.stageProgress || 0) * 0.30;

    return Math.min(100, Math.floor(total));
  };
  const currentProgress = calculateProgress();
`;
content = content.replace(/const toggleTag = \(tag: string\) => \{/, `${calculateProgressStr}\n  const toggleTag = (tag: string) => {`);

// 6. Update handleStartExtraction
content = content.replace(
  /const handleStartExtraction = \(\) => \{[\s\S]*?setLiveTaskResult\(null\);\n  \};/,
  `const handleStartExtraction = () => {
    setDeconstructStep('processing');
    setDebugAsrTriggeredTaskIds([]);
    setLiveTaskResult(null);
  };`
);

// 7. Update runNextDebugStep
content = content.replace(
  /const runNextDebugStep = async \(\) => \{[\s\S]*?setDeconstructStep\('result'\);\n  \};/,
  `const runNextDebugStep = async () => {
    const unTriggeredTaskIds = uploadedFiles
      .map((file) => file.taskId)
      .filter((taskId) => !!taskId && !debugAsrTriggeredTaskIds.includes(taskId));

    if (unTriggeredTaskIds.length > 0) {
      setTriggeringDebugAsr(true);
      try {
        const triggerResponses = await Promise.all(unTriggeredTaskIds.map((taskId) => videoApi.triggerAsrDebug(taskId)));
        const failedResponse = triggerResponses.find((resp) => resp.code !== '0' && resp.code !== '200');
        if (failedResponse) {
          throw new Error(failedResponse.message || '触发 ASR Debug 分析失败');
        }
        setDebugAsrTriggeredTaskIds((prev) => [...prev, ...unTriggeredTaskIds]);
        showToast('已触发 Debug 分析', 'success');
      } catch (error) {
        const message = error instanceof Error ? error.message : '触发 Debug 分析失败';
        showToast(message, 'error');
        return;
      } finally {
        setTriggeringDebugAsr(false);
      }
    } else {
      setDeconstructStep('result');
    }
  };`
);

// 8. Replace processing-dashboard UI with a beautiful design
const beautifulProcessingUI = `
                      <div className="extract-view processing-view fade-in" style={{ flex: 1, minHeight: '500px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                        <div className="glass-progress-container" style={{
                          background: 'rgba(255, 255, 255, 0.6)',
                          backdropFilter: 'blur(12px)',
                          borderRadius: '24px',
                          padding: '40px 60px',
                          boxShadow: '0 8px 32px rgba(31, 38, 135, 0.07)',
                          border: '1px solid rgba(255, 255, 255, 0.4)',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: '32px',
                          width: '100%',
                          maxWidth: '700px'
                        }}>
                          <div style={{ textAlign: 'center' }}>
                            <h3 style={{ margin: '0 0 8px 0', fontSize: '1.5rem', color: '#1e293b' }}>AI 深度拆解中...</h3>
                            <p style={{ margin: 0, color: '#64748b' }}>正在进行多模态时空对齐与大模型分析</p>
                          </div>
                          
                          <div className="progress-circle-modern" style={{ position: 'relative', width: '180px', height: '180px' }}>
                            <svg viewBox="0 0 36 36" style={{ width: '100%', height: '100%', transform: 'rotate(-90deg)' }}>
                              <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="rgba(99, 102, 241, 0.1)" strokeWidth="3" />
                              <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="url(#gradient)" strokeWidth="3" strokeDasharray={currentProgress + ', 100'} style={{ transition: 'stroke-dasharray 0.5s ease' }} strokeLinecap="round" />
                              <defs>
                                <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                                  <stop offset="0%" stopColor="#38bdf8" />
                                  <stop offset="100%" stopColor="#6366f1" />
                                </linearGradient>
                              </defs>
                            </svg>
                            <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                              <span style={{ fontSize: '2.5rem', fontWeight: 700, color: '#1e293b', background: 'linear-gradient(90deg, #38bdf8, #6366f1)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>{currentProgress}</span>
                              <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>%</span>
                            </div>
                          </div>

                          <div className="stage-dependency-map" style={{ display: 'flex', width: '100%', justifyContent: 'space-between', position: 'relative', padding: '0 20px' }}>
                            {/* Line */}
                            <div style={{ position: 'absolute', top: '16px', left: '40px', right: '40px', height: '2px', background: '#e2e8f0', zIndex: 0 }}></div>
                            
                            {/* Stages */}
                            {[
                              { key: 'extraction', label: '基础提取', sub: 'ASR / SCENE', active: currentProgress >= 0, done: getStageInfo('ASR')?.stageStatus === 'COMPLETED' && getStageInfo('SCENE')?.stageStatus === 'COMPLETED' },
                              { key: 'TIMELINE', label: '模态对齐', sub: 'Timeline Match', active: getStageInfo('TIMELINE')?.stageStatus === 'RUNNING' || getStageInfo('TIMELINE')?.stageStatus === 'COMPLETED', done: getStageInfo('TIMELINE')?.stageStatus === 'COMPLETED' },
                              { key: 'LLM', label: '深度推断', sub: 'Structure Analysis', active: getStageInfo('LLM')?.stageStatus === 'RUNNING' || getStageInfo('LLM')?.stageStatus === 'COMPLETED', done: getStageInfo('LLM')?.stageStatus === 'COMPLETED' }
                            ].map((stage, i) => (
                              <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 1, gap: '8px' }}>
                                <div style={{ 
                                  width: '34px', height: '34px', borderRadius: '50%', 
                                  background: stage.done ? '#6366f1' : stage.active ? '#fff' : '#f8fafc',
                                  border: stage.done ? 'none' : stage.active ? '2px solid #6366f1' : '2px solid #e2e8f0',
                                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                                  boxShadow: stage.active && !stage.done ? '0 0 0 4px rgba(99, 102, 241, 0.1)' : 'none',
                                  color: stage.done ? '#fff' : '#cbd5e1'
                                }}>
                                  {stage.done ? (
                                    <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="3" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                  ) : (
                                    <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: stage.active ? '#6366f1' : 'transparent' }}></div>
                                  )}
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: stage.active ? '#1e293b' : '#94a3b8' }}>{stage.label}</div>
                                  <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>{stage.sub}</div>
                                </div>
                              </div>
                            ))}
                          </div>
                          
                          {isDebugMode && (
                            <div className="debug-actions" style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
                              <button
                                className="btn-outline"
                                onClick={handleNextDebugStep}
                                disabled={triggeringDebugAsr}
                              >
                                {triggeringDebugAsr ? '触发中...' : '手动触发下一步 (Debug)'}
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
`;
content = content.replace(
  /<div className="processing-dashboard">[\s\S]*?<\/div>\s*<\/div>\s*\)\}/,
  beautifulProcessingUI + '\n                  )}'
);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Update success');
