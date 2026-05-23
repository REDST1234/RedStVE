import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/ToastContext';
import { deconstructApi } from '../../api/deconstruct';
import { videoApi } from '../../api/video';
import { formatBytes, extractFileName, formatDuration } from '../../utils/format';
import { Project, UploadedFileView, VideoTaskResultData } from '../../types';

export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  
  
  // States that were in App.tsx
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [deconstructStep, setDeconstructStep] = useState<'info' | 'upload' | 'processing' | 'result'>('info');
  const [extractProgress, setExtractProgress] = useState(0);
  const [extractStageIndex, setExtractStageIndex] = useState(0);
  const [isDebugMode, setIsDebugMode] = useState(true);
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFileView[]>([]);
  const [uploading, setUploading] = useState(false);
  const [triggeringDebugAsr, setTriggeringDebugAsr] = useState(false);
  const [debugAsrTriggeredTaskIds, setDebugAsrTriggeredTaskIds] = useState<string[]>([]);
  const [deletingMaterialIds, setDeletingMaterialIds] = useState<string[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [liveTaskResult, setLiveTaskResult] = useState<VideoTaskResultData | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const latestMediaInfo = uploadedFiles[0]?.mediaInfo || null;

  const [projectTitle, setProjectTitle] = useState('');
  const [projectDesc, setProjectDesc] = useState('');
  const [projectTags, setProjectTags] = useState<string[]>(['混剪']);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const PROCESSING_STEPS = [
    { label: '初始化 FFmpeg 媒体探测器...', progress: 10 },
    { label: '运行 ASR 语音转写与 SceneDetector 镜头切分...', progress: 40 },
    { label: '抽取 Hook 关键帧与检测物理异动...', progress: 60 },
    { label: 'TimelineMatcher 多模态时间轴归一化...', progress: 80 },
    { label: '调用 LLM 深度分析脚本、节奏与包装结构...', progress: 95 },
    { label: '解析与特征库比对完成', progress: 100 }
  ];

  // API Load Logic
  useEffect(() => {
    if (!id || id === 'new') return;
    let cancelled = false;

    const loadData = async () => {
      try {
        const resp = await deconstructApi.getProject(id);
        if (resp.code !== '0' && resp.code !== '200') throw new Error(resp.message || '获取详情失败');
        if (cancelled) return;
        
        const data = resp.data;
        const proj: Project = {
          id: data.id,
          title: data.title,
          description: data.description || '',
          tags: data.tags || [],
          cover: data.coverUrl || '',
          status: data.status === 'COMPLETED' ? 'completed' : data.status === 'PENDING' ? 'pending' : 'working',
          date: data.createdAt ? data.createdAt.split('T')[0] : ''
        };
        setEditingProject(proj);
        
        if (data.materials && data.materials.length > 0) {
          const files = data.materials.map(m => ({
            materialBizId: m.materialBizId || '',
            name: m.originalFileName || extractFileName(m.filePath),
            size: '--',
            taskId: m.taskId || '',
            mediaInfo: { duration: m.duration, width: m.width, height: m.height, format: m.format }
          }));
          setUploadedFiles(files);
        }
      } catch (err) {
        console.warn('获取详情失败', err);
      }
    };
    void loadData();
    return () => { cancelled = true; };
  }, [id]);

  // Async extract simulation
  useEffect(() => {
    if (deconstructStep === 'processing' && !isDebugMode) {
      if (extractStageIndex < PROCESSING_STEPS.length - 1) {
        const timer = setTimeout(() => {
          setExtractStageIndex(prev => prev + 1);
          setExtractProgress(PROCESSING_STEPS[extractStageIndex + 1].progress);
        }, 1500);
        return () => clearTimeout(timer);
      } else {
        const timer = setTimeout(() => setDeconstructStep('result'), 1000);
        return () => clearTimeout(timer);
      }
    }
  }, [deconstructStep, extractStageIndex, isDebugMode]);
  // Polling logic for real data
  useEffect(() => {
    if (deconstructStep === 'processing' && uploadedFiles.length > 0) {
      const taskId = uploadedFiles[0].taskId;
      if (!taskId) return;

      const fetchResult = async () => {
        try {
          const resp = await videoApi.getTaskResult(taskId, true);
          if (resp.code === '0' || resp.code === '200') {
            setLiveTaskResult(resp.data);
            if (resp.data.status === 'COMPLETED' || resp.data.status === 'FAILED') {
              // Debug 模式下不自动进入结果页，保持“执行下一步”可控。
              if (!isDebugMode && resp.data.status === 'COMPLETED') {
                setDeconstructStep('result');
              }
            }
          }
        } catch (error) {
          console.warn('Failed to poll task result', error);
        }
      };

      fetchResult(); // Initial fetch
      const intervalId = setInterval(fetchResult, 2000);

      return () => clearInterval(intervalId);
    }
  }, [deconstructStep, uploadedFiles, isDebugMode]);
  const toggleTag = (tag: string) => {
    setProjectTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]);
  };

  const handleSaveProject = async () => {
    if (!projectTitle.trim()) {
      showToast('请输入项目标题，不能为空', 'error');
      return;
    }
    setIsSaving(true);
    try {
      const payload = {
        title: projectTitle,
        description: projectDesc,
        tags: projectTags,
        coverUrl: editingProject?.cover || 'https://images.unsplash.com/photo-1611162617474-5b21e879e113?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80'
      };
      
      try {
        const resp = editingProject 
          ? await deconstructApi.updateProject(editingProject.id, payload)
          : await deconstructApi.createProject(payload);
          
        if (resp.code !== '0' && resp.code !== '200') {
           throw new Error(resp.message || '保存失败');
        }
        showToast('✨ 项目保存成功', 'success');
      } catch (err) {
        console.warn('API 未就绪，走 Mock 保存逻辑', err);
        showToast('✨ 项目保存成功 (Mock)', 'success');
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteProject = async () => {
    if (!editingProject) return;
    setIsDeleting(true);
    try {
      try {
        const resp = await deconstructApi.deleteProject(editingProject.id);
        if (resp.code !== '0' && resp.code !== '200') {
           throw new Error(resp.message || '删除失败');
        }
      } catch (err) {
        console.warn('API 未就绪，走 Mock 删除逻辑', err);
      }
      setShowDeleteConfirm(false);
      navigateToList();
    } finally {
      setIsDeleting(false);
    }
  };

  const navigateToList = () => navigate('/deconstruct');

  const handleStartExtraction = () => {
    setDeconstructStep('processing');
    setExtractStageIndex(0);
    setExtractProgress(PROCESSING_STEPS[0].progress);
    // 重新开始时重置调试态，避免沿用上一轮任务触发记录。
    setDebugAsrTriggeredTaskIds([]);
    setLiveTaskResult(null);
  };

  const handleNextDebugStep = () => {
    void runNextDebugStep();
  };

  const runNextDebugStep = async () => {
    if (extractStageIndex < PROCESSING_STEPS.length - 1) {
      const nextStageIndex = extractStageIndex + 1;
      const nextProgress = PROCESSING_STEPS[nextStageIndex].progress;

      // 40% 对应 ASR 阶段：Debug 模式下由前端显式触发后端接口。
      if (nextProgress === 40 && uploadedFiles.length > 0) {
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
            showToast(`已触发 ${unTriggeredTaskIds.length} 个任务的 ASR Debug 分析`, 'success');
          } catch (error) {
            const message = error instanceof Error ? error.message : '触发 ASR Debug 分析失败';
            showToast(message, 'error');
            return;
          } finally {
            setTriggeringDebugAsr(false);
          }
        }
      }

      setExtractStageIndex(nextStageIndex);
      setExtractProgress(nextProgress);
      return;
    }
    setDeconstructStep('result');
  };

  const handleSaveAndReturn = () => navigateToList();

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      void uploadSelectedFiles(Array.from(files));
    }
  };

  const handleDropUpload = async (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (uploading) return;
    const files = e.dataTransfer.files;
    const videoFiles = Array.from(files).filter(f => f.type.startsWith('video/') || f.name.endsWith('.mp4') || f.name.endsWith('.mov'));
    if (videoFiles.length === 0) {
      showToast('请上传有效的视频文件', 'error');
      return;
    }
    await uploadSelectedFiles(videoFiles);
  };

  const openFilePicker = () => {
    if (!uploading) fileInputRef.current?.click();
  };

  const uploadSelectedFiles = async (files: File[]) => {
    if (files.length === 0) return;
    setUploadError(null);
    setUploading(true);
    try {
      if (files.length === 1) {
        const resp = await videoApi.uploadSingle(files[0], editingProject?.id, isDebugMode);
        if (resp.code !== '0') throw new Error(resp.message || '上传失败');
        
        const item: UploadedFileView = {
          materialBizId: resp.data.materialBizId,
          name: files[0].name,
          size: formatBytes(files[0].size),
          taskId: resp.data.taskId,
          mediaInfo: resp.data.mediaInfo
        };
        setUploadedFiles(prev => [...prev, item]);
        return;
      }

      const resp = await videoApi.uploadBatch(files, editingProject?.id, isDebugMode);
      if (resp.code !== '0') throw new Error(resp.message || '批量上传失败');
      
      const taskIds = resp.data.taskIds || [];
      const mediaInfos = resp.data.mediaInfos || [];
      const responseItems = resp.data.items || [];
        const items = files.map((file, index) => ({
        materialBizId: responseItems[index]?.materialBizId || '',
        name: file.name,
        size: formatBytes(file.size),
        taskId: responseItems[index]?.taskId || taskIds[index] || '',
        mediaInfo: responseItems[index]?.mediaInfo || mediaInfos[index] || {}
      }));
      setUploadedFiles(prev => [...prev, ...items]);
    } catch (error) {
      const message = error instanceof Error ? error.message : '上传失败，请重试';
      setUploadError(message);
      showToast(message, 'error');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteUploadedFile = async (materialBizId: string) => {
    if (!materialBizId.trim()) {
      showToast('素材ID无效，无法删除', 'error');
      return;
    }
    if (deletingMaterialIds.includes(materialBizId)) return;
    
    setDeletingMaterialIds((prev) => [...prev, materialBizId]);
    setUploadError(null);
    try {
      const resp = await videoApi.deleteMaterial(materialBizId);
      if (resp.code !== '0' && resp.code !== '200') throw new Error(resp.message || '删除素材失败');

      setUploadedFiles((prev) => {
        const next = prev.filter((item) => item.materialBizId !== materialBizId);
        return next;
      });
      showToast('素材已删除', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '删除素材失败';
      setUploadError(message);
      showToast(message, 'error');
    } finally {
      setDeletingMaterialIds((prev) => prev.filter((id) => id !== materialBizId));
    }
  };

  return (
    <>
      <div className="detail-page fade-in" style={{borderColor: '#e0e7ff', display: 'flex', flexDirection: 'row', gap: '20px', padding: '24px', height: 'calc(100vh - 80px)' }}>
              
              {/* === 左侧主工作流 === */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'auto', paddingRight: '12px' }}>
                <div className="detail-header" style={{ padding: '0 0 20px 0' }}>
                  <button className="back-btn" onClick={() => {
                    if (deconstructStep !== 'info') {
                      setDeconstructStep('info');
                    } else {
                      navigateToList();
                    }
                  }} title="返回项目看板">
                    <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
                    </svg>
                  </button>
                  <h2 className="detail-title">{editingProject ? '编辑拆解详情' : '新建结构拆解项目'}</h2>
                  
                  {deconstructStep === 'info' && (
                    <div style={{ marginLeft: 'auto', display: 'flex', gap: '12px', alignItems: 'center' }}>
                      {editingProject && (
                        <button className="btn-outline" style={{ borderColor: '#ef4444', color: '#ef4444' }} onClick={() => setShowDeleteConfirm(true)} disabled={isDeleting}>
                          {isDeleting ? '删除中...' : '🗑️ 删除项目'}
                        </button>
                      )}
                      <button className="btn-outline" onClick={handleSaveProject} disabled={isSaving}>
                        {isSaving ? '保存中...' : '💾 保存项目'}
                      </button>
                      <button className="btn-jump-flat" style={{background: '#6366f1', color: 'white', borderColor: '#4f46e5'}} onClick={() => setDeconstructStep('upload')} title="进入提取工作流">
                        <span>🚀 开始拆解工作流</span>
                      </button>
                    </div>
                  )}
                </div>

                <div className="detail-body" style={{ padding: 0 }}>
                  {deconstructStep === 'info' && (
                    <>
                      <div className="detail-left">
                        <div className="cover-preview">
                          {editingProject?.cover ? (
                            <img src={editingProject.cover} alt="cover" />
                          ) : (
                            <div className="cover-placeholder">
                              <svg width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                              <span>暂无封面</span>
                            </div>
                          )}
                        </div>
                        
                        <div className="params-box">
                          <div className="params-title">详细参数 (Details)</div>
                          <div className="param-row"><span className="param-label">时长</span><span className="param-val">{formatDuration(latestMediaInfo?.duration)}</span></div>
                          <div className="param-row"><span className="param-label">分辨率</span><span className="param-val">{latestMediaInfo?.width && latestMediaInfo?.height ? `${latestMediaInfo.width} x ${latestMediaInfo.height}` : '---- x ----'}</span></div>
                          <div className="param-row"><span className="param-label">FPS</span><span className="param-val">{latestMediaInfo?.fps ? latestMediaInfo.fps.toFixed(3) : '--'}</span></div>
                          <div className="param-row"><span className="param-label">镜头数</span><span className="param-val">0</span></div>
                        </div>
                      </div>

                      <div className="detail-mid" style={{flexDirection: 'row', gap: '32px'}}>
                        <div className="input-group" style={{flex: 1}}>
                          <div className="input-group">
                            <label className="input-label">项目标题 (Title)</label>
                            <input type="text" className="input-field" placeholder="输入爆款视频解析项目标题..." value={projectTitle} onChange={e => setProjectTitle(e.target.value)} />
                          </div>
                          
                          <div className="input-group" style={{marginTop: '24px'}}>
                            <label className="input-label">项目描述 (Description)</label>
                            <textarea className="input-field" placeholder="记录提取与结构拆解的核心目标..." value={projectDesc} onChange={e => setProjectDesc(e.target.value)}></textarea>
                          </div>

                          <div className="input-group" style={{marginTop: '24px'}}>
                            <label className="input-label">封面地址 (Cover URL)</label>
                            <input
                              type="text"
                              className="input-field"
                              placeholder="支持 http(s)://、/local/path、./relative/path、D:\\images\\cover.jpg"
                              value={editingProject?.cover || ''}
                              onChange={e => setEditingProject(prev => prev ? {...prev, cover: e.target.value} : null)}
                            />
                          </div>

                          <div className="input-group" style={{marginTop: '24px'}}>
                            <label className="input-label">品类标签 (Tags)</label>
                            <div className="tags-container">
                              {['混剪', '营销', '影视', '从零', '电商', 'Vlog'].map(tag => (
                                <div key={tag} onClick={() => toggleTag(tag)} className={`tag-btn ${projectTags.includes(tag) ? 'selected' : ''}`} style={{ cursor: 'pointer' }}>
                                  {tag}
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>

                        {/* 📜 多路归一日志 (Timeline Log) */}
                        <div className="timeline-log-panel">
                          <div className="log-header">
                            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                            样例多路归一日志 (Timeline Log)
                          </div>
                          <div className="log-body">
                            <div className="log-row">
                              <span className="log-time">[0.0s - 3.2s]</span>
                              <div className="log-badge asr">🔵 ASR台词</div>
                              <span className="log-text">“大一早八顶不住？那是...”</span>
                            </div>
                            <div className="log-row">
                              <span className="log-time">[1.2s]</span>
                              <div className="log-badge physics">🟠 物理异动</div>
                              <span className="log-text highlight">检测到像素突变 + 转场音效波峰</span>
                            </div>
                            <div className="log-row">
                              <span className="log-time">[3.2s - 12.0s]</span>
                              <div className="log-badge asr">🔵 ASR台词</div>
                              <span className="log-text">“纯正手作咖啡，10秒瞬间让你清醒回到巅峰状态！”</span>
                            </div>
                            <div className="log-row">
                              <span className="log-time">[8.5s]</span>
                              <div className="log-badge physics">🟠 物理异动</div>
                              <span className="log-text">人物骨骼大幅度变化 (挥手动作)</span>
                            </div>
                            <div className="log-row">
                              <span className="log-time">[12.0s - 15.0s]</span>
                              <div className="log-badge asr">🔵 ASR台词</div>
                              <span className="log-text">“点击下方链接，享受专属于你的清晨...”</span>
                            </div>
                            <div className="log-row">
                              <span className="log-time">[13.5s]</span>
                              <div className="log-badge physics">🟠 物理异动</div>
                              <span className="log-text highlight">购物车弹框 UI 识别提取</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </>
                  )}

                  {deconstructStep === 'upload' && (
                    <div className="extract-view upload-view fade-in">
                      <div className="extract-header">
                        <h3>步骤 1：上传视频素材</h3>
                        <p>请上传需要进行结构拆解和信息提取的原始视频文件，支持批量上传进行批次任务分析。</p>
                      </div>
                      
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".mp4,.mov,video/mp4,video/quicktime"
                        multiple
                        style={{ display: 'none' }}
                        onChange={handleFileInputChange}
                      />

                      <div
                        className="upload-dropzone large"
                        onClick={openFilePicker}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={handleDropUpload}
                      >
                        <div className="upload-icon">
                          <svg width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                        </div>
                        <div className="upload-text">点击或拖拽视频文件到此区域</div>
                        <div className="upload-subtext">支持 MP4, MOV 格式，单文件上限 2GB</div>
                        {uploading && <div className="upload-subtext" style={{marginTop: '8px'}}>上传并探测中...</div>}
                      </div>

                      {uploadError && (
                        <div style={{ marginTop: '12px', color: '#dc2626', fontSize: '0.9rem' }}>
                          {uploadError}
                        </div>
                      )}

                      {uploadedFiles.length > 0 && (
                        <div className="uploaded-list">
                          <h4>已准备好进行提取的文件</h4>
                          {uploadedFiles.map((f, i) => (
                            <div className="uploaded-item has-tooltip" key={`${f.materialBizId}-${f.taskId}-${i}`} style={{ position: 'relative' }}>
                              <svg width="24" height="24" fill="none" stroke="#6366f1" strokeWidth="2" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                              <span className="file-name">{f.name}</span>
                              <span className="file-size">{f.size}</span>
                              <button
                                type="button"
                                className="btn-outline"
                                disabled={uploading || !f.materialBizId || deletingMaterialIds.includes(f.materialBizId)}
                                onClick={() => void handleDeleteUploadedFile(f.materialBizId)}
                                style={{ marginLeft: '12px', padding: '4px 10px', fontSize: '0.8rem' }}
                              >
                                {deletingMaterialIds.includes(f.materialBizId) ? '删除中...' : '删除素材'}
                              </button>

                              {f.mediaInfo && (
                                <div className="ffprobe-tooltip">
                                  <div style={{ fontWeight: 600, marginBottom: '8px', color: '#334155' }}>FFprobe 基础信息</div>
                                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '0.9rem', color: '#475569' }}>
                                    <div>时长: {f.mediaInfo.duration ? formatDuration(f.mediaInfo.duration) : '--'}</div>
                                    <div>分辨率: {f.mediaInfo.width && f.mediaInfo.height ? `${f.mediaInfo.width}x${f.mediaInfo.height}` : '--'}</div>
                                    <div>FPS: {f.mediaInfo.fps ? f.mediaInfo.fps.toFixed(3) : '--'}</div>
                                    <div>编码: {f.mediaInfo.codec || '--'}</div>
                                  </div>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="extract-footer">
                        <label className="debug-toggle">
                          <input type="checkbox" checked={isDebugMode} onChange={(e) => setIsDebugMode(e.target.checked)} />
                          <span>开启调试模式 (使用原始 JSON 展出)</span>
                        </label>
                        <button className="btn-primary" disabled={uploadedFiles.length === 0 || uploading} onClick={handleStartExtraction}>开始提取视频</button>
                      </div>
                    </div>
                  )}

                  {deconstructStep === 'processing' && (
                    <div className="extract-view processing-view fade-in" style={{ flex: 1, minHeight: '500px' }}>
                      <div className="processing-dashboard">
                        <div className="progress-circle">
                          <svg viewBox="0 0 36 36" className="circular-chart">
                            <path className="circle-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                            <path className="circle" strokeDasharray={`${extractProgress}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                          </svg>
                          <div className="percentage">{extractProgress}%</div>
                        </div>
                        
                        <div className="processing-terminal">
                          <div className="terminal-header">
                            <span className="dot red"></span><span className="dot yellow"></span><span className="dot green"></span>
                            <span className="title">AI 提取服务终端</span>
                          </div>
                          <div className="terminal-body">
                            {PROCESSING_STEPS.slice(0, extractStageIndex + 1).map((step, idx) => (
                              <div key={idx} className={`terminal-line ${idx === extractStageIndex ? 'active' : 'done'}`}>
                                <span className="prompt">$ </span>
                                <span className="command">{step.label}</span>
                                {idx < extractStageIndex && <span className="status"> [OK]</span>}
                              </div>
                            ))}
                            <div className="cursor"></div>
                          </div>
                        </div>

                        {isDebugMode && (
                          <div className="debug-actions" style={{ display: 'flex', gap: '12px' }}>
                            <button
                              className="btn-outline"
                              onClick={handleNextDebugStep}
                              disabled={triggeringDebugAsr}
                            >
                              {triggeringDebugAsr ? '触发ASR中...' : '执行下一步 (Debug)'}
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {deconstructStep === 'result' && (
                    <div className="extract-view result-view fade-in">
                      <div className="result-header">
                        <h3>✅ 提取分析完成</h3>
                        <p>多维解构模型已成功分离视频的基础信息、镜头、文本与三维结构。</p>
                      </div>

                      {!isDebugMode ? (
                        <div className="result-dashboard">
                          {/* Left Pane: Media & Timeline */}
                          <div className="result-left">
                            <div className="result-card media-info">
                              <h4>基础元信息 (Media Prober)</h4>
                              <div className="info-grid">
                                <div className="info-item"><span>时长</span><strong>30.5s</strong></div>
                                <div className="info-item"><span>分辨率</span><strong>1080x1920</strong></div>
                                <div className="info-item"><span>帧率</span><strong>30 FPS</strong></div>
                                <div className="info-item"><span>镜头数</span><strong>8 个切片</strong></div>
                              </div>
                            </div>
                            
                            <div className="result-card timeline-mini">
                              <h4>多模态时序树 (Timeline)</h4>
                              <div className="log-body mini">
                                <div className="log-row"><span className="log-time">[0.0s]</span><span className="log-text">ASR: 大一早八顶不住？</span></div>
                                <div className="log-row"><span className="log-time">[1.2s]</span><span className="log-text highlight">物理异动: 像素突变</span></div>
                                <div className="log-row"><span className="log-time">[3.2s]</span><span className="log-text">ASR: 纯正手作咖啡瞬间...</span></div>
                              </div>
                            </div>
                          </div>

                          {/* Right Pane: LLM Structures */}
                          <div className="result-right">
                            <div className="result-card llm-structure">
                              <h4>LLM 三维结构分析报告</h4>
                              
                              <div className="structure-section script">
                                <h5>📜 脚本结构 (Script)</h5>
                                <div className="struct-item"><strong>Hook (0-3s):</strong> 抛出痛点问题（高燃节奏）</div>
                                <div className="struct-item"><strong>中段展开 (3-15s):</strong> 痛点放大 + 卖点反转</div>
                                <div className="struct-item"><strong>结尾 CTA (15s-):</strong> 引导点击购物车</div>
                              </div>
                              
                              <div className="structure-section rhythm">
                                <h5>⏱️ 节奏结构 (Rhythm)</h5>
                                <div className="struct-item"><strong>切换频率:</strong> 快节奏 (2.5次/秒)</div>
                                <div className="struct-item"><strong>高潮区间:</strong> 8.5s - 12.0s</div>
                              </div>
                              
                              <div className="structure-section packaging">
                                <h5>🎨 包装结构 (Packaging)</h5>
                                <div className="struct-item"><strong>字幕风格:</strong> 居中大字，带弹跳入场</div>
                                <div className="struct-item"><strong>转场偏好:</strong> 缩放转场 (Zoom-in) 为主</div>
                              </div>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="result-debug-message" style={{ padding: '48px 24px', background: '#f8fafc', borderRadius: '12px', color: '#64748b', textAlign: 'center', border: '1px dashed #cbd5e1' }}>
                          <svg width="48" height="48" fill="none" stroke="#94a3b8" strokeWidth="1.5" viewBox="0 0 24 24" style={{ marginBottom: '16px' }}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                          <div style={{ fontSize: '1.1rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>调试模式已开启</div>
                          <div>提取分析结果已在右侧 JSON 视图中展出，可供前端逻辑绑定参考。</div>
                        </div>
                      )}
                      
                      <div className="extract-footer">
                        <button className="btn-outline" onClick={() => setDeconstructStep('info')}>返回详情页</button>
                        <button className="btn-primary" onClick={handleSaveAndReturn}>保存结构化模板</button>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* === 右侧常驻的 JSON 大盘 (Debug 模式下专属) === */}
              {isDebugMode && deconstructStep !== 'info' && (
                <div className="debug-json-panel fade-in" style={{ width: '420px', background: '#0f172a', borderRadius: '12px', padding: '20px', display: 'flex', flexDirection: 'column', color: '#e2e8f0', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)', flexShrink: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px', borderBottom: '1px solid #334155', paddingBottom: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <svg width="20" height="20" fill="none" stroke="#38bdf8" strokeWidth="2" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
                      <h3 style={{ fontSize: '1.05rem', margin: 0, color: '#f8fafc' }}>Extraction JSON (Live)</h3>
                    </div>
                    <span style={{ fontSize: '0.8rem', color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 8px', borderRadius: '12px' }}>
                      {deconstructStep === 'result' ? 'Finished' : 'Syncing'}
                    </span>
                  </div>
                  
                  <div style={{ flex: 1, overflow: 'auto', fontSize: '0.85rem', fontFamily: 'Consolas, monospace', color: '#38bdf8', lineHeight: '1.6' }}>
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(liveTaskResult || { status: 'idle', message: 'Waiting for start...' }, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </div>
            
      {showDeleteConfirm && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3>⚠️ 确认删除项目</h3>
            <p>删除后相关的所有分析数据、时间轴切片将被永久清除。此操作不可撤销。</p>
            <div className="modal-actions">
              <button className="btn-outline" onClick={() => setShowDeleteConfirm(false)} disabled={isDeleting}>取消</button>
              <button className="btn-primary" style={{background: '#ef4444', borderColor: '#ef4444'}} onClick={handleDeleteProject} disabled={isDeleting}>
                {isDeleting ? '删除中...' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
