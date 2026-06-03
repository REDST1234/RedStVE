import { CSSProperties, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { CreationAssetData, CreationMatchResultData, CreationMatchResultItem } from '../../types';

type StrategyCard = {
  strategyType: string;
  params: Record<string, unknown>;
};

type ParsedAdaptationPlan = {
  strategyChain: StrategyCard[];
};

const STATUS_META: Record<string, { label: string; color: string; soft: string; border: string }> = {
  MATCHED: { label: '已匹配', color: '#047857', soft: 'rgba(16, 185, 129, 0.12)', border: 'rgba(16, 185, 129, 0.28)' },
  PARTIAL: { label: '部分覆盖', color: '#b45309', soft: 'rgba(245, 158, 11, 0.14)', border: 'rgba(245, 158, 11, 0.28)' },
  MISSING: { label: '素材缺失', color: '#dc2626', soft: 'rgba(239, 68, 68, 0.12)', border: 'rgba(239, 68, 68, 0.24)' },
  VETOED: { label: '被否决', color: '#7c2d12', soft: 'rgba(249, 115, 22, 0.14)', border: 'rgba(249, 115, 22, 0.24)' }
};

const CARD_SURFACE: CSSProperties = {
  border: '1px solid #dbe4f0',
  borderRadius: '18px',
  background: 'linear-gradient(180deg, #ffffff 0%, #f8fbff 100%)',
  boxShadow: '0 18px 48px rgba(15, 23, 42, 0.06)'
};

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

  const matchOverview = useMemo(() => {
    if (!matchResult) {
      return {
        matched: 0,
        partial: 0,
        missing: 0,
        vetoed: 0,
        executableStrategies: 0
      };
    }
    return matchResult.items.reduce((acc, item) => {
      const status = (item.matchStatus || '').toUpperCase();
      const chain = parseAdaptationPlan(item.adaptationPlanJson).strategyChain;
      if (status === 'MATCHED') acc.matched += 1;
      if (status === 'PARTIAL') acc.partial += 1;
      if (status === 'MISSING') acc.missing += 1;
      if (status === 'VETOED') acc.vetoed += 1;
      acc.executableStrategies += chain.length;
      return acc;
    }, {
      matched: 0,
      partial: 0,
      missing: 0,
      vetoed: 0,
      executableStrategies: 0
    });
  }, [matchResult]);

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
    <div className="detail-page fade-in" style={{ borderColor: '#dbeafe', background: 'linear-gradient(180deg, #f8fbff 0%, #eef6ff 100%)' }}>
      <div className="detail-header" style={{ background: 'rgba(248, 250, 252, 0.86)', justifyContent: 'space-between', backdropFilter: 'blur(18px)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate(`/create/detail/${projectId}/workflow`)} title="返回素材阶段">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <div>
            <h2 className="detail-title" style={{ marginBottom: '4px' }}>创作工作流 · 素材缺口识别</h2>
            <div style={{ fontSize: '0.84rem', color: '#64748b' }}>
              把模板段落和当前素材放到同一视图里看清楚：哪里已经能迁移，哪里仍然缺素材，哪里只能先做局部适配。
            </div>
          </div>
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
          <button
            onClick={() => navigate(`/create/detail/${projectId}/bgm-debug`)}
            className="action-btn secondary"
            style={{ padding: '6px 12px', fontSize: '0.85rem', marginLeft: '6px' }}
          >
            去往 BGM 推荐 Debug
          </button>
        </div>
      </div>

      <div style={{ padding: '18px 32px 0', color: '#64748b', fontSize: '0.9rem' }}>
        项目：<strong style={{ color: '#0f172a' }}>{projectTitle || '未命名项目'}</strong> · 状态：{projectStatus}
      </div>

      <div className="detail-body" style={{ display: 'grid', gridTemplateColumns: '340px minmax(0, 1fr)', gap: '18px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ ...CARD_SURFACE, padding: '18px' }}>
            <h4 style={{ marginBottom: '14px' }}>识别前检查</h4>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <MetricTile label="模板绑定" value={projectTemplateId ? '已完成' : '未完成'} tone={projectTemplateId ? 'good' : 'warn'} />
              <MetricTile label="素材总数" value={String(assets.length)} />
              <MetricTile label="视频" value={String(groupedCounts.video)} />
              <MetricTile label="图片" value={String(groupedCounts.image)} />
              <MetricTile label="文本" value={String(groupedCounts.text)} />
              <MetricTile label="已分析" value={allAssetsProfiled ? '是' : '否'} tone={allAssetsProfiled ? 'good' : 'warn'} />
            </div>
            {gapDetectionDisabledReason && (
              <div style={{ marginTop: '14px', color: '#b45309', fontSize: '0.82rem', background: 'rgba(245, 158, 11, 0.08)', border: '1px solid rgba(245, 158, 11, 0.18)', borderRadius: '12px', padding: '10px 12px' }}>
                当前不可识别：{gapDetectionDisabledReason}
              </div>
            )}
          </div>

          <div style={{ ...CARD_SURFACE, padding: '18px' }}>
            <h4 style={{ marginBottom: '14px' }}>本轮诊断概览</h4>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <MetricTile label="已匹配段落" value={String(matchOverview.matched)} tone="good" />
              <MetricTile label="部分覆盖" value={String(matchOverview.partial)} tone="warn" />
              <MetricTile label="缺失段落" value={String(matchOverview.missing)} tone="bad" />
              <MetricTile label="可执行策略数" value={String(matchOverview.executableStrategies)} />
            </div>
            {matchResult && (
              <div style={{ marginTop: '14px', display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.82rem', color: '#475569' }}>
                <div>当前版本：<strong style={{ color: '#0f172a' }}>{matchResult.versionId}</strong></div>
                <div>整体覆盖率：<strong style={{ color: '#0f172a' }}>{Math.round((matchResult.overallCoverage || 0) * 100)}%</strong></div>
                <div>说明：当前页面优先展示“已有素材是否能迁移”，不是最终成片补全能力；如果段落为 MISSING，现阶段通常不会生成可执行策略链。</div>
              </div>
            )}
          </div>

          <div style={{ ...CARD_SURFACE, padding: '18px' }}>
            <h4 style={{ marginBottom: '10px' }}>如何读这个面板</h4>
            <div style={{ color: '#64748b', fontSize: '0.84rem', lineHeight: 1.75 }}>
              绿色代表素材已经足够迁移，橙色代表需要局部补位或改造，红色代表当前缺少同类素材。下方策略卡片展示的是系统现在能执行的适配链；如果是空态，说明当前阶段还没有可执行的素材改造方案。
            </div>
          </div>
        </div>

        <div style={{ ...CARD_SURFACE, padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
            <div>
              <h4 style={{ marginBottom: '4px' }}>素材缺口识别结果</h4>
              <div style={{ fontSize: '0.82rem', color: '#64748b' }}>
                {matchResult
                  ? `Version: ${matchResult.versionId} · 覆盖率: ${Math.round((matchResult.overallCoverage || 0) * 100)}%`
                  : '当前还没有可展示的匹配结果'}
              </div>
            </div>
            {matchResult && (
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn-outline" onClick={confirmAdaptation} disabled={adaptingSlots}>
                  {adaptingSlots ? '适配中...' : '确认并重新执行适配'}
                </button>
                <button
                  className="btn-primary"
                  onClick={() => navigate(`/create/detail/${projectId}/generation`)}
                >
                  去往视频生成 (P3)
                </button>
              </div>
            )}
          </div>

          {matchResult ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {matchResult.items.map((item) => (
                <SegmentMatchCard key={`${item.segmentIndex}-${item.segmentRole}`} item={item} />
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
                  : '点击右上角“开始素材缺口识别”后，这里会展示各段落的匹配状态、覆盖原因和可执行适配策略。'}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SegmentMatchCard({ item }: { item: CreationMatchResultItem }) {
  const status = (item.matchStatus || 'MISSING').toUpperCase();
  const meta = STATUS_META[status] || STATUS_META.MISSING;
  const plan = parseAdaptationPlan(item.adaptationPlanJson);
  const strategyChain = plan.strategyChain;

  return (
    <div
      style={{
        border: `1px solid ${meta.border}`,
        borderRadius: '18px',
        padding: '16px',
        background: `linear-gradient(180deg, ${meta.soft} 0%, rgba(255,255,255,0.96) 36%)`,
        boxShadow: '0 16px 40px rgba(15, 23, 42, 0.05)'
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '14px', alignItems: 'flex-start', marginBottom: '10px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <div style={{ fontSize: '1.12rem', fontWeight: 700, color: '#0f172a' }}>
            Segment {item.segmentIndex} · {item.segmentRole}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            <StatusChip label={meta.label} color={meta.color} soft={meta.soft} border={meta.border} />
            {typeof item.matchScore === 'number' && (
              <InfoPill label={`覆盖 ${Math.round((item.matchScore || 0) * 100)}%`} />
            )}
            {item.matchedAssetId && (
              <InfoPill label={`素材 ${item.matchedAssetId}${item.matchedHighlightId ? ` / ${item.matchedHighlightId}` : ''}`} />
            )}
          </div>
        </div>
        <div style={{ minWidth: '200px', maxWidth: '280px', fontSize: '0.8rem', color: '#475569', lineHeight: 1.65, textAlign: 'right' }}>
          {buildStatusSummary(item)}
        </div>
      </div>

      {item.matchReason && (
        <NarrativePanel
          title="匹配说明"
          color="#1d4ed8"
          background="rgba(59, 130, 246, 0.07)"
          text={item.matchReason}
        />
      )}

      {item.vetoReason && (
        <NarrativePanel
          title="否决原因"
          color="#b91c1c"
          background="rgba(239, 68, 68, 0.08)"
          text={item.vetoReason}
        />
      )}

      <div style={{ marginTop: '14px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
          <div style={{ fontSize: '0.88rem', fontWeight: 700, color: '#0f172a' }}>适配策略链</div>
          <div style={{ fontSize: '0.76rem', color: '#64748b' }}>
            {strategyChain.length > 0 ? `${strategyChain.length} 个可执行步骤` : '当前无可执行策略'}
          </div>
        </div>

        {strategyChain.length > 0 ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '10px' }}>
            {strategyChain.map((strategy, index) => (
              <StrategyCardView key={`${strategy.strategyType}-${index}`} strategy={strategy} index={index} />
            ))}
          </div>
        ) : (
          <EmptyStrategyState item={item} />
        )}
      </div>
    </div>
  );
}

function StrategyCardView({ strategy, index }: { strategy: StrategyCard; index: number }) {
  const entries = Object.entries(strategy.params || {}).slice(0, 5);
  return (
    <div style={{ border: '1px solid #dbe7f3', borderRadius: '14px', padding: '12px', background: 'rgba(255,255,255,0.92)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
        <div style={{ width: '24px', height: '24px', borderRadius: '999px', background: '#dbeafe', color: '#1d4ed8', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.74rem', fontWeight: 700 }}>
          {index + 1}
        </div>
        <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.88rem' }}>{beautifyStrategyName(strategy.strategyType)}</div>
      </div>
      {entries.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {entries.map(([key, value]) => (
            <div key={key} style={{ display: 'grid', gridTemplateColumns: '92px 1fr', gap: '8px', fontSize: '0.78rem', lineHeight: 1.55 }}>
              <span style={{ color: '#64748b' }}>{beautifyFieldName(key)}</span>
              <span style={{ color: '#0f172a', wordBreak: 'break-word' }}>{renderCompactValue(value)}</span>
            </div>
          ))}
        </div>
      ) : (
        <div style={{ fontSize: '0.78rem', color: '#64748b' }}>该步骤没有额外参数，系统会按默认策略执行。</div>
      )}
    </div>
  );
}

function EmptyStrategyState({ item }: { item: CreationMatchResultItem }) {
  const status = (item.matchStatus || '').toUpperCase();
  const copy = status === 'MATCHED'
    ? {
      title: '无需额外适配',
      desc: '当前素材已经足够支撑这一段，系统会优先直接复用原素材或轻量裁切，不需要额外策略链。'
    }
    : status === 'PARTIAL'
      ? {
        title: '目前仅完成覆盖判断',
        desc: '这一段已找到部分可用素材，但当前系统尚未生成额外的补位策略。后续可继续补素材，或接入更强的合成补全能力。'
      }
      : {
        title: '当前没有可执行补全链',
        desc: '这表示系统没有找到可直接匹配的同类素材，而且当前协议仍以“已有素材改造”为主，还不能自动把文本或 LOGO 合成为完整段落。'
      };

  return (
    <div style={{
      border: '1px dashed #cbd5e1',
      borderRadius: '14px',
      padding: '14px 16px',
      background: 'rgba(248, 250, 252, 0.9)',
      color: '#475569'
    }}>
      <div style={{ fontWeight: 700, color: '#0f172a', marginBottom: '6px' }}>{copy.title}</div>
      <div style={{ fontSize: '0.82rem', lineHeight: 1.7 }}>{copy.desc}</div>
    </div>
  );
}

function MetricTile({ label, value, tone = 'neutral' }: { label: string; value: string; tone?: 'good' | 'warn' | 'bad' | 'neutral' }) {
  const palette = tone === 'good'
    ? { bg: 'rgba(16, 185, 129, 0.1)', border: 'rgba(16, 185, 129, 0.18)', value: '#047857' }
    : tone === 'warn'
      ? { bg: 'rgba(245, 158, 11, 0.11)', border: 'rgba(245, 158, 11, 0.18)', value: '#b45309' }
      : tone === 'bad'
        ? { bg: 'rgba(239, 68, 68, 0.1)', border: 'rgba(239, 68, 68, 0.18)', value: '#dc2626' }
        : { bg: 'rgba(148, 163, 184, 0.08)', border: 'rgba(148, 163, 184, 0.16)', value: '#0f172a' };

  return (
    <div style={{ border: `1px solid ${palette.border}`, borderRadius: '14px', background: palette.bg, padding: '12px' }}>
      <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '6px' }}>{label}</div>
      <div style={{ fontSize: '1rem', fontWeight: 700, color: palette.value }}>{value}</div>
    </div>
  );
}

function StatusChip({ label, color, soft, border }: { label: string; color: string; soft: string; border: string }) {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '6px',
      padding: '4px 10px',
      borderRadius: '999px',
      background: soft,
      border: `1px solid ${border}`,
      color,
      fontSize: '0.76rem',
      fontWeight: 700
    }}>
      <span style={{ width: '6px', height: '6px', borderRadius: '999px', background: color }} />
      {label}
    </span>
  );
}

function InfoPill({ label }: { label: string }) {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: '4px 10px',
      borderRadius: '999px',
      background: 'rgba(255,255,255,0.78)',
      border: '1px solid #dbe4f0',
      color: '#334155',
      fontSize: '0.76rem'
    }}>
      {label}
    </span>
  );
}

function NarrativePanel({ title, text, color, background }: { title: string; text: string; color: string; background: string }) {
  return (
    <div style={{ marginTop: '10px', borderRadius: '14px', padding: '12px 14px', background }}>
      <div style={{ fontSize: '0.76rem', fontWeight: 700, color, marginBottom: '6px' }}>{title}</div>
      <div style={{ fontSize: '0.83rem', color: '#334155', lineHeight: 1.7 }}>{text}</div>
    </div>
  );
}

function parseAdaptationPlan(value?: string): ParsedAdaptationPlan {
  if (!value) {
    return { strategyChain: [] };
  }
  try {
    const parsed = JSON.parse(value) as { strategyChain?: unknown[] };
    const chain = Array.isArray(parsed?.strategyChain) ? parsed.strategyChain : [];
    return {
      strategyChain: chain.map((item) => {
        const node = (item && typeof item === 'object') ? item as Record<string, unknown> : {};
        return {
          strategyType: String(node.strategyType || 'UNKNOWN'),
          params: (node.params && typeof node.params === 'object') ? node.params as Record<string, unknown> : {}
        };
      })
    };
  } catch {
    return { strategyChain: [] };
  }
}

function beautifyStrategyName(name?: string) {
  const normalized = (name || 'UNKNOWN').trim().toUpperCase();
  const alias: Record<string, string> = {
    TRIM_AND_CUT: '截取裁切',
    SMART_CROP: '智能裁剪',
    LIGHTING_ADJUST: '光感微调',
    LOCAL_BLUR: '局部模糊',
    LOOP_SEQUENCE: '循环延展',
    KEN_BURNS_MOTION: 'Ken Burns 动效',
    AUDIO_DUCKING: '音频压混'
  };
  return alias[normalized] || normalized.replace(/_/g, ' ');
}

function beautifyFieldName(name: string) {
  const alias: Record<string, string> = {
    brightnessPercent: '亮度',
    contrastPercent: '对比度',
    boundingBox: '作用区域',
    x: '横向',
    y: '纵向',
    w: '宽度',
    h: '高度'
  };
  return alias[name] || name;
}

function renderCompactValue(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map((item) => renderCompactValue(item)).join(' / ');
  if (typeof value === 'object') return Object.entries(value as Record<string, unknown>).map(([k, v]) => `${k}:${renderCompactValue(v)}`).join(', ');
  return String(value);
}

function buildStatusSummary(item: CreationMatchResultItem) {
  const status = (item.matchStatus || '').toUpperCase();
  if (status === 'MATCHED') {
    return '这一段已经找到可直接复用的素材，后续主要看是否需要轻量裁切、局部模糊或节奏对齐。';
  }
  if (status === 'PARTIAL') {
    return '这一段有部分可用素材，但覆盖不完整。更适合继续补同类素材，或后续接入更强的文本/品牌补全方案。';
  }
  if (status === 'VETOED') {
    return '系统认为现有素材不适合硬迁移到这一段，所以暂时阻止适配，避免生成质量明显失真。';
  }
  return '这一段当前没有找到可直接迁移的素材，后续需要补上传同类可视化素材，或接入更完整的合成补全能力。';
}
