import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';

export default function CreateProjectGeneration() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const projectId = id || '';

  const [projectTitle, setProjectTitle] = useState('');
  const [projectStatus, setProjectStatus] = useState('DRAFT');
  
  const [isGenerating, setIsGenerating] = useState(false);
  const [progress, setProgress] = useState(0);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!projectId || projectId === 'new') {
      navigate('/create', { replace: true });
      return;
    }
    loadProject();
    checkInitialStatus();
    
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [projectId]);

  const loadProject = async () => {
    if (!projectId) return;
    try {
      const resp = await creationApi.getProject(projectId);
      setProjectTitle(resp.data?.title || '');
      setProjectStatus(resp.data?.status || 'DRAFT');
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

  const startGeneration = async (forceRegenerate = false) => {
    if (!projectId) return;
    setIsGenerating(true);
    setProgress(0);
    setErrorMsg(null);
    setVideoUrl(null);
    try {
      if (forceRegenerate) {
        await creationApi.regenerateVideo(projectId);
        showToast('已清除旧缓存，重新交给大模型编排生成', 'success');
      } else {
        await creationApi.generateVideo(projectId);
        showToast('视频生成任务已提交，大模型正在思考...', 'success');
      }
      startPolling();
    } catch (error: any) {
      showToast(error?.message || '提交生成失败', 'error');
      setIsGenerating(false);
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

  const handleStatusResponse = (data: { status: string; progress: number; outputPath?: string; error?: string }) => {
    if (data.status === 'RENDERING') {
      setIsGenerating(true);
      setProgress(data.progress || 0.1);
    } else if (data.status === 'DONE') {
      setIsGenerating(false);
      setProgress(1);
      if (timerRef.current) clearInterval(timerRef.current);
      // Construct local URL using remotion-service's output
      setVideoUrl(`http://localhost:3001/out/${projectId}.mp4`);
      setProjectStatus('DONE');
      showToast('视频渲染完成！', 'success');
    } else if (data.status === 'FAILED') {
      setIsGenerating(false);
      setProgress(0);
      setErrorMsg(data.error || '渲染过程中发生错误');
      if (timerRef.current) clearInterval(timerRef.current);
      setProjectStatus('FAILED');
      showToast('视频生成失败', 'error');
    } else if (data.status === 'QUEUED' || data.status === 'GENERATING') {
      setIsGenerating(true);
      if (progress === 0) setProgress(0.05);
    }
  };

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate(`/create/detail/${projectId}/gap-detection`)} title="返回适配阶段">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作工作流 · 智能生成 (P3)</h2>
        </div>
      </div>

      <div style={{ padding: '14px 32px 0', color: '#64748b', fontSize: '0.9rem' }}>
        项目：<strong style={{ color: '#0f172a' }}>{projectTitle || '未命名项目'}</strong> · 状态：{projectStatus}
      </div>

      <div className="detail-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: '20px' }}>
        
        <div className="result-card" style={{ width: '80%', maxWidth: '800px', padding: '40px', textAlign: 'center' }}>
          <h3 style={{ marginBottom: '20px', color: '#1e293b' }}>
            🎉 准备就绪，一键召唤大模型导演
          </h3>
          <p style={{ color: '#64748b', marginBottom: '30px', fontSize: '0.95rem' }}>
            AI 将根据您前序阶段选定的模板结构、补充的素材以及适配策略，为您自动编排视频时间轴，并驱动渲染引擎进行极速出片。
          </p>

          {!isGenerating && !videoUrl && (
            <button 
              className="btn-primary" 
              style={{ padding: '12px 24px', fontSize: '1.1rem', borderRadius: '8px' }}
              onClick={() => startGeneration()}
            >
              一键生成视频
            </button>
          )}

          {isGenerating && (
            <div style={{ marginTop: '20px', width: '100%' }}>
              <div style={{ fontSize: '0.9rem', color: '#3b82f6', marginBottom: '10px', fontWeight: 500 }}>
                {progress < 0.1 ? '大模型正在编排剧本...' : '引擎正在渲染画面...'} 
                {Math.floor(progress * 100)}%
              </div>
              <div style={{ width: '100%', height: '12px', background: '#e2e8f0', borderRadius: '6px', overflow: 'hidden' }}>
                <div 
                  style={{ 
                    height: '100%', 
                    background: '#3b82f6', 
                    width: `${Math.max(5, progress * 100)}%`,
                    transition: 'width 0.5s ease-out'
                  }}
                />
              </div>
            </div>
          )}

          {errorMsg && (
            <div style={{ marginTop: '20px', padding: '16px', background: '#fef2f2', color: '#ef4444', borderRadius: '8px', border: '1px solid #fca5a5' }}>
              <strong>渲染失败: </strong> {errorMsg}
            </div>
          )}

          {videoUrl && (
            <div style={{ marginTop: '30px' }}>
              <h4 style={{ marginBottom: '16px', color: '#059669' }}>🎬 渲染成功！您的专属视频已出炉：</h4>
              <div style={{ 
                border: '4px solid #1e293b', 
                borderRadius: '12px', 
                overflow: 'hidden', 
                display: 'inline-block',
                background: '#000',
                boxShadow: '0 10px 25px rgba(0,0,0,0.2)'
              }}>
                <video 
                  src={videoUrl} 
                  controls 
                  autoPlay 
                  style={{ width: '100%', maxWidth: '360px', maxHeight: '640px', display: 'block' }} 
                />
              </div>
              
              <div style={{ marginTop: '20px' }}>
                <button 
                  className="btn-outline" 
                  onClick={() => startGeneration(true)}
                  style={{ fontSize: '0.9rem' }}
                >
                  重新生成
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
