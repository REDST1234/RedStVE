import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { CreationAssetData } from '../../types';
import { extractFileName, formatBytes, formatDuration } from '../../utils/format';
import { ProjectCreationTabs } from '../../components/ProjectCreationTabs';

export default function CreateProjectWorkflow() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const projectId = id || '';

  const [projectTitle, setProjectTitle] = useState('');
  const [projectStatus, setProjectStatus] = useState('DRAFT');

  const [assets, setAssets] = useState<CreationAssetData[]>([]);
  const [textContent, setTextContent] = useState('');

  const [loadingAssets, setLoadingAssets] = useState(false);
  const [uploadingType, setUploadingType] = useState<'VIDEO' | 'IMAGE' | 'TEXT' | null>(null);
  const [confirmingAssets, setConfirmingAssets] = useState(false);
  const [deletingAssetId, setDeletingAssetId] = useState<string | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<CreationAssetData | null>(null);

  const groupedAssets = useMemo(() => {
    const video = assets.filter((a) => a.materialType === 'VIDEO');
    const image = assets.filter((a) => a.materialType === 'IMAGE');
    const text = assets.filter((a) => a.materialType === 'TEXT');
    return { video, image, text };
  }, [assets]);

  const hasAnyAssets = assets.length > 0;
  const hasProfilingStarted = assets.some((item) => (item.status || '').toUpperCase() !== 'UPLOADED');
  const hasProfilingInProgress = assets.some((item) => (item.status || '').toUpperCase() === 'PROFILING');
  useEffect(() => {
    if (!projectId || projectId === 'new') {
      navigate('/create/detail/new', { replace: true });
      return;
    }
    void Promise.all([loadProject(), loadAssets()]);
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

  const loadAssets = async (showSuccessToast = false) => {
    if (!projectId) return;
    setLoadingAssets(true);
    try {
      const resp = await creationApi.listAssets(projectId);
      setAssets(resp.data || []);
      if (showSuccessToast) {
        showToast('素材列表已刷新', 'success');
      }
    } catch (error: any) {
      showToast(error?.message || '加载素材失败', 'error');
    } finally {
      setLoadingAssets(false);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>, type: 'VIDEO' | 'IMAGE') => {
    const file = e.target.files?.[0];
    if (!file || !projectId) return;
    setUploadingType(type);
    try {
      await creationApi.uploadAsset(projectId, type, { file });
      await loadAssets();
      showToast(`${type === 'VIDEO' ? '视频' : '图片'}素材上传成功`, 'success');
    } catch (error: any) {
      showToast(error?.message || '上传失败', 'error');
    } finally {
      setUploadingType(null);
      e.target.value = ''; // reset
    }
  };

  const uploadText = async () => {
    if (!projectId || !textContent.trim()) return;
    setUploadingType('TEXT');
    try {
      await creationApi.uploadAsset(projectId, 'TEXT', { textContent: textContent.trim() });
      setTextContent('');
      await loadAssets();
      showToast('文本素材上传成功', 'success');
    } catch (error: any) {
      showToast(error?.message || '文本上传失败', 'error');
    } finally {
      setUploadingType(null);
    }
  };

  const confirmAssets = async (type?: 'VIDEO' | 'IMAGE' | 'TEXT') => {
    if (!projectId || !hasAnyAssets) return;
    setConfirmingAssets(true);
    try {
      const resp = await creationApi.confirmAssets(projectId, type);
      await loadAssets();
      showToast(`素材确认完成，已触发 ${resp.data?.triggeredCount ?? 0} 个异步解析任务`, 'success');
    } catch (error: any) {
      showToast(error?.message || '确认素材失败', 'error');
    } finally {
      setConfirmingAssets(false);
    }
  };

  const deleteAsset = async (materialBizId: string) => {
    if (!projectId || !materialBizId) return;
    if (!window.confirm('确认删除该素材吗？将执行逻辑删除并物理删除磁盘文件。')) {
      return;
    }
    setDeletingAssetId(materialBizId);
    try {
      await creationApi.deleteAsset(projectId, materialBizId);
      if (selectedAsset?.materialBizId === materialBizId) {
        setSelectedAsset(null);
      }
      await loadAssets();
      showToast('素材已删除', 'success');
    } catch (error: any) {
      showToast(error?.message || '删除素材失败', 'error');
    } finally {
      setDeletingAssetId(null);
    }
  };

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between', borderBottom: 'none', paddingBottom: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate('/create')} title="返回项目列表">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作工作流 · 素材阶段</h2>
        </div>
      </div>
      <ProjectCreationTabs projectId={projectId} activeTab="workflow" />

      <div style={{ padding: '14px 32px 0', color: '#64748b', fontSize: '0.9rem' }}>
        项目：<strong style={{ color: '#0f172a' }}>{projectTitle || '未命名项目'}</strong> · 状态：{projectStatus}
      </div>

      <div className="detail-body" style={{ display: 'grid', gridTemplateColumns: '1.65fr 1fr', gap: '16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div className="result-card">
            <h4>素材上传</h4>
            <div className="creation-upload-grid">
              <div className="creation-upload-panel">
                <div className="creation-upload-title">视频素材</div>
                <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '64px', border: '1px dashed #cbd5e1', borderRadius: '6px', cursor: 'pointer', background: '#f1f5f9', color: '#64748b', transition: 'all 0.2s' }}>
                  {uploadingType === 'VIDEO' ? <span style={{ fontSize: '0.9rem' }}>上传中...</span> : <span style={{ fontSize: '28px', fontWeight: 300 }}>+</span>}
                  <input type="file" accept="video/*" style={{ display: 'none' }} onChange={(e) => handleFileUpload(e, 'VIDEO')} disabled={uploadingType !== null} />
                </label>
              </div>

              <div className="creation-upload-panel">
                <div className="creation-upload-title">图片素材</div>
                <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '64px', border: '1px dashed #cbd5e1', borderRadius: '6px', cursor: 'pointer', background: '#f1f5f9', color: '#64748b', transition: 'all 0.2s' }}>
                  {uploadingType === 'IMAGE' ? <span style={{ fontSize: '0.9rem' }}>上传中...</span> : <span style={{ fontSize: '28px', fontWeight: 300 }}>+</span>}
                  <input type="file" accept="image/*" style={{ display: 'none' }} onChange={(e) => handleFileUpload(e, 'IMAGE')} disabled={uploadingType !== null} />
                </label>
              </div>

              <div className="creation-upload-panel">
                <div className="creation-upload-title">文本/卖点信息</div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <textarea
                    className="input-field"
                    style={{ flex: 1, height: '64px', minHeight: '64px', resize: 'none' }}
                    value={textContent}
                    onChange={(e) => setTextContent(e.target.value)}
                    placeholder="输入文案..."
                  />
                  <button 
                    style={{ width: '44px', height: '64px', padding: '0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '28px', fontWeight: 300, background: '#f1f5f9', color: '#64748b', border: '1px solid #cbd5e1', borderRadius: '6px', cursor: 'pointer' }} 
                    onClick={uploadText} 
                    disabled={!textContent.trim() || uploadingType !== null}
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="result-card" style={{ flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h4 style={{ marginBottom: 0 }}>素材资产池</h4>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn-primary" onClick={() => confirmAssets()} disabled={!hasAnyAssets || confirmingAssets}>
                  {confirmingAssets ? '确认中...' : '确认并提取素材信息'}
                </button>
                <button className="btn-outline" onClick={() => loadAssets(true)} disabled={loadingAssets}>
                  {loadingAssets ? '刷新中...' : '刷新素材'}
                </button>
              </div>
            </div>

            {!hasProfilingStarted && hasAnyAssets && (
              <div style={{ marginBottom: '10px', color: '#b45309', fontSize: '0.82rem' }}>
                当前仅上传未提取。点击“确认并提取素材信息”后才会触发异步解析。
              </div>
            )}
            {hasProfilingInProgress && (
              <div style={{ marginBottom: '10px', color: '#2563eb', fontSize: '0.82rem' }}>
                素材解析进行中，请等待状态变更。
              </div>
            )}

            <div className="creation-assets-columns">
              <AssetColumn title="视频" count={groupedAssets.video.length} items={groupedAssets.video} onDelete={deleteAsset} deletingAssetId={deletingAssetId} onAnalyze={() => confirmAssets('VIDEO')} isAnalyzing={confirmingAssets} selectedAssetId={selectedAsset?.materialBizId} onSelect={setSelectedAsset} />
              <AssetColumn title="图片" count={groupedAssets.image.length} items={groupedAssets.image} onDelete={deleteAsset} deletingAssetId={deletingAssetId} onAnalyze={() => confirmAssets('IMAGE')} isAnalyzing={confirmingAssets} selectedAssetId={selectedAsset?.materialBizId} onSelect={setSelectedAsset} />
              <AssetColumn title="文本/卖点信息" count={groupedAssets.text.length} items={groupedAssets.text} onDelete={deleteAsset} deletingAssetId={deletingAssetId} onAnalyze={() => confirmAssets('TEXT')} isAnalyzing={confirmingAssets} selectedAssetId={selectedAsset?.materialBizId} onSelect={setSelectedAsset} />
            </div>
          </div>
        </div>

        <div className="result-card" style={{ minHeight: '540px' }}>
          <h4>当前素材信息面板</h4>
          {selectedAsset ? (
            <div style={{ marginTop: '16px' }}>
              <div style={{ marginBottom: '16px', borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
                <div style={{ fontWeight: 'bold', color: '#0f172a', marginBottom: '4px' }}>
                  {extractFileName(selectedAsset.originalFileName || selectedAsset.filePath)}
                </div>
                <div style={{ fontSize: '0.82rem', color: '#64748b' }}>
                  状态: {selectedAsset.status}
                </div>
              </div>
              <ProfileDisplay profileJson={selectedAsset.profileJson} materialType={selectedAsset.materialType} textContent={selectedAsset.textContent} />
            </div>
          ) : (
            <div className="creation-material-placeholder">
              <div className="creation-material-placeholder-title">未选择素材</div>
              <div className="creation-material-placeholder-text">
                请在左侧列表中点击“查看”按钮，展示该素材的提取信息、画像与处理建议。
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function AssetColumn({
  title,
  count,
  items,
  onDelete,
  deletingAssetId,
  onAnalyze,
  isAnalyzing,
  selectedAssetId,
  onSelect
}: {
  title: string;
  count: number;
  items: CreationAssetData[];
  onDelete: (materialBizId: string) => void;
  deletingAssetId: string | null;
  onAnalyze: () => void;
  isAnalyzing: boolean;
  selectedAssetId?: string;
  onSelect: (item: CreationAssetData) => void;
}) {
  return (
    <div className="creation-asset-column">
      <div className="creation-asset-column-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>{title} ({count})</span>
        {count > 0 && (
          <button 
            className="btn-outline" 
            style={{ padding: '2px 6px', fontSize: '0.75rem' }} 
            onClick={onAnalyze}
            disabled={isAnalyzing}
          >
            分析{title}
          </button>
        )}
      </div>
      <div className="creation-asset-list">
        {items.map((item) => (
          <div 
            className="creation-asset-item" 
            key={item.materialBizId} 
            style={{ 
              border: selectedAssetId === item.materialBizId ? '1px solid #3b82f6' : '1px solid #e2e8f0',
              boxShadow: selectedAssetId === item.materialBizId ? '0 0 0 2px rgba(59,130,246,0.2)' : '0 1px 3px rgba(0,0,0,0.05)',
              background: selectedAssetId === item.materialBizId ? '#eff6ff' : '#ffffff',
              cursor: 'pointer',
              borderRadius: '6px',
              padding: '12px',
              transition: 'all 0.2s'
            }}
            onClick={() => onSelect(item)}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
              <strong 
                title={item.materialType === 'TEXT' ? item.textContent : extractFileName(item.originalFileName || item.filePath)}
                style={{ 
                  fontSize: '0.82rem', 
                  wordBreak: 'break-all', 
                  color: '#1e293b',
                  display: '-webkit-box',
                  WebkitLineClamp: 3,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden'
                }}
              >
                {item.materialType === 'TEXT' && item.textContent 
                  ? item.textContent 
                  : extractFileName(item.originalFileName || item.filePath)}
              </strong>
              <span style={{ fontSize: '0.72rem', color: '#64748b', background: '#f1f5f9', padding: '2px 6px', borderRadius: '4px', whiteSpace: 'nowrap', alignSelf: 'flex-start' }}>{item.status}</span>
            </div>
            {item.fileSize ? <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '6px' }}>大小: {formatBytes(item.fileSize)}</div> : null}
            {item.duration ? <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '2px' }}>时长: {formatDuration(item.duration)}</div> : null}
            <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'flex-end' }}>
              <button
                style={{ 
                  padding: '4px 10px', 
                  fontSize: '0.75rem', 
                  color: '#ef4444', 
                  background: '#fef2f2', 
                  border: '1px solid #fca5a5', 
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
                disabled={deletingAssetId === item.materialBizId}
                onClick={(e) => { e.stopPropagation(); onDelete(item.materialBizId); }}
              >
                {deletingAssetId === item.materialBizId ? '删除中...' : '删除'}
              </button>
            </div>
          </div>
        ))}
        {items.length === 0 && <div style={{ color: '#94a3b8', fontSize: '0.82rem' }}>暂无</div>}
      </div>
    </div>
  );
}

function ProfileDisplay({ profileJson, materialType, textContent }: { profileJson?: string; materialType?: string; textContent?: string }) {
  if (!profileJson) return <div style={{ color: '#94a3b8', fontSize: '0.9rem' }}>该素材暂无解析数据，请先提取素材信息。</div>;
  let data: any = {};
  try {
    data = JSON.parse(profileJson);
  } catch (e) {
    return <div style={{ color: '#ef4444' }}>JSON 解析失败，数据损坏。</div>;
  }

  const { physicalAttributes, semanticTags, highlights, asrResult } = data;
  const isVideo = materialType === 'VIDEO';
  const isText = materialType === 'TEXT';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', overflowY: 'auto', maxHeight: '550px', paddingRight: '8px' }}>
      
      {/* 原始文案 */}
      {isText && textContent && (
        <div style={{ padding: '12px', background: '#f8fafc', borderRadius: '6px' }}>
          <h5 style={{ margin: '0 0 8px 0', color: '#334155' }}>原文案 (Source)</h5>
          <div style={{ fontSize: '0.82rem', whiteSpace: 'pre-wrap', color: '#334155', lineHeight: '1.5' }}>{textContent}</div>
        </div>
      )}

      {/* 物理属性 */}
      {physicalAttributes && (
        <div style={{ padding: '12px', background: '#f8fafc', borderRadius: '6px' }}>
          <h5 style={{ margin: '0 0 8px 0', color: '#334155' }}>基础物理属性 (Layer 1)</h5>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '0.82rem' }}>
            {physicalAttributes.resolution && <div><strong>分辨率:</strong> {physicalAttributes.resolution}</div>}
            {physicalAttributes.hasAudio !== undefined && <div><strong>包含音频:</strong> {physicalAttributes.hasAudio ? '是' : '否'}</div>}
            {physicalAttributes.charCount !== undefined && <div><strong>字符数:</strong> {physicalAttributes.charCount}</div>}
          </div>
        </div>
      )}

      {/* 语义标签 */}
      {semanticTags && (
        <div style={{ padding: '12px', background: '#f8fafc', borderRadius: '6px' }}>
          <h5 style={{ margin: '0 0 8px 0', color: '#334155' }}>
            {isVideo ? '全局语义与听觉感受 (Layer 2)' : '全局语义 (Layer 2)'}
          </h5>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '0.82rem' }}>
            {semanticTags.mainEntities && <div><strong>核心实体:</strong> {semanticTags.mainEntities.join('，')}</div>}
            {semanticTags.textCategory && <div><strong>文本类别:</strong> {semanticTags.textCategory}</div>}
            {semanticTags.mainKeywords && <div><strong>核心关键词:</strong> {semanticTags.mainKeywords.join('，')}</div>}
            {semanticTags.overallStyle && <div><strong>整体风格:</strong> {semanticTags.overallStyle}</div>}
            {semanticTags.emotionTone && <div><strong>情感基调:</strong> {semanticTags.emotionTone}</div>}
            {semanticTags.lightVibe && <div><strong>光影氛围:</strong> {semanticTags.lightVibe}</div>}
            {semanticTags.audioDescription && isVideo && <div><strong>听觉环境:</strong> <span style={{ color: '#059669', fontWeight: 500 }}>{semanticTags.audioDescription}</span></div>}
            {semanticTags.suitableRoles && <div><strong>推荐角色:</strong> <span style={{ color: '#d97706', fontWeight: 500 }}>{semanticTags.suitableRoles.join(', ')}</span></div>}
          </div>
        </div>
      )}

      {/* 高光片段 */}
      {highlights && highlights.length > 0 && (
        <div style={{ padding: '12px', background: '#f8fafc', borderRadius: '6px' }}>
          <h5 style={{ margin: '0 0 8px 0', color: '#334155' }}>
            {isVideo ? '高光动作与时空片段 (Layer 3)' : '高光内容 (Layer 3)'}
          </h5>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {highlights.map((h: any, i: number) => (
              <div key={i} style={{ borderLeft: '3px solid #3b82f6', paddingLeft: '8px', fontSize: '0.82rem' }}>
                <div style={{ fontWeight: 'bold', color: '#1e293b' }}>
                  {isVideo && h.timeAnchor?.startTime !== undefined
                    ? `[${h.timeAnchor.startTime.toFixed(1)}s - ${h.timeAnchor.endTime?.toFixed(1) || '?'}s] (打分: ${h.usabilityScore})`
                    : `(打分: ${h.usabilityScore})`
                  }
                </div>
                {h.actionState && <div style={{ marginTop: '4px' }}><strong>{isVideo ? '动作轨迹:' : '内容特征:'}</strong> {h.actionState}</div>}
                {h.audioContext && isVideo && <div style={{ marginTop: '4px' }}><strong>视听配合:</strong> <span style={{ color: '#059669', fontWeight: 500 }}>{h.audioContext}</span></div>}
                {h.textContent && isText && <div style={{ marginTop: '4px' }}><strong>高光文本:</strong> <span style={{ color: '#0f172a', fontWeight: 500 }}>{h.textContent}</span></div>}
                {h.textType && isText && <div style={{ marginTop: '4px' }}><strong>文本角色:</strong> {h.textType}</div>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 原始结构化声音 */}
      {asrResult && asrResult.segments && asrResult.segments.length > 0 && isVideo && (
        <div style={{ padding: '12px', background: '#f8fafc', borderRadius: '6px' }}>
          <h5 style={{ margin: '0 0 8px 0', color: '#334155' }}>原生听觉轨道提取 (Pre-ASR)</h5>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.8rem', color: '#475569' }}>
            {asrResult.segments.map((seg: any, i: number) => (
              <div key={i}>[{seg.start?.toFixed(1)}s - {seg.end?.toFixed(1)}s] {seg.text}</div>
            ))}
          </div>
        </div>
      )}

    </div>
  );
}
