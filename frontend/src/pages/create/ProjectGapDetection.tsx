import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { CreationAssetData, CreationMatchResultData } from '../../types';

export default function CreateProjectGapDetection() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const projectId = id || '';

  const [projectTitle, setProjectTitle] = useState('');
  const [projectStatus, setProjectStatus] = useState('DRAFT');
  const [projectTemplateId, setProjectTemplateId] = useState('');
  const [assets, setAssets] = useState<CreationAssetData[]>([]);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMatch, setLoadingMatch] = useState(false);
  const [matchingSlots, setMatchingSlots] = useState(false);
  const [adaptingSlots, setAdaptingSlots] = useState(false);
  const [matchResult, setMatchResult] = useState<CreationMatchResultData | null>(null);

  const groupedCounts = useMemo(() => ({
    video: assets.filter((item) => item.materialType === 'VIDEO').length,
    image: assets.filter((item) => item.materialType === 'IMAGE').length,
    text: assets.filter((item) => item.materialType === 'TEXT').length
  }), [assets]);

  const hasAnyAssets = assets.length > 0;
  const allAssetsProfiled = hasAnyAssets && assets.every((item) => (item.status || '').toUpperCase() === 'PROFILED');
  const canRunGapDetection = Boolean(projectTemplateId) && allAssetsProfiled && !matchingSlots;
  const gapDetectionDisabledReason = !projectTemplateId
    ? '请先绑定模板'
    : !hasAnyAssets
      ? '请先上传素材'
      : !allAssetsProfiled
        ? '等待素材分析完成'
        : '';

  useEffect(() => {
    if (!projectId || projectId === 'new') {
      navigate('/create', { replace: true });
      return;
    }
    void Promise.all([loadProject(), loadAssets(), loadMatchResult(true)]);
  }, [projectId]);

  const loadProject = async () => {
    if (!projectId) return;
    try {
      const resp = await creationApi.getProject(projectId);
      setProjectTitle(resp.data?.title || '');
      setProjectStatus(resp.data?.status || 'DRAFT');
      setProjectTemplateId(resp.data?.templateId || '');
    } catch (error: any) {
      showToast(error?.message || '加载项目失败', 'error');
    }
  };

  const loadAssets = async () => {
    if (!projectId) return;
    setLoadingAssets(true);
    try {
      const resp = await creationApi.listAssets(projectId);
      setAssets(resp.data || []);
    } catch (error: any) {
      showToast(error?.message || '加载素材失败', 'error');
    } finally {
      setLoadingAssets(false);
    }
  };

  const loadMatchResult = async (silentIfMissing = false) => {
    if (!projectId) return;
    setLoadingMatch(true);
    try {
      const resp = await creationApi.getMatchResult(projectId);
      setMatchResult(resp.data || null);
    } catch (error: any) {
      setMatchResult(null);
      if (!silentIfMissing) {
        showToast(error?.message || '加载素材缺口识别结果失败', 'error');
      }
    } finally {
      setLoadingMatch(false);
    }
  };

  const runGapDetection = async () => {
    if (!projectId || !canRunGapDetection) return;
    setMatchingSlots(true);
    try {
      const triggered = await creationApi.triggerMatch(projectId);
      const versionId = triggered.data?.versionId;
      const result = await creationApi.getMatchResult(projectId, versionId);
      setMatchResult(result.data);
      await loadProject();
      showToast('素材缺口识别完成', 'success');
    } catch (error: any) {
      showToast(error?.message || '素材缺口识别失败', 'error');
    } finally {
      setMatchingSlots(false);
    }
  };

  const confirmAdaptation = async () => {
    if (!projectId || !matchResult?.versionId) return;
    setAdaptingSlots(true);
    try {
      await creationApi.triggerAdapt(projectId, matchResult.versionId);
      await loadProject();
      showToast('已触发素材适配处理', 'success');
    } catch (error: any) {
      showToast(error?.message || '触发适配失败', 'error');
    } finally {
      setAdaptingSlots(false);
    }
  };

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate(`/create/detail/${projectId}/workflow`)} title="返回素材阶段">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作工作流 · 素材缺口识别</h2>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <button
            onClick={runGapDetection}
            className="btn-primary"
            style={{ padding: '8px 14px', fontSize: '0.88rem' }}
            disabled={!canRunGapDetection}
            title={gapDetectionDisabledReason || '开始素材缺口识别'}
          >
            {matchingSlots ? '识别中...' : matchResult ? '重新识别素材缺口' : '开始素材缺口识别'}
          </button>
          <button
            className="btn-outline"
            style={{ padding: '8px 14px', fontSize: '0.88rem' }}
            onClick={() => void Promise.all([loadProject(), loadAssets(), loadMatchResult(true)])}
            disabled={loadingAssets || loadingMatch}
          >
            {loadingAssets || loadingMatch ? '刷新中...' : '刷新结果'}
          </button>
          <button
            onClick={() => navigate(`/create/detail/${projectId}/template-debug`)}
            className="action-btn secondary"
            style={{ padding: '6px 12px', fontSize: '0.85rem' }}
          >
            去往模板推荐 Debug
          </button>
        </div>
      </div>

      <div style={{ padding: '14px 32px 0', color: '#64748b', fontSize: '0.9rem' }}>
        项目：<strong style={{ color: '#0f172a' }}>{projectTitle || '未命名项目'}</strong> · 状态：{projectStatus}
      </div>

      <div className="detail-body" style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: '16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div className="result-card">
            <h4>识别前检查</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.86rem', color: '#334155' }}>
              <div><strong>模板绑定：</strong>{projectTemplateId ? '已完成' : '未完成'}</div>
              <div><strong>素材总数：</strong>{assets.length}</div>
              <div><strong>视频：</strong>{groupedCounts.video}</div>
              <div><strong>图片：</strong>{groupedCounts.image}</div>
              <div><strong>文本：</strong>{groupedCounts.text}</div>
              <div><strong>全部素材已分析：</strong>{allAssetsProfiled ? '是' : '否'}</div>
            </div>
            {gapDetectionDisabledReason && (
              <div style={{ marginTop: '12px', color: '#b45309', fontSize: '0.82rem' }}>
                当前不可识别：{gapDetectionDisabledReason}
              </div>
            )}
          </div>

          <div className="result-card">
            <h4>当前说明</h4>
            <div style={{ color: '#64748b', fontSize: '0.84rem', lineHeight: 1.7 }}>
              这里负责展示 `/{'{projectId}'}/match-result` 的最新识别结果，并在你确认素材准备完毕后手动触发缺口识别。
            </div>
          </div>
        </div>

        <div className="result-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <div>
              <h4 style={{ marginBottom: '4px' }}>素材缺口识别结果</h4>
              <div style={{ fontSize: '0.82rem', color: '#64748b' }}>
                {matchResult
                  ? `Version: ${matchResult.versionId} · 覆盖率: ${Math.round((matchResult.overallCoverage || 0) * 100)}%`
                  : '当前还没有可展示的匹配结果'}
              </div>
            </div>
            {matchResult && (
              <button className="btn-primary" onClick={confirmAdaptation} disabled={adaptingSlots}>
                {adaptingSlots ? '适配中...' : '确认并执行适配'}
              </button>
            )}
          </div>

          {matchResult ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {matchResult.items.map((item) => (
                <div key={`${item.segmentIndex}-${item.segmentRole}`} style={{ border: '1px solid #e2e8f0', borderRadius: '6px', padding: '10px', background: '#fff' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', marginBottom: '6px' }}>
                    <strong>Segment {item.segmentIndex} · {item.segmentRole}</strong>
                    <span style={{ color: item.matchStatus === 'MATCHED' ? '#059669' : item.matchStatus === 'PARTIAL' ? '#d97706' : '#dc2626', fontWeight: 600 }}>
                      {item.matchStatus} {item.matchScore !== undefined ? `· ${Math.round((item.matchScore || 0) * 100)}%` : ''}
                    </span>
                  </div>
                  {item.matchedAssetId && <div style={{ fontSize: '0.82rem', color: '#475569' }}>素材: {item.matchedAssetId}{item.matchedHighlightId ? ` / ${item.matchedHighlightId}` : ''}</div>}
                  {item.matchReason && <div style={{ fontSize: '0.82rem', color: '#334155', marginTop: '4px' }}>{item.matchReason}</div>}
                  {item.vetoReason && <div style={{ fontSize: '0.82rem', color: '#b91c1c', marginTop: '4px' }}>{item.vetoReason}</div>}
                  {item.adaptationPlanJson && (
                    <pre style={{ marginTop: '8px', background: '#f8fafc', padding: '8px', borderRadius: '4px', fontSize: '0.72rem', overflowX: 'auto', maxHeight: '140px' }}>
                      {formatJson(item.adaptationPlanJson)}
                    </pre>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="creation-material-placeholder" style={{ minHeight: '360px' }}>
              <div className="creation-material-placeholder-title">
                {loadingMatch ? '正在读取识别结果...' : '暂无识别结果'}
              </div>
              <div className="creation-material-placeholder-text">
                {loadingMatch
                  ? '系统正在拉取当前项目最新的 match-result。'
                  : '点击右上角“开始素材缺口识别”后，这里会展示各段落的匹配状态、缺口原因与 adaptationPlan。'}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatJson(value?: string) {
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
