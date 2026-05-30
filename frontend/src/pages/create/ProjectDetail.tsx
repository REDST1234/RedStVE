import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { TemplateSummaryData } from '../../types';

type TemplateOption = {
  templateId: string;
  templateVersion: number;
  templateName: string;
};

export default function CreateProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const routeProjectId = id || '';
  const isUnsavedDraft = !routeProjectId || routeProjectId === 'new';
  const projectId = isUnsavedDraft ? '' : routeProjectId;

  const [projectTitle, setProjectTitle] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [projectStatus, setProjectStatus] = useState('DRAFT');
  const [projectTemplateId, setProjectTemplateId] = useState('');

  const [templateOptions, setTemplateOptions] = useState<TemplateOption[]>([]);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState('');

  const [savingProject, setSavingProject] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [bindingTemplate, setBindingTemplate] = useState(false);
  const [startingWorkflow, setStartingWorkflow] = useState(false);

  const selectedTemplate = useMemo(() => {
    if (!selectedTemplateKey) return null;
    const [templateId, version] = selectedTemplateKey.split('@@');
    return {
      templateId,
      templateVersion: Number(version || 1)
    };
  }, [selectedTemplateKey]);

  const boundTemplateView = useMemo(() => {
    if (!projectTemplateId) return '未绑定模板';
    const matched = templateOptions.find((item) => item.templateId === projectTemplateId);
    if (!matched) return '已绑定模板（详情加载中）';
    return `${matched.templateName} · v${matched.templateVersion}`;
  }, [projectTemplateId, templateOptions]);

  useEffect(() => {
    if (isUnsavedDraft) {
      setProjectTitle('');
      setProjectDescription('');
      setProjectStatus('DRAFT');
      setProjectTemplateId('');
    } else {
      void loadProject(projectId);
    }
    void loadTemplateOptions();
  }, [routeProjectId]);

  useEffect(() => {
    if (!projectTemplateId || templateOptions.length === 0) return;
    const matched = templateOptions.find((item) => item.templateId === projectTemplateId);
    if (matched) {
      setSelectedTemplateKey(`${matched.templateId}@@${matched.templateVersion}`);
    }
  }, [projectTemplateId, templateOptions]);

  const loadProject = async (targetProjectId: string) => {
    try {
      const resp = await creationApi.getProject(targetProjectId);
      const project = resp.data;
      setProjectTitle(project?.title || '');
      setProjectDescription(project?.description || '');
      setProjectTemplateId(project?.templateId || '');
      setProjectStatus(project?.status || 'DRAFT');
    } catch (error: any) {
      showToast(error?.message || '加载项目失败', 'error');
    }
  };

  const loadTemplateOptions = async () => {
    try {
      const resp = await creationApi.listTemplates();
      const latest = pickLatestTemplates(resp.data || []);
      setTemplateOptions(latest);

      if (projectTemplateId) {
        const matched = latest.find((item) => item.templateId === projectTemplateId);
        if (matched) {
          setSelectedTemplateKey(`${matched.templateId}@@${matched.templateVersion}`);
        }
      } else if (latest.length > 0) {
        setSelectedTemplateKey(`${latest[0].templateId}@@${latest[0].templateVersion}`);
      }
    } catch (error: any) {
      showToast(error?.message || '加载模板库失败', 'error');
    }
  };

  const persistProject = async (): Promise<string | null> => {
    if (!projectTitle.trim()) {
      showToast('项目名称不能为空', 'error');
      return null;
    }

    if (isUnsavedDraft) {
      const created = await creationApi.createProject(projectTitle.trim(), projectDescription.trim() || undefined);
      const newId = created.data?.projectId;
      if (!newId) throw new Error('创建项目失败: 未返回 projectId');
      navigate(`/create/detail/${newId}`, { replace: true });
      return newId;
    }

    await creationApi.updateProject(projectId, projectTitle.trim(), projectDescription.trim() || undefined);
    return projectId;
  };

  const saveProjectBasics = async () => {
    setSavingProject(true);
    try {
      const persistedId = await persistProject();
      if (!persistedId) return;
      showToast('项目信息已保存', 'success');
      await loadProject(persistedId);
    } catch (error: any) {
      showToast(error?.message || '保存项目失败', 'error');
    } finally {
      setSavingProject(false);
    }
  };

  const deleteProject = async () => {
    if (isUnsavedDraft) {
      showToast('未保存项目无需删除', 'success');
      navigate('/create');
      return;
    }
    if (!window.confirm('确认删除该项目吗？')) {
      return;
    }
    setDeletingProject(true);
    try {
      await creationApi.deleteProject(projectId);
      showToast('项目已删除', 'success');
      navigate('/create');
    } catch (error: any) {
      showToast(error?.message || '删除项目失败', 'error');
    } finally {
      setDeletingProject(false);
    }
  };

  const bindTemplate = async () => {
    if (!selectedTemplate) return;
    setBindingTemplate(true);
    try {
      const persistedId = isUnsavedDraft ? await persistProject() : projectId;
      if (!persistedId) return;
      await creationApi.bindTemplate(persistedId, selectedTemplate.templateId, selectedTemplate.templateVersion);
      setProjectTemplateId(selectedTemplate.templateId);
      showToast('模板绑定成功', 'success');
      await loadProject(persistedId);
    } catch (error: any) {
      showToast(error?.message || '模板绑定失败', 'error');
    } finally {
      setBindingTemplate(false);
    }
  };

  const startWorkflow = async () => {
    setStartingWorkflow(true);
    try {
      const persistedId = await persistProject();
      if (!persistedId) return;
      showToast('已进入素材工作流', 'success');
      navigate(`/create/detail/${persistedId}/workflow`);
    } catch (error: any) {
      showToast(error?.message || '启动工作流失败', 'error');
    } finally {
      setStartingWorkflow(false);
    }
  };

  return (
    <div className="detail-page fade-in" style={{ borderColor: '#e0e7ff' }}>
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate('/create')} title="返回">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作项目总览</h2>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button className="btn-outline" onClick={saveProjectBasics} disabled={savingProject || startingWorkflow || bindingTemplate}>
            {savingProject ? '保存中...' : '保存项目'}
          </button>
          <button className="btn-outline" onClick={deleteProject} disabled={deletingProject || savingProject || startingWorkflow}>
            {deletingProject ? '删除中...' : '删除项目'}
          </button>
          <button className="btn-primary" onClick={startWorkflow} disabled={startingWorkflow || savingProject || bindingTemplate || !projectTitle.trim()}>
            {startingWorkflow ? '准备中...' : '开始创作工作流'}
          </button>
        </div>
      </div>

      <div className="detail-body" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        <div className="result-card">
          <h4>项目基础信息</h4>
          <div className="input-group">
            <label className="input-label">项目名称</label>
            <input className="input-field" value={projectTitle} onChange={(e) => setProjectTitle(e.target.value)} placeholder="请输入项目名称" />
          </div>
          <div className="input-group" style={{ marginTop: '10px' }}>
            <label className="input-label">项目描述</label>
            <textarea
              className="input-field"
              style={{ minHeight: '120px' }}
              value={projectDescription}
              onChange={(e) => setProjectDescription(e.target.value)}
              placeholder="请输入项目描述"
            />
          </div>
          <div className="info-grid" style={{ marginTop: '14px' }}>
            <div className="info-item">
              <span>当前状态</span>
              <strong>{projectStatus || '-'}</strong>
            </div>
            <div className="info-item">
              <span>已绑定模板</span>
              <strong>{boundTemplateView}</strong>
            </div>
          </div>
          {isUnsavedDraft && (
            <div style={{ marginTop: '10px', color: '#b45309', fontSize: '0.82rem' }}>
              当前为未落库草稿。仅在“保存项目”或“开始创作工作流”时落库。
            </div>
          )}
        </div>

        <div className="result-card">
          <h4>模板绑定信息</h4>
          <div className="input-group">
            <label className="input-label">选择模板（仅展示模板名称与版本）</label>
            <select
              className="input-field"
              value={selectedTemplateKey}
              onChange={(e) => setSelectedTemplateKey(e.target.value)}
            >
              {templateOptions.map((tpl) => (
                <option key={`${tpl.templateId}@@${tpl.templateVersion}`} value={`${tpl.templateId}@@${tpl.templateVersion}`}>
                  {tpl.templateName} · v{tpl.templateVersion}
                </option>
              ))}
              {templateOptions.length === 0 && <option value="">暂无模板</option>}
            </select>
          </div>

          <div style={{ marginTop: '14px', color: '#64748b', fontSize: '0.9rem' }}>
            当前绑定：{boundTemplateView}
          </div>

          <div style={{ marginTop: '14px', display: 'flex', gap: '10px' }}>
            <button className="btn-primary" onClick={bindTemplate} disabled={!selectedTemplate || bindingTemplate || startingWorkflow}>
              {bindingTemplate ? '绑定中...' : '绑定模板'}
            </button>
          </div>

          <div style={{ marginTop: '16px', color: '#94a3b8', fontSize: '0.82rem' }}>
            本页仅做项目与模板准备，不展示匹配/适配等后续执行能力。
          </div>
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
