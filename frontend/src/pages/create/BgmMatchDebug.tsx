import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { BgmRecommendItemData, ProjectBgmBindingData } from '../../types';

export const BgmMatchDebug: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const [w1, setW1] = useState<number>(0.35);
  const [w2, setW2] = useState<number>(0.50);
  const [w3, setW3] = useState<number>(0.15);
  const [topN, setTopN] = useState<number>(5);
  const [results, setResults] = useState<BgmRecommendItemData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [actionMessage, setActionMessage] = useState('');
  const [currentBgm, setCurrentBgm] = useState<ProjectBgmBindingData | null>(null);
  const [bindingAudioId, setBindingAudioId] = useState<string | null>(null);
  const [clearing, setClearing] = useState(false);
  
  // 当前播放的音频ID
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);

  // 协调权重，保证三者相加为 1
  const handleWeightChange = (type: 'w1' | 'w2' | 'w3', value: number) => {
    const newVal = Math.max(0, Math.min(1, value));
    if (type === 'w1') {
      setW1(newVal);
      const remaining = 1 - newVal;
      const sumOthers = w2 + w3;
      if (sumOthers > 0) {
        setW2(parseFloat(((w2 / sumOthers) * remaining).toFixed(2)));
        setW3(parseFloat(((w3 / sumOthers) * remaining).toFixed(2)));
      } else {
        setW2(parseFloat((remaining / 2).toFixed(2)));
        setW3(parseFloat((remaining / 2).toFixed(2)));
      }
    } else if (type === 'w2') {
      setW2(newVal);
      const remaining = 1 - newVal;
      const sumOthers = w1 + w3;
      if (sumOthers > 0) {
        setW1(parseFloat(((w1 / sumOthers) * remaining).toFixed(2)));
        setW3(parseFloat(((w3 / sumOthers) * remaining).toFixed(2)));
      } else {
        setW1(parseFloat((remaining / 2).toFixed(2)));
        setW3(parseFloat((remaining / 2).toFixed(2)));
      }
    } else if (type === 'w3') {
      setW3(newVal);
      const remaining = 1 - newVal;
      const sumOthers = w1 + w2;
      if (sumOthers > 0) {
        setW1(parseFloat(((w1 / sumOthers) * remaining).toFixed(2)));
        setW2(parseFloat(((w2 / sumOthers) * remaining).toFixed(2)));
      } else {
        setW1(parseFloat((remaining / 2).toFixed(2)));
        setW2(parseFloat((remaining / 2).toFixed(2)));
      }
    }
  };

  const handleTest = async () => {
    if (!projectId) return;
    setLoading(true);
    setError('');
    setActionMessage('');
    // 停止正在播放的音频
    if (audioElement) {
      audioElement.pause();
      setPlayingId(null);
    }
    try {
      const res = await creationApi.recommendBgm(projectId, w1, w2, w3, topN);
      setResults(res.data.recommendations || []);
    } catch (err: any) {
      setError(err.message || '获取 BGM 推荐失败，可能素材尚未成功分析或项目不存在。');
    } finally {
      setLoading(false);
    }
  };

  const loadCurrentBgm = async (silent: boolean = false) => {
    if (!projectId) return;
    if (!silent) {
      setActionMessage('');
    }
    try {
      const res = await creationApi.getSelectedBgm(projectId);
      setCurrentBgm(res.data ?? null);
    } catch (err: any) {
      const message = err?.message || '查询当前 BGM 失败';
      if (!silent) {
        setError(message);
      }
    }
  };

  const handleSelectBgm = async (item: BgmRecommendItemData) => {
    if (!projectId) return;
    setBindingAudioId(item.audioId);
    setError('');
    setActionMessage('');
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
      setActionMessage(`已将「${item.audioName || item.audioId}」设为当前项目 BGM`);
    } catch (err: any) {
      setError(err?.message || '选择当前 BGM 失败');
    } finally {
      setBindingAudioId(null);
    }
  };

  const handleClearCurrentBgm = async () => {
    if (!projectId) return;
    setClearing(true);
    setError('');
    setActionMessage('');
    try {
      await creationApi.clearSelectedBgm(projectId);
      setCurrentBgm(null);
      setActionMessage('已清除当前项目 BGM');
    } catch (err: any) {
      setError(err?.message || '清除当前 BGM 失败');
    } finally {
      setClearing(false);
    }
  };

  // 自动触发一次加载
  useEffect(() => {
    handleTest();
    loadCurrentBgm(true);
    return () => {
      if (audioElement) {
        audioElement.pause();
      }
    };
  }, [projectId]);

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
        setError("无法播放音频，请确保本地静态资源映射正常: " + item.filePath);
      });
      audio.onended = () => {
        setPlayingId(null);
      };
      setAudioElement(audio);
      setPlayingId(item.audioId);
    }
  };

  return (
    <div className="bgm-debug-container">
      {/* 嵌入局部高性能 Vanilla CSS 样式，避免全局 Tailwind 未加载导致样式失效 */}
      <style>{`
        .bgm-debug-container {
          padding: 32px;
          max-width: 1280px;
          margin: 0 auto;
          min-height: 100vh;
          display: flex;
          flex-direction: column;
          background-color: #0f172a;
          color: #f8fafc;
          font-family: system-ui, -apple-system, sans-serif;
          box-sizing: border-box;
        }
        .bgm-debug-container * {
          box-sizing: border-box;
        }
        .bgm-debug-header {
          display: flex;
          align-items: center;
          justify-content: justify;
          justify-content: space-between;
          margin-bottom: 32px;
          padding-bottom: 16px;
          border-bottom: 1px solid #1e293b;
        }
        .bgm-debug-title {
          font-size: 1.85rem;
          font-weight: 800;
          letter-spacing: -0.025em;
          background: linear-gradient(to right, #a78bfa, #818cf8, #22d3ee);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          margin: 0;
        }
        .bgm-debug-subtitle {
          font-size: 0.875rem;
          color: #94a3b8;
          margin-top: 4px;
          margin-bottom: 0;
        }
        .btn-back-workflow {
          padding: 10px 20px;
          font-size: 0.875rem;
          font-weight: 600;
          background-color: #1e293b;
          border: 1px solid #334155;
          color: #f8fafc;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .btn-back-workflow:hover {
          background-color: #334155;
          border-color: #475569;
        }
        .bgm-debug-layout {
          display: grid;
          grid-template-columns: 1fr;
          gap: 32px;
          flex: 1;
        }
        @media (min-width: 1024px) {
          .bgm-debug-layout {
            grid-template-columns: 350px 1fr;
          }
        }
        .bgm-debug-sidebar {
          display: flex;
          flex-direction: column;
          gap: 24px;
        }
        .bgm-debug-card-panel {
          background-color: rgba(30, 41, 59, 0.8);
          border: 1px solid rgba(51, 65, 85, 0.8);
          padding: 24px;
          border-radius: 16px;
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3);
          backdrop-filter: blur(12px);
        }
        .panel-title {
          font-size: 1.25rem;
          font-weight: 700;
          color: #a5b4fc;
          margin-top: 0;
          margin-bottom: 24px;
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .slider-item {
          margin-bottom: 24px;
        }
        .slider-header {
          display: flex;
          justify-content: space-between;
          font-size: 0.875rem;
          margin-bottom: 8px;
        }
        .slider-label {
          font-weight: 600;
          color: #cbd5e1;
        }
        .slider-value {
          font-family: monospace;
          font-weight: 700;
        }
        .slider-input {
          width: 100%;
          height: 6px;
          background: #334155;
          border-radius: 9999px;
          outline: none;
          cursor: pointer;
          -webkit-appearance: none;
        }
        .slider-input::-webkit-slider-thumb {
          -webkit-appearance: none;
          width: 16px;
          height: 16px;
          border-radius: 50%;
          cursor: pointer;
        }
        .slider-purple::-webkit-slider-thumb { background: #8b5cf6; }
        .slider-indigo::-webkit-slider-thumb { background: #6366f1; }
        .slider-cyan::-webkit-slider-thumb { background: #06b6d4; }
        .slider-desc {
          font-size: 0.75rem;
          color: #94a3b8;
          margin-top: 6px;
          margin-bottom: 0;
        }
        .input-number-group {
          margin-bottom: 24px;
        }
        .input-number-label {
          display: block;
          font-size: 0.875rem;
          font-weight: 600;
          color: #cbd5e1;
          margin-bottom: 8px;
        }
        .input-number-field {
          width: 100%;
          background-color: #0f172a;
          border: 1px solid #334155;
          border-radius: 12px;
          padding: 10px 16px;
          color: #ffffff;
          font-weight: 600;
          outline: none;
          transition: border-color 0.2s ease;
        }
        .input-number-field:focus {
          border-color: #6366f1;
        }
        .weight-sum-box {
          background-color: rgba(15, 23, 42, 0.6);
          padding: 14px;
          border-radius: 12px;
          border: 1px solid #1e293b;
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 0.75rem;
          margin-bottom: 24px;
        }
        .btn-update-list {
          width: 100%;
          padding: 14px;
          background: linear-gradient(to right, #7c3aed, #4f46e5);
          color: #ffffff;
          font-weight: 700;
          border: none;
          border-radius: 12px;
          cursor: pointer;
          transition: all 0.2s ease;
          box-shadow: 0 10px 15px -3px rgba(99, 102, 241, 0.3);
        }
        .btn-update-list:hover {
          filter: brightness(1.1);
        }
        .btn-update-list:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        .error-message {
          margin-top: 16px;
          font-size: 0.75rem;
          background-color: rgba(127, 29, 29, 0.4);
          border: 1px solid #991b1b;
          padding: 12px;
          border-radius: 8px;
          color: #fca5a5;
        }
        .info-panel {
          background-color: rgba(30, 41, 59, 0.4);
          border: 1px solid rgba(51, 65, 85, 0.4);
          padding: 20px;
          border-radius: 16px;
          font-size: 0.75rem;
          color: #94a3b8;
        }
        .info-panel-title {
          font-weight: 700;
          color: #e2e8f0;
          margin-top: 0;
          margin-bottom: 14px;
          font-size: 0.875rem;
        }
        .info-panel-text {
          margin-bottom: 10px;
          line-height: 1.5;
        }
        .info-panel-text:last-child {
          margin-bottom: 0;
        }
        .results-panel {
          background-color: rgba(30, 41, 59, 0.8);
          border: 1px solid rgba(51, 65, 85, 0.8);
          border-radius: 16px;
          display: flex;
          flex-direction: column;
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3);
          backdrop-filter: blur(12px);
          overflow: hidden;
        }
        .results-header {
          padding: 20px;
          border-bottom: 1px solid #334155;
          display: flex;
          justify-content: space-between;
          align-items: center;
          background-color: rgba(30, 41, 59, 0.4);
        }
        .results-title {
          font-size: 1.25rem;
          font-weight: 700;
          color: #a5b4fc;
          margin: 0;
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .results-badge {
          font-size: 0.75rem;
          background-color: rgba(30, 41, 59, 0.8);
          color: #818cf8;
          padding: 4px 12px;
          border-radius: 9999px;
          border: 1px solid #312e81;
          font-weight: 600;
        }
        .results-body {
          padding: 24px;
          overflow-y: auto;
          display: flex;
          flex-direction: column;
          gap: 16px;
        }
        .current-bgm-card {
          background: linear-gradient(135deg, rgba(76, 29, 149, 0.35), rgba(30, 41, 59, 0.75));
          border: 1px solid rgba(129, 140, 248, 0.35);
          border-radius: 16px;
          padding: 18px 20px;
          margin-bottom: 4px;
        }
        .current-bgm-row {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 16px;
        }
        .current-bgm-title {
          margin: 0;
          font-size: 1rem;
          font-weight: 800;
          color: #e0e7ff;
        }
        .current-bgm-meta {
          margin-top: 8px;
          display: flex;
          flex-wrap: wrap;
          gap: 10px;
          font-size: 0.75rem;
          color: #cbd5e1;
        }
        .current-bgm-path {
          margin-top: 8px;
          margin-bottom: 0;
          font-size: 10px;
          font-family: monospace;
          color: #94a3b8;
          word-break: break-all;
        }
        .current-bgm-actions {
          display: flex;
          align-items: center;
          gap: 10px;
          flex-shrink: 0;
        }
        .btn-clear-bgm {
          padding: 10px 14px;
          border-radius: 10px;
          border: 1px solid rgba(248, 113, 113, 0.35);
          background: rgba(127, 29, 29, 0.25);
          color: #fecaca;
          font-size: 0.75rem;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.2s ease;
        }
        .btn-clear-bgm:hover {
          background: rgba(153, 27, 27, 0.4);
        }
        .btn-clear-bgm:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        .action-message {
          margin-top: 16px;
          font-size: 0.75rem;
          background-color: rgba(6, 95, 70, 0.25);
          border: 1px solid rgba(16, 185, 129, 0.35);
          padding: 12px;
          border-radius: 8px;
          color: #6ee7b7;
        }
        .placeholder-box {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 80px 0;
          color: #475569;
        }
        .placeholder-title {
          font-size: 1.125rem;
          font-weight: 600;
          color: #64748b;
          margin-top: 16px;
          margin-bottom: 4px;
        }
        .bgm-item-card {
          position: relative;
          background-color: rgba(15, 23, 42, 0.6);
          border: 1px solid #1e293b;
          border-radius: 12px;
          padding: 20px;
          display: flex;
          flex-direction: column;
          transition: all 0.3s ease;
          overflow: hidden;
        }
        @media (min-width: 768px) {
          .bgm-item-card {
            flex-direction: row;
            align-items: center;
            justify-content: space-between;
          }
        }
        .bgm-item-card:hover {
          border-color: rgba(99, 102, 241, 0.5);
          background-color: #0f172a;
        }
        .bgm-item-card::before {
          content: '';
          position: absolute;
          left: 0;
          top: 0;
          bottom: 0;
          width: 3px;
          background-color: transparent;
          transition: background-color 0.3s ease;
        }
        .bgm-item-card:hover::before {
          background: linear-gradient(to bottom, #8b5cf6, #06b6d4);
        }
        .bgm-card-left {
          display: flex;
          align-items: center;
          gap: 16px;
          flex: 1;
          margin-bottom: 16px;
        }
        @media (min-width: 768px) {
          .bgm-card-left {
            margin-bottom: 0;
            margin-right: 16px;
          }
        }
        .btn-play-pause {
          width: 48px;
          height: 48px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          border: none;
          cursor: pointer;
          transition: all 0.2s ease;
        }
        .btn-play-pause.playing {
          background-color: #10b981;
          color: #0f172a;
          box-shadow: 0 0 12px rgba(16, 185, 129, 0.4);
        }
        .btn-play-pause.paused {
          background-color: #1e293b;
          color: #ffffff;
        }
        .btn-play-pause.paused:hover {
          background-color: #334155;
        }
        .bgm-info-title-row {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .bgm-rank-badge {
          font-size: 0.75rem;
          font-weight: 700;
          font-family: monospace;
          background-color: #1e293b;
          color: #94a3b8;
          padding: 2px 8px;
          border-radius: 4px;
        }
        .bgm-title-text {
          font-size: 1.125rem;
          font-weight: 700;
          color: #ffffff;
          margin: 0;
        }
        .bgm-metadata-row {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 12px;
          font-size: 0.75rem;
          color: #94a3b8;
          margin-top: 4px;
        }
        .metadata-highlight {
          color: #cbd5e1;
          font-weight: 600;
        }
        .bgm-path-text {
          font-size: 10px;
          color: #475569;
          font-family: monospace;
          margin-top: 6px;
          margin-bottom: 0;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 350px;
        }
        .bgm-card-right {
          display: flex;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;
          justify-content: flex-end;
        }
        .score-box {
          text-align: center;
          padding: 6px 10px;
          background-color: rgba(30, 41, 59, 0.4);
          border-radius: 8px;
          min-width: 70px;
        }
        .score-box-label {
          font-size: 10px;
          color: #64748b;
          margin-bottom: 2px;
        }
        .score-box-val {
          font-size: 0.875rem;
          font-weight: 700;
        }
        .score-purple { color: #c084fc; }
        .score-indigo { color: #818cf8; }
        .score-cyan { color: #22d3ee; }
        .final-score-box {
          text-align: center;
          padding: 10px 14px;
          background-color: rgba(49, 46, 129, 0.4);
          border: 1px solid rgba(99, 102, 241, 0.3);
          border-radius: 12px;
          min-width: 85px;
          margin-left: 8px;
        }
        .final-score-label {
          font-size: 10px;
          color: #818cf8;
          font-weight: 600;
          margin-bottom: 2px;
        }
        .final-score-val {
          font-size: 1.125rem;
          font-weight: 900;
          color: #c7d2fe;
        }
        .btn-select-bgm {
          padding: 12px 16px;
          border-radius: 12px;
          border: 1px solid rgba(34, 211, 238, 0.35);
          background: linear-gradient(to right, rgba(14, 116, 144, 0.85), rgba(79, 70, 229, 0.85));
          color: #ffffff;
          font-size: 0.75rem;
          font-weight: 800;
          cursor: pointer;
          transition: all 0.2s ease;
          min-width: 120px;
        }
        .btn-select-bgm:hover {
          filter: brightness(1.08);
        }
        .btn-select-bgm:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        .current-chip {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 4px 10px;
          border-radius: 9999px;
          font-size: 0.75rem;
          font-weight: 700;
          background: rgba(16, 185, 129, 0.18);
          border: 1px solid rgba(16, 185, 129, 0.3);
          color: #86efac;
        }
      `}</style>

      {/* 头部标题与返回 */}
      <div className="bgm-debug-header">
        <div>
          <h1 className="bgm-debug-title">智能 BGM 推荐调试测试床</h1>
          <p className="bgm-debug-subtitle">项目ID: {projectId}</p>
        </div>
        <button
          onClick={() => navigate(`/create/detail/${projectId}/gap-detection`)}
          className="btn-back-workflow"
        >
          <svg width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          返回项目工作流
        </button>
      </div>

      <div className="bgm-debug-layout">
        {/* 左侧：参数调优面板 */}
        <div className="bgm-debug-sidebar">
          <div className="bgm-debug-card-panel">
            <h2 className="panel-title">
              <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
              </svg>
              三维算法权重调优
            </h2>

            <div>
              {/* 语义权重 */}
              <div className="slider-item">
                <div className="slider-header">
                  <span className="slider-label">语义匹配权重 (W1)</span>
                  <span className="slider-value score-purple">{(w1 * 100).toFixed(0)}%</span>
                </div>
                <input
                  type="range" min="0" max="1" step="0.01"
                  value={w1}
                  onChange={e => handleWeightChange('w1', parseFloat(e.target.value))}
                  className="slider-input slider-purple"
                />
                <p className="slider-desc">基于 Chroma 向量检索与素材标签的多模态语义相似度</p>
              </div>

              {/* 能量曲线权重 */}
              <div className="slider-item">
                <div className="slider-header">
                  <span className="slider-label">能量曲线匹配权重 (W2)</span>
                  <span className="slider-value score-indigo">{(w2 * 100).toFixed(0)}%</span>
                </div>
                <input
                  type="range" min="0" max="1" step="0.01"
                  value={w2}
                  onChange={e => handleWeightChange('w2', parseFloat(e.target.value))}
                  className="slider-input slider-indigo"
                />
                <p className="slider-desc">拟合模板与 BGM 绝对时间轴上的感知节奏/能量起伏波形</p>
              </div>

              {/* 物理特征权重 */}
              <div className="slider-item">
                <div className="slider-header">
                  <span className="slider-label">时长物理适配权重 (W3)</span>
                  <span className="slider-value score-cyan">{(w3 * 100).toFixed(0)}%</span>
                </div>
                <input
                  type="range" min="0" max="1" step="0.01"
                  value={w3}
                  onChange={e => handleWeightChange('w3', parseFloat(e.target.value))}
                  className="slider-input slider-cyan"
                />
                <p className="slider-desc">惩罚时长不足的音频；评估 BPM 与运镜的物理契合度</p>
              </div>

              {/* Top N 数量 */}
              <div className="input-number-group">
                <label className="input-number-label">返回推荐数量 (Top N)</label>
                <input
                  type="number" min="1" max="20"
                  value={topN}
                  onChange={e => setTopN(Math.max(1, Math.min(20, parseInt(e.target.value) || 5)))}
                  className="input-number-field"
                />
              </div>

              {/* 权重和指示器 */}
              <div className="weight-sum-box">
                <span style={{ color: '#94a3b8' }}>当前权重总和:</span>
                <span style={{
                  fontFamily: 'monospace',
                  fontWeight: 'bold',
                  color: Math.abs(w1 + w2 + w3 - 1.0) < 0.01 ? '#10b981' : '#f59e0b'
                }}>
                  {((w1 + w2 + w3) * 100).toFixed(0)}%
                </span>
              </div>

              {/* 触发匹配按钮 */}
              <button
                onClick={handleTest}
                disabled={loading}
                className="btn-update-list"
              >
                {loading ? '正在计算推荐模型...' : '更新推荐列表'}
              </button>
            </div>
            {error && <div className="error-message">{error}</div>}
            {actionMessage && <div className="action-message">{actionMessage}</div>}
          </div>

          {/* 补充：核心拦截/匹配逻辑说明 */}
          <div className="info-panel">
            <h3 className="info-panel-title">💡 核心拦截与打分规则</h3>
            <p className="info-panel-text">
              <strong style={{ color: '#f43f5e' }}>🚫 一票否决 (Hard Veto):</strong> 若模板环境被标记为 <code>ASMR</code>，系统将硬性拒绝任何重低音、摇滚、电子、Hip-hop 等嘈杂风格的 BGM，得分直接判为无效并予以剔除。
            </p>
            <p className="info-panel-text">
              <strong style={{ color: '#22d3ee' }}>⏳ 时长适配惩罚:</strong> BGM 的时长应该能够完全覆盖视频/模板的总时长。对于长度不足的 BGM 必须进行循环播放，并进行一定的衰减扣分。
            </p>
            <p className="info-panel-text">
              <strong style={{ color: '#818cf8' }}>🔥 能量拟合对齐:</strong> 对齐模板段落（Hook、Climax、Outro 等）的期望能量值与音频在对应绝对时间片段的真实分贝能量，差值越大，扣分越多。
            </p>
          </div>
        </div>

        {/* 右侧：推荐结果展示列表 */}
        <div className="results-panel">
          <div className="results-header">
            <h2 className="results-title">
              <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
              </svg>
              智能推荐结果 ({results.length})
            </h2>
            <span className="results-badge">
              Chroma db 独立集合
            </span>
          </div>

          <div className="results-body">
            {currentBgm && (
              <div className="current-bgm-card">
                <div className="current-bgm-row">
                  <div style={{ flex: 1 }}>
                    <div className="current-chip">当前已选 BGM</div>
                    <h3 className="current-bgm-title" style={{ marginTop: 10 }}>
                      {currentBgm.audioName || currentBgm.audioId}
                    </h3>
                    <div className="current-bgm-meta">
                      <span>音频ID: <strong>{currentBgm.audioId}</strong></span>
                      <span>Mix: <strong>{currentBgm.mixLevel}</strong></span>
                      <span>Volume: <strong>{currentBgm.volume.toFixed(2)}</strong></span>
                      <span>来源: <strong>{currentBgm.sourceType}</strong></span>
                    </div>
                    <p className="current-bgm-path" title={currentBgm.previewUrl || currentBgm.srcPath}>
                      {currentBgm.previewUrl || currentBgm.srcPath}
                    </p>
                  </div>
                  <div className="current-bgm-actions">
                    <button
                      type="button"
                      className="btn-clear-bgm"
                      onClick={handleClearCurrentBgm}
                      disabled={clearing}
                    >
                      {clearing ? '清除中...' : '清除当前 BGM'}
                    </button>
                  </div>
                </div>
              </div>
            )}
            {results.length === 0 && !loading ? (
              <div className="placeholder-box">
                <svg width="64" height="64" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24" style={{ color: '#334155' }}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div className="placeholder-title">暂无推荐的背景音乐</div>
                <div style={{ fontSize: '0.875rem', color: '#475569' }}>请确保素材已经分析完毕（处于 PROFILED 状态）</div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {results.map((item) => (
                  <div key={item.audioId} className="bgm-item-card">
                    <div className="bgm-card-left">
                      {/* 播放/暂停控制图标 */}
                      <button
                        onClick={() => togglePlay(item)}
                        className={`btn-play-pause ${playingId === item.audioId ? 'playing' : 'paused'}`}
                      >
                        {playingId === item.audioId ? (
                          <svg width="20" height="20" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
                          </svg>
                        ) : (
                          <svg width="20" height="20" fill="currentColor" viewBox="0 0 24 24" style={{ transform: 'translateX(2px)' }}>
                            <path d="M8 5v14l11-7z" />
                          </svg>
                        )}
                      </button>

                      <div>
                        <div className="bgm-info-title-row">
                          <span className="bgm-rank-badge">
                            RANK {item.rank}
                          </span>
                          <h3 className="bgm-title-text">
                            {item.audioName || item.audioId}
                          </h3>
                        </div>
                        <div className="bgm-metadata-row">
                          <span>风格: <span className="metadata-highlight">{item.overallStyle}</span></span>
                          <span style={{ color: '#475569' }}>•</span>
                          <span>BPM: <span className="metadata-highlight">{item.bpm}</span></span>
                          <span style={{ color: '#475569' }}>•</span>
                          <span>时长: <span className="metadata-highlight">{item.durationSeconds.toFixed(1)}s</span></span>
                        </div>
                        <p className="bgm-path-text" title={item.filePath}>
                          URL: {item.filePath}
                        </p>
                      </div>
                    </div>

                    {/* 得分拆解展示 */}
                    <div className="bgm-card-right">
                      <div className="score-box">
                        <div className="score-box-label">语义 W1</div>
                        <div className="score-box-val score-purple">{(item.semanticScore * 100).toFixed(0)}</div>
                      </div>
                      <div className="score-box">
                        <div className="score-box-label">能量 W2</div>
                        <div className="score-box-val score-indigo">{(item.energyCurveScore * 100).toFixed(0)}</div>
                      </div>
                      <div className="score-box">
                        <div className="score-box-label">时长 W3</div>
                        <div className="score-box-val score-cyan">{(item.durationBpmScore * 100).toFixed(0)}</div>
                      </div>
                      <div className="final-score-box">
                        <div className="final-score-label">综合匹配度</div>
                        <div className="final-score-val">
                          {item.finalScore.toFixed(3)}
                        </div>
                      </div>
                      <button
                        type="button"
                        className="btn-select-bgm"
                        onClick={() => handleSelectBgm(item)}
                        disabled={bindingAudioId === item.audioId}
                      >
                        {currentBgm?.audioId === item.audioId
                          ? '当前项目已选'
                          : bindingAudioId === item.audioId
                            ? '绑定中...'
                            : '设为当前 BGM'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
