import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { TemplateSummaryData, TemplateRecommendItemData, BgmRecommendItemData, ProjectBgmBindingData } from '../../types';
import { ProjectCreationTabs } from '../../components/ProjectCreationTabs';

type TemplateOption = {
  templateId: string;
  templateVersion: number;
  templateName: string;
};

export default function ProjectRecommendation() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const projectId = id || '';

  // -------------------------
  // Template States
  // -------------------------
  const [templateOptions, setTemplateOptions] = useState<TemplateOption[]>([]);
  const [projectTemplateId, setProjectTemplateId] = useState('');
  const [bindingTemplate, setBindingTemplate] = useState(false);

  // ── localStorage keys ──
  const LS_TPL_W1 = 'aivideo:tpl_w1';
  const LS_TPL_W2 = 'aivideo:tpl_w2';
  const LS_TPL_TOPN = 'aivideo:tpl_topn';
  const LS_BGM_W1 = 'aivideo:bgm_w1';
  const LS_BGM_W2 = 'aivideo:bgm_w2';
  const LS_BGM_W3 = 'aivideo:bgm_w3';
  const LS_BGM_TOPN = 'aivideo:bgm_topn';

  const readNum = (key: string, fallback: number): number => {
    try {
      const raw = localStorage.getItem(key);
      if (raw !== null) {
        const v = parseFloat(raw);
        if (!isNaN(v)) return v;
      }
    } catch { /* localStorage blocked */ }
    return fallback;
  };

  const [tplW1, setTplW1] = useState(() => readNum(LS_TPL_W1, 0.6));
  const [tplW2, setTplW2] = useState(() => readNum(LS_TPL_W2, 0.4));
  const [tplTopN, setTplTopN] = useState(() => Math.round(readNum(LS_TPL_TOPN, 10)));
  const [tplTopNText, setTplTopNText] = useState(() => String(Math.round(readNum(LS_TPL_TOPN, 10))));
  const [tplRecommendations, setTplRecommendations] = useState<TemplateRecommendItemData[]>([]);
  const [tplRecommendLoading, setTplRecommendLoading] = useState(false);
  const [tplRecommendError, setTplRecommendError] = useState('');

  // persist template params
  useEffect(() => { try { localStorage.setItem(LS_TPL_W1, String(tplW1)); } catch {} }, [tplW1]);
  useEffect(() => { try { localStorage.setItem(LS_TPL_W2, String(tplW2)); } catch {} }, [tplW2]);
  useEffect(() => { try { localStorage.setItem(LS_TPL_TOPN, String(tplTopN)); } catch {} }, [tplTopN]);

  // -------------------------
  // BGM States
  // -------------------------
  const [bgmW1, setBgmW1] = useState(() => readNum(LS_BGM_W1, 0.35));
  const [bgmW2, setBgmW2] = useState(() => readNum(LS_BGM_W2, 0.50));
  const [bgmW3, setBgmW3] = useState(() => readNum(LS_BGM_W3, 0.15));
  const [bgmTopN, setBgmTopN] = useState(() => Math.round(readNum(LS_BGM_TOPN, 5)));
  const [bgmTopNText, setBgmTopNText] = useState(() => String(Math.round(readNum(LS_BGM_TOPN, 5))));
  const [bgmResults, setBgmResults] = useState<BgmRecommendItemData[]>([]);
  const [bgmLoading, setBgmLoading] = useState(false);
  const [bgmError, setBgmError] = useState('');
  const [currentBgm, setCurrentBgm] = useState<ProjectBgmBindingData | null>(null);
  const [bindingAudioId, setBindingAudioId] = useState<string | null>(null);
  const [clearingBgm, setClearingBgm] = useState(false);

  // persist BGM params
  useEffect(() => { try { localStorage.setItem(LS_BGM_W1, String(bgmW1)); } catch {} }, [bgmW1]);
  useEffect(() => { try { localStorage.setItem(LS_BGM_W2, String(bgmW2)); } catch {} }, [bgmW2]);
  useEffect(() => { try { localStorage.setItem(LS_BGM_W3, String(bgmW3)); } catch {} }, [bgmW3]);
  useEffect(() => { try { localStorage.setItem(LS_BGM_TOPN, String(bgmTopN)); } catch {} }, [bgmTopN]);

  // Audio Playback
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (projectId) {
      loadProjectInfo();
      loadTemplateOptions();
      fetchTplRecommendations();
      fetchBgmRecommendations();
      loadCurrentBgm(true);
    }
    return () => {
      if (audioElement) {
        audioElement.pause();
      }
    };
  }, [projectId]);

  // removed unused useEffect

  // -------------------------
  // Project & Template API
  // -------------------------
  const loadProjectInfo = async () => {
    try {
      const resp = await creationApi.getProject(projectId);
      setProjectTemplateId(resp.data?.templateId || '');
    } catch (error: any) {
      showToast(error?.message || '加载项目信息失败', 'error');
    }
  };

  const loadTemplateOptions = async () => {
    try {
      const resp = await creationApi.listTemplates();
      const latest = pickLatestTemplates(resp.data || []);
      setTemplateOptions(latest);
    } catch (error: any) {
      showToast(error?.message || '加载模板库失败', 'error');
    }
  };

  const fetchTplRecommendations = async (forceRefresh: boolean = false) => {
    setTplRecommendLoading(true);
    setTplRecommendError('');
    try {
      const res = await creationApi.recommendTemplates(projectId, tplW1, tplW2, tplTopN, forceRefresh);
      setTplRecommendations(res.data?.recommendations ?? []);
    } catch (err: any) {
      setTplRecommendError(err?.message || '模板推荐获取失败');
    } finally {
      setTplRecommendLoading(false);
    }
  };

  const doBindTemplate = async (templateId: string, templateVersion: number) => {
    setBindingTemplate(true);
    try {
      await creationApi.bindTemplate(projectId, templateId, templateVersion);
      setProjectTemplateId(templateId);
      showToast('模板绑定成功', 'success');
      await loadProjectInfo();
      fetchTplRecommendations();
    } catch (error: any) {
      showToast(error?.message || '模板绑定失败', 'error');
    } finally {
      setBindingTemplate(false);
    }
  };

  // -------------------------
  // BGM API
  // -------------------------
  const handleBgmWeightChange = (type: 'w1' | 'w2' | 'w3', value: number) => {
    const newVal = Math.max(0, Math.min(1, value));
    if (type === 'w1') {
      setBgmW1(newVal);
      const remaining = 1 - newVal;
      const sumOthers = bgmW2 + bgmW3;
      if (sumOthers > 0) {
        setBgmW2(parseFloat(((bgmW2 / sumOthers) * remaining).toFixed(2)));
        setBgmW3(parseFloat(((bgmW3 / sumOthers) * remaining).toFixed(2)));
      } else {
        setBgmW2(parseFloat((remaining / 2).toFixed(2)));
        setBgmW3(parseFloat((remaining / 2).toFixed(2)));
      }
    } else if (type === 'w2') {
      setBgmW2(newVal);
      const remaining = 1 - newVal;
      const sumOthers = bgmW1 + bgmW3;
      if (sumOthers > 0) {
        setBgmW1(parseFloat(((bgmW1 / sumOthers) * remaining).toFixed(2)));
        setBgmW3(parseFloat(((bgmW3 / sumOthers) * remaining).toFixed(2)));
      } else {
        setBgmW1(parseFloat((remaining / 2).toFixed(2)));
        setBgmW3(parseFloat((remaining / 2).toFixed(2)));
      }
    } else if (type === 'w3') {
      setBgmW3(newVal);
      const remaining = 1 - newVal;
      const sumOthers = bgmW1 + bgmW2;
      if (sumOthers > 0) {
        setBgmW1(parseFloat(((bgmW1 / sumOthers) * remaining).toFixed(2)));
        setBgmW2(parseFloat(((bgmW2 / sumOthers) * remaining).toFixed(2)));
      } else {
        setBgmW1(parseFloat((remaining / 2).toFixed(2)));
        setBgmW2(parseFloat((remaining / 2).toFixed(2)));
      }
    }
  };

  const fetchBgmRecommendations = async (forceRefresh: boolean = false) => {
    setBgmLoading(true);
    setBgmError('');
    if (audioElement) {
      audioElement.pause();
      setPlayingId(null);
    }
    try {
      const res = await creationApi.recommendBgm(projectId, bgmW1, bgmW2, bgmW3, bgmTopN, forceRefresh);
      setBgmResults(res.data.recommendations || []);
    } catch (err: any) {
      setBgmError(err.message || '获取 BGM 推荐失败');
    } finally {
      setBgmLoading(false);
    }
  };

  const loadCurrentBgm = async (silent: boolean = false) => {
    try {
      const res = await creationApi.getSelectedBgm(projectId);
      setCurrentBgm(res.data ?? null);
    } catch (err: any) {
      if (!silent) showToast(err?.message || '查询当前 BGM 失败', 'error');
    }
  };

  const handleSelectBgm = async (item: BgmRecommendItemData) => {
    setBindingAudioId(item.audioId);
    try {
      const res = await creationApi.selectBgm(projectId, {
        audioId: item.audioId,
        audioName: item.audioName,
        filePath: item.filePath,
        sourceType: 'RECOMMENDED',
        recommendScore: item.finalScore,
        semanticScore: item.semanticScore,
        energyCurveScore: item.energyCurveScore,
        durationBpmScore: item.durationBpmScore,
        mixLevel: 'BALANCED',
        loopEnabled: true,
        fadeInFrames: 15,
        fadeOutFrames: 30,
        duckingEnabled: true,
        duckingRatio: 0.2,
        metadata: {
          bpm: item.bpm,
          overallStyle: item.overallStyle,
          durationSeconds: item.durationSeconds
        }
      });
      setCurrentBgm(res.data);
      showToast(`已设置 BGM: ${item.audioName || item.audioId}`, 'success');
    } catch (err: any) {
      showToast(err?.message || '选择当前 BGM 失败', 'error');
    } finally {
      setBindingAudioId(null);
    }
  };

  const handleClearCurrentBgm = async () => {
    setClearingBgm(true);
    try {
      await creationApi.clearSelectedBgm(projectId);
      setCurrentBgm(null);
      showToast('已清除当前项目 BGM', 'success');
    } catch (err: any) {
      showToast(err?.message || '清除当前 BGM 失败', 'error');
    } finally {
      setClearingBgm(false);
    }
  };

  const togglePlay = (item: BgmRecommendItemData) => {
    if (playingId === item.audioId && audioElement) {
      audioElement.pause();
      setPlayingId(null);
    } else {
      if (audioElement) {
        audioElement.pause();
      }
      const audio = new Audio(item.filePath);
      audio.play().catch(err => {
        console.error("播放音频失败:", err);
        showToast("无法播放音频: " + item.filePath, 'error');
      });
      audio.onended = () => setPlayingId(null);
      setAudioElement(audio);
      setPlayingId(item.audioId);
    }
  };

  const boundTemplateView = useMemo(() => {
    if (!projectTemplateId) return '未绑定模板';
    const matched = templateOptions.find((item) => item.templateId === projectTemplateId);
    if (!matched) return '已绑定模板（详情加载中）';
    return `${matched.templateName} · v${matched.templateVersion}`;
  }, [projectTemplateId, templateOptions]);

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      {/* Header */}
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between', borderBottom: 'none', paddingBottom: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate(`/create/detail/${projectId}/workflow`)} title="返回素材阶段">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作工作流 · 智能推荐匹配</h2>
        </div>
      </div>
      <ProjectCreationTabs projectId={projectId} activeTab="recommendation" />

      <div className="detail-body" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        
        {/* =====================
            LEFT: TEMPLATE
        ===================== */}
        <div className="result-card">
          <h4>模板智能推荐与绑定</h4>
          
          <div style={{ marginBottom: '16px', padding: '12px', background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>匹配参数调优</span>
              <span style={{ fontSize: '0.7rem', color: '#94a3b8' }}>W1+W2 = {(tplW1 + tplW2).toFixed(2)}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '10px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>语义(W1): {tplW1.toFixed(2)}</label>
                <input type="range" min="0" max="1" step="0.05" value={tplW1}
                  onChange={(e) => { const v = parseFloat(e.target.value); setTplW1(v); setTplW2(Math.round((1 - v) * 100) / 100); }}
                  style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>结构(W2): {tplW2.toFixed(2)}</label>
                <input type="range" min="0" max="1" step="0.05" value={tplW2}
                  onChange={(e) => { const v = parseFloat(e.target.value); setTplW2(v); setTplW1(Math.round((1 - v) * 100) / 100); }}
                  style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>Top N</label>
                <input type="number" min="1" max="50" value={tplTopNText}
                  onChange={(e) => setTplTopNText(e.target.value)}
                  onBlur={() => { const v = parseInt(tplTopNText, 10); if (!isNaN(v) && v >= 1 && v <= 50) { setTplTopN(v); } else { setTplTopNText(String(tplTopN)); } }}
                  className="input-field" style={{ padding: '6px 8px', fontSize: '0.85rem', width: '100%' }} />
              </div>
            </div>
            <button className="btn-primary" onClick={() => fetchTplRecommendations(true)} disabled={tplRecommendLoading}
              style={{ width: '100%', justifyContent: 'center', fontSize: '0.85rem', padding: '8px 14px' }}>
              {tplRecommendLoading ? '计算中...' : '刷新推荐'}
            </button>
            {tplRecommendError && <p style={{ marginTop: '8px', fontSize: '0.82rem', color: '#ef4444' }}>{tplRecommendError}</p>}
          </div>

          <div style={{ marginBottom: '14px', padding: '8px 12px', background: '#eff6ff', borderRadius: '6px', border: '1px solid #bfdbfe' }}>
            <span style={{ fontSize: '0.82rem', color: '#475569' }}>当前绑定：</span>
            <strong style={{ color: '#0f172a' }}>{boundTemplateView}</strong>
          </div>

          {tplRecommendations.length === 0 && !tplRecommendLoading ? (
            <div style={{ textAlign: 'center', color: '#94a3b8', padding: '20px', fontSize: '0.9rem' }}>暂无推荐结果，请点击"刷新推荐"</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '500px', overflowY: 'auto' }}>
              {tplRecommendations.map((item) => {
                const isCurrentlyBound = projectTemplateId === item.templateId;
                return (
                  <div key={item.templateId} style={{
                    border: isCurrentlyBound ? '2px solid #0ea5e9' : '1px solid #e2e8f0',
                    borderRadius: '8px', padding: '12px',
                    background: isCurrentlyBound ? '#f0f9ff' : '#ffffff',
                    boxShadow: isCurrentlyBound ? '0 0 0 3px rgba(14,165,233,0.15)' : '0 1px 2px rgba(0,0,0,0.04)',
                    transition: 'all 0.2s',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                      <div style={{ width: '28px', height: '28px', borderRadius: '50%', background: item.rank <= 3 ? '#0ea5e9' : '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: '0.8rem', marginRight: '10px', flexShrink: 0 }}>{item.rank}</div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: '0.9rem', color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.templateName || item.templateId}
                          {isCurrentlyBound && <span style={{ marginLeft: '6px', fontSize: '0.72rem', color: '#0ea5e9', background: '#e0f2fe', padding: '2px 6px', borderRadius: '4px', fontWeight: 500 }}>当前绑定</span>}
                        </div>
                        <div style={{ fontSize: '0.76rem', color: '#64748b' }}>品类: {item.categoryId} | 镜头数: {item.segmentCount} | v{item.templateVersion}</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#f0f9ff', borderRadius: '6px' }}><div style={{ fontSize: '0.68rem', color: '#0369a1' }}>语义</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#0c4a6e' }}>{item.semanticScore.toFixed(3)}</div></div>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#f0fdf4', borderRadius: '6px' }}><div style={{ fontSize: '0.68rem', color: '#166534' }}>结构</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#14532d' }}>{item.structureScore.toFixed(3)}</div></div>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#eff6ff', borderRadius: '6px', border: '1px solid #bfdbfe' }}><div style={{ fontSize: '0.68rem', color: '#1e40af' }}>最终</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#1e3a5f' }}>{item.finalScore.toFixed(3)}</div></div>
                    </div>
                    <button className={isCurrentlyBound ? 'btn-outline' : 'btn-primary'} onClick={() => doBindTemplate(item.templateId, item.templateVersion)} disabled={bindingTemplate} style={{ width: '100%', justifyContent: 'center', fontSize: '0.82rem', padding: '7px 12px' }}>
                      {bindingTemplate ? '绑定中...' : isCurrentlyBound ? '已绑定' : '选择此模板'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* =====================
            RIGHT: BGM
        ===================== */}
        <div className="result-card">
          <h4>BGM智能推荐与绑定</h4>
          
          <div style={{ marginBottom: '16px', padding: '12px', background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>音乐匹配参数调优</span>
              <span style={{ fontSize: '0.7rem', color: '#94a3b8' }}>W1+W2+W3 = {(bgmW1 + bgmW2 + bgmW3).toFixed(2)}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '10px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>语义(W1): {bgmW1.toFixed(2)}</label>
                <input type="range" min="0" max="1" step="0.05" value={bgmW1} onChange={e => handleBgmWeightChange('w1', parseFloat(e.target.value))} style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>能量(W2): {bgmW2.toFixed(2)}</label>
                <input type="range" min="0" max="1" step="0.05" value={bgmW2} onChange={e => handleBgmWeightChange('w2', parseFloat(e.target.value))} style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>时长对齐(W3): {bgmW3.toFixed(2)}</label>
                <input type="range" min="0" max="1" step="0.05" value={bgmW3} onChange={e => handleBgmWeightChange('w3', parseFloat(e.target.value))} style={{ width: '100%' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>Top N</label>
                <input type="number" min="1" max="50" value={bgmTopNText} onChange={(e) => setBgmTopNText(e.target.value)} onBlur={() => { const v = parseInt(bgmTopNText, 10); if (!isNaN(v) && v >= 1 && v <= 50) { setBgmTopN(v); } else { setBgmTopNText(String(bgmTopN)); } }} className="input-field" style={{ padding: '6px 8px', fontSize: '0.85rem', width: '100%' }} />
              </div>
            </div>
            <button className="btn-primary" onClick={() => fetchBgmRecommendations(true)} disabled={bgmLoading}
              style={{ width: '100%', justifyContent: 'center', fontSize: '0.85rem', padding: '8px 14px', background: 'linear-gradient(to right, #8b5cf6, #6366f1)', border: 'none', color: '#fff' }}>
              {bgmLoading ? '计算中...' : '获取BGM推荐'}
            </button>
            {bgmError && <p style={{ marginTop: '8px', fontSize: '0.82rem', color: '#ef4444' }}>{bgmError}</p>}
          </div>

          <div style={{ marginBottom: '14px', padding: '12px', background: '#f5f3ff', borderRadius: '6px', border: '1px solid #ddd6fe', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <span style={{ fontSize: '0.82rem', color: '#4c1d95' }}>当前绑定BGM：</span>
              <strong style={{ color: '#4c1d95', display: 'block' }}>{currentBgm ? currentBgm.audioName || currentBgm.audioId : '未绑定'}</strong>
            </div>
            {currentBgm && (
              <button onClick={handleClearCurrentBgm} disabled={clearingBgm} style={{ background: '#fca5a5', color: '#7f1d1d', border: 'none', padding: '4px 10px', borderRadius: '4px', fontSize: '0.75rem', cursor: 'pointer' }}>
                {clearingBgm ? '清除中...' : '清除'}
              </button>
            )}
          </div>

          {bgmResults.length === 0 && !bgmLoading ? (
             <div style={{ textAlign: 'center', color: '#94a3b8', padding: '20px', fontSize: '0.9rem' }}>暂无推荐音乐，请点击"获取BGM推荐"</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '500px', overflowY: 'auto' }}>
              {bgmResults.map((item) => {
                const isCurrentlyBound = currentBgm?.audioId === item.audioId;
                const isPlaying = playingId === item.audioId;
                return (
                  <div key={item.audioId} style={{
                    border: isCurrentlyBound ? '2px solid #8b5cf6' : '1px solid #e2e8f0',
                    borderRadius: '8px', padding: '12px',
                    background: isCurrentlyBound ? '#f5f3ff' : '#ffffff',
                    transition: 'all 0.2s',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                      <button onClick={() => togglePlay(item)} style={{
                        width: '32px', height: '32px', borderRadius: '50%', background: isPlaying ? '#10b981' : '#f1f5f9', border: 'none',
                        color: isPlaying ? '#fff' : '#64748b', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '10px'
                      }}>
                        {isPlaying ? (
                          <svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
                        ) : (
                          <svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24" style={{ transform: 'translateX(2px)' }}><path d="M8 5v14l11-7z" /></svg>
                        )}
                      </button>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: '0.9rem', color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          <span style={{ fontSize: '0.75rem', fontWeight: 700, background: '#f1f5f9', padding: '2px 6px', borderRadius: '4px', marginRight: '6px', color: '#64748b' }}>#{item.rank}</span>
                          {item.audioName || item.audioId}
                        </div>
                        <div style={{ fontSize: '0.76rem', color: '#64748b' }}>BPM: {item.bpm} | 风格: {item.overallStyle} | {item.durationSeconds.toFixed(1)}s</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#faf5ff', borderRadius: '6px' }}><div style={{ fontSize: '0.68rem', color: '#7e22ce' }}>语义</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#6b21a8' }}>{item.semanticScore.toFixed(3)}</div></div>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#eef2ff', borderRadius: '6px' }}><div style={{ fontSize: '0.68rem', color: '#4338ca' }}>能量</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#3730a3' }}>{item.energyCurveScore.toFixed(3)}</div></div>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#ecfeff', borderRadius: '6px' }}><div style={{ fontSize: '0.68rem', color: '#0e7490' }}>时长</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#155e75' }}>{item.durationBpmScore.toFixed(3)}</div></div>
                      <div style={{ flex: 1, textAlign: 'center', padding: '5px 4px', background: '#f5f3ff', borderRadius: '6px', border: '1px solid #ddd6fe' }}><div style={{ fontSize: '0.68rem', color: '#6d28d9' }}>最终</div><div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#5b21b6' }}>{item.finalScore.toFixed(3)}</div></div>
                    </div>
                    <button className={isCurrentlyBound ? 'btn-outline' : 'btn-primary'} onClick={() => handleSelectBgm(item)} disabled={bindingAudioId === item.audioId} 
                      style={{ width: '100%', justifyContent: 'center', fontSize: '0.82rem', padding: '7px 12px', background: isCurrentlyBound ? 'transparent' : '#8b5cf6', borderColor: '#8b5cf6', color: isCurrentlyBound ? '#8b5cf6' : '#fff' }}>
                      {bindingAudioId === item.audioId ? '绑定中...' : isCurrentlyBound ? '已设为BGM' : '设为当前BGM'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

        </div>

      </div>
    </div>
  );
}

function pickLatestTemplates(templates: TemplateSummaryData[]): TemplateOption[] {
  const map = new Map<string, TemplateSummaryData>();
  templates.forEach((item) => {
    if (!item.templateId || !item.templateVersion) return;
    const current = map.get(item.templateId);
    if (!current) {
      map.set(item.templateId, item);
      return;
    }
    if ((item.templateVersion || 0) > (current.templateVersion || 0)) {
      map.set(item.templateId, item);
      return;
    }
    if ((item.templateVersion || 0) === (current.templateVersion || 0)) {
      const nextUpdated = new Date(item.updatedAt || item.createdAt || '').getTime();
      const currUpdated = new Date(current.updatedAt || current.createdAt || '').getTime();
      if (Number.isFinite(nextUpdated) && (!Number.isFinite(currUpdated) || nextUpdated > currUpdated)) {
        map.set(item.templateId, item);
      }
    }
  });

  return Array.from(map.values())
    .map((item) => ({
      templateId: item.templateId,
      templateVersion: item.templateVersion,
      templateName: item.templateName || item.templateId
    }))
    .sort((a, b) => a.templateName.localeCompare(b.templateName));
}
