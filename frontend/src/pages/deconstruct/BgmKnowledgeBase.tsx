import React, { useRef, useState, useEffect } from 'react';
import { useToast } from '../../contexts/ToastContext';
import { request } from '../../services/http';
import { ApiResponse } from '../../types';

export default function BgmKnowledgeBase() {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  
  const [isUploading, setIsUploading] = useState(false);
  const [bgmData, setBgmData] = useState<any>(null);
  const [bgmList, setBgmList] = useState<any[]>([]);
  const [playingId, setPlayingId] = useState<string | null>(null);

  useEffect(() => {
    fetchBgmList();
  }, []);

  const fetchBgmList = async () => {
    try {
      const res = await request<ApiResponse<any[]>>('/v1/bgm/list');
      if (res.code === '0' || res.code === '200') {
        const list = res.data.map(item => ({
          ...item,
          id: item.audioId,
          audioName: item.audioName,
          filePath: item.filePath,
          style: item.globalMetrics?.overallStyle || 'unknown',
          bpm: item.globalMetrics?.bpm || 0,
          duration: item.globalMetrics?.durationSeconds ? `${item.globalMetrics.durationSeconds}s` : '--',
          rawFullData: item // 保存全量数据供详情页使用
        }));
        setBgmList(list);
      } else {
        throw new Error(res.message);
      }
    } catch (err: any) {
      showToast('获取BGM列表失败: ' + err.message, 'error');
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      uploadFile(e.target.files[0]);
    }
  };

  const uploadFile = async (file: File) => {
    setIsUploading(true);
    setBgmData(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await request<ApiResponse<any>>('/v1/bgm/analyze', {
        method: 'POST',
        body: formData,
        headers: { 'Accept': 'application/json' }
      });
      
      if (res.code === '0' || res.code === '200') {
        const newData = res.data;
        setBgmData(newData);
        
        // Add to list if not exists
        if (!bgmList.find(b => b.id === newData.audioId)) {
          setBgmList(prev => [{
            id: newData.audioId,
            audioName: newData.audioName || file.name,
            filePath: newData.filePath || file.name,
            style: newData.globalMetrics?.overallStyle || 'unknown',
            bpm: newData.globalMetrics?.bpm || 0,
            duration: newData.globalMetrics?.durationSeconds ? `${newData.globalMetrics.durationSeconds}s` : '--'
          }, ...prev]);
        }
        
        showToast('BGM分析完成', 'success');
      } else {
        throw new Error(res.message);
      }
    } catch (err: any) {
      showToast('BGM上传或分析失败: ' + err.message, 'error');
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handlePlayToggle = (id: string) => {
    if (playingId === id) {
      audioRef.current?.pause();
      setPlayingId(null);
    } else {
      setPlayingId(id);
      if (audioRef.current) {
        audioRef.current.src = `/api/v1/bgm/play/${id}`;
        audioRef.current.play().catch(e => showToast('播放失败: ' + e.message, 'error'));
      }
    }
  };

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('确定要删除这个 BGM 吗？将同时删除本地文件与向量库。')) return;
    try {
      await request(`/v1/bgm/${id}`, { method: 'DELETE' });
      showToast('删除成功', 'success');
      if (playingId === id) {
        audioRef.current?.pause();
        setPlayingId(null);
      }
      fetchBgmList();
    } catch (err: any) {
      showToast('删除失败: ' + err.message, 'error');
    }
  };

  const handleViewDetails = (bgm: any) => {
    if (bgm.rawFullData) {
      setBgmData(bgm.rawFullData);
    } else {
      showToast('该本地音频暂无分析结果', 'error');
    }
  };

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: '24px', padding: '24px', boxSizing: 'border-box', overflowY: 'auto' }} className="custom-scrollbar">
      
      {!bgmData && !isUploading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h2 style={{ margin: '0 0 8px 0', color: '#f8fafc', fontSize: '1.4rem' }}>本地 BGM 资源库</h2>
              <p style={{ margin: 0, color: '#94a3b8', fontSize: '0.9rem' }}>管理本地环境音与纯音乐，支持大模型多模态结构分析</p>
            </div>
            <button 
              onClick={() => fileInputRef.current?.click()} 
              style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '8px', cursor: 'pointer', fontWeight: 600, boxShadow: '0 4px 12px rgba(59, 130, 246, 0.3)' }}
            >
              <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
              上传新 BGM
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {bgmList.map(bgm => (
              <div key={bgm.id} style={{ display: 'flex', alignItems: 'center', background: 'rgba(30, 41, 59, 0.6)', padding: '16px 24px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.05)', gap: '20px', transition: 'all 0.2s' }} onMouseOver={e => e.currentTarget.style.background='rgba(30, 41, 59, 0.8)'} onMouseOut={e => e.currentTarget.style.background='rgba(30, 41, 59, 0.6)'}>
                
                <button 
                  onClick={() => handlePlayToggle(bgm.id)} 
                  style={{ background: playingId === bgm.id ? '#a855f7' : 'rgba(255,255,255,0.1)', border: 'none', borderRadius: '50%', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#fff', transition: 'all 0.2s', boxShadow: playingId === bgm.id ? '0 0 15px rgba(168, 85, 247, 0.6)' : 'none' }}
                >
                  {playingId === bgm.id ? (
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
                  ) : (
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style={{ marginLeft: '4px' }}><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                  )}
                </button>
                
                <button 
                  onClick={(e) => handleDelete(bgm.id, e)}
                  style={{ background: 'rgba(239, 68, 68, 0.1)', border: 'none', borderRadius: '50%', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#ef4444', transition: 'all 0.2s', marginLeft: 'auto', marginRight: '16px' }}
                  title="删除此 BGM"
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                </button>

                <div style={{ flex: 1 }}>
                  <div style={{ color: '#f8fafc', fontSize: '1.1rem', fontWeight: 600 }}>{bgm.audioName}</div>
                  <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginTop: '4px' }}>{bgm.filePath}</div>
                </div>
                
                <div style={{ display: 'flex', gap: '32px', color: '#cbd5e1', fontSize: '0.9rem', marginRight: '16px' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <span style={{ color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>曲风</span>
                    <span style={{ background: 'rgba(56, 189, 248, 0.15)', color: '#38bdf8', padding: '2px 8px', borderRadius: '4px', fontSize: '0.8rem' }}>{bgm.style}</span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <span style={{ color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>BPM</span>
                    <span style={{ color: '#f8fafc', fontWeight: 500 }}>{bgm.bpm}</span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <span style={{ color: '#64748b', fontSize: '0.75rem', marginBottom: '4px' }}>时长</span>
                    <span style={{ color: '#f8fafc', fontWeight: 500 }}>{bgm.duration}</span>
                  </div>
                </div>
                
                <button 
                  onClick={() => handleViewDetails(bgm)} 
                  style={{ padding: '8px 16px', background: 'transparent', border: '1px solid rgba(168, 85, 247, 0.5)', color: '#c084fc', borderRadius: '8px', cursor: 'pointer', transition: 'all 0.2s', fontWeight: 500 }}
                  onMouseOver={e => { e.currentTarget.style.background = 'rgba(168, 85, 247, 0.1)'; e.currentTarget.style.borderColor = '#a855f7'; }}
                  onMouseOut={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'rgba(168, 85, 247, 0.5)'; }}
                >
                  大模型解析
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {isUploading && (
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ width: '48px', height: '48px', border: '3px solid rgba(255,255,255,0.1)', borderTopColor: '#38bdf8', borderRadius: '50%', animation: 'spin 1s ease-in-out infinite', marginBottom: '16px' }}></div>
          <h3 style={{ margin: '0 0 8px 0', color: '#f8fafc' }}>LLM 正在分析音频结构与听感...</h3>
          <p style={{ margin: 0, color: '#94a3b8', fontSize: '0.9rem' }}>通常需要 5-10 秒钟</p>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </div>
      )}

      {bgmData && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '16px' }}>
            <div>
              <button 
                onClick={() => setBgmData(null)}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer', padding: 0, marginBottom: '12px' }}
              >
                <svg width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
                返回列表
              </button>
              <h2 style={{ margin: '0 0 8px 0', color: '#f8fafc', fontSize: '1.8rem', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <svg width="24" height="24" fill="none" stroke="#a855f7" strokeWidth="2" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>
                {bgmData.audioName || '未命名音频'}
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 'normal', background: 'rgba(255,255,255,0.05)', padding: '4px 8px', borderRadius: '6px' }}>{bgmData.audioId}</span>
              </h2>
              <p style={{ margin: 0, color: '#94a3b8' }}>文件路径: {bgmData.filePath}</p>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '24px' }}>
            <div style={{ flex: 1, background: 'rgba(30, 41, 59, 0.5)', borderRadius: '16px', padding: '24px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <h3 style={{ margin: '0 0 16px 0', color: '#38bdf8', fontSize: '1rem' }}>全局指标 (Global Metrics)</h3>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
                <MetricCard label="BPM" value={bgmData.globalMetrics?.bpm} />
                <MetricCard label="整体曲风" value={bgmData.globalMetrics?.overallStyle} />
                <MetricCard label="时长 (秒)" value={bgmData.globalMetrics?.durationSeconds} />
              </div>
            </div>
            <div style={{ flex: 1, background: 'rgba(30, 41, 59, 0.5)', borderRadius: '16px', padding: '24px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <h3 style={{ margin: '0 0 16px 0', color: '#a3e635', fontSize: '1rem' }}>响度参数 (Loudness)</h3>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
                <MetricCard label="Integrated LUFS" value={bgmData.loudnessMetrics?.integratedLufs} />
                <MetricCard label="True Peak (dBTP)" value={bgmData.loudnessMetrics?.truePeakDbtp} />
                <MetricCard label="Loudness Range" value={bgmData.loudnessMetrics?.loudnessRangeLra} />
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '24px' }}>
            {/* Timeline */}
            <div style={{ flex: 2, background: 'rgba(30, 41, 59, 0.5)', borderRadius: '16px', padding: '24px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <h3 style={{ margin: '0 0 24px 0', color: '#f8fafc', fontSize: '1.2rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '1.4rem' }}>⏱️</span> 听觉切片时序 (Auditory Timeline)
              </h3>
              <div style={{ 
                  position: 'relative', 
                  paddingLeft: '24px', 
                  paddingRight: '24px', 
                  borderLeft: '2px solid rgba(168, 85, 247, 0.3)', 
              }}>
                {bgmData.auditoryTimeline?.map((ev: any, idx: number) => (
                  <div key={idx} style={{ position: 'relative', marginBottom: '32px', display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <div style={{ position: 'absolute', left: '-30px', top: '4px', width: '10px', height: '10px', borderRadius: '50%', background: '#a855f7', boxShadow: '0 0 8px #a855f7', zIndex: 2 }} />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <span style={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: '0.9rem' }}>
                            [{ev.timeRange?.start}s - {ev.timeRange?.end}s]
                        </span>
                        <span style={{ background: 'rgba(168, 85, 247, 0.15)', color: '#c084fc', padding: '4px 10px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 'bold', border: '1px solid rgba(168, 85, 247, 0.3)' }}>
                            🎵 {ev.auditoryPerception?.moodVibe} (Energy: {ev.auditoryPerception?.energyLevel})
                        </span>
                        <span style={{ background: 'rgba(255, 255, 255, 0.1)', color: '#cbd5e1', padding: '4px 10px', borderRadius: '6px', fontSize: '0.8rem' }}>
                            适配槽位: {ev.bestMatchedSlot}
                        </span>
                      </div>
                      
                      <div style={{ color: '#f1f5f9', fontSize: '0.95rem', lineHeight: 1.6, background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>
                        <div style={{ marginBottom: '8px' }}><strong>声学特征:</strong> {ev.auditoryPerception?.acousticFeatures?.join(', ')}</div>
                        <div style={{ color: '#94a3b8' }}><strong>推荐画面:</strong> {ev.recommendedMaterial}</div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Mix Defaults */}
            <div style={{ flex: 1, background: 'rgba(30, 41, 59, 0.5)', borderRadius: '16px', padding: '24px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <h3 style={{ margin: '0 0 16px 0', color: '#f472b6', fontSize: '1rem' }}>混音默认建议 (Mix Defaults)</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <MetricCard label="Default Mix Level" value={bgmData.mixDefaults?.defaultMixLevel} />
                <MetricCard label="Fade In Frames" value={bgmData.mixDefaults?.fadeInFrames} />
                <MetricCard label="Fade Out Frames" value={bgmData.mixDefaults?.fadeOutFrames} />
                <MetricCard label="Ducking Ratio" value={bgmData.mixDefaults?.duckingRatio} />
                <MetricCard label="Ducking Recommended" value={bgmData.mixDefaults?.duckingRecommended ? 'Yes' : 'No'} />
                <MetricCard label="Loop Recommended" value={bgmData.mixDefaults?.loopRecommended ? 'Yes' : 'No'} />
              </div>
            </div>
          </div>
        </div>
      )}

      <input ref={fileInputRef} type="file" accept="audio/*" style={{ display: 'none' }} onChange={handleFileSelect} />
      <audio ref={audioRef} onEnded={() => setPlayingId(null)} style={{ display: 'none' }} />
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: any }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>{label}</div>
      <div style={{ fontSize: '1.1rem', color: '#f8fafc', fontWeight: 600 }}>{value === undefined || value === null ? '--' : value}</div>
    </div>
  );
}
