import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { ProjectCreationTabs } from '../../components/ProjectCreationTabs';



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
  const [savingProject, setSavingProject] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [startingWorkflow, setStartingWorkflow] = useState(false);
  const [aspectRatio, setAspectRatio] = useState<'9:16' | '16:9'>('9:16');



  useEffect(() => {
    if (isUnsavedDraft) {
      setProjectTitle('');
      setProjectDescription('');
      setProjectStatus('DRAFT');
      setAspectRatio('9:16');
    } else {
      void loadProject(projectId);
    }
  }, [routeProjectId]);

  const loadProject = async (targetProjectId: string) => {
    try {
      const resp = await creationApi.getProject(targetProjectId);
      const project = resp.data;
      setProjectTitle(project?.title || '');
      setProjectDescription(project?.description || '');
      setProjectStatus(project?.status || 'DRAFT');
      if (project?.aspectRatio && (project.aspectRatio === '9:16' || project.aspectRatio === '16:9')) {
        setAspectRatio(project.aspectRatio as '9:16' | '16:9');
      }
    } catch (error: any) {
      showToast(error?.message || '加载项目失败', 'error');
    }
  };

  const persistProject = async (): Promise<string | null> => {
    if (!projectTitle.trim()) {
      showToast('项目名称不能为空', 'error');
      return null;
    }

    if (isUnsavedDraft) {
      const created = await creationApi.createProject(projectTitle.trim(), projectDescription.trim() || undefined, aspectRatio);
      const newId = created.data?.projectId;
      if (!newId) throw new Error('创建项目失败: 未返回 projectId');
      navigate(`/create/detail/${newId}`, { replace: true });
      return newId;
    }

    await creationApi.updateProject(projectId, projectTitle.trim(), projectDescription.trim() || undefined, aspectRatio);
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
      <div className="detail-header" style={{ background: '#f8fafc', justifyContent: 'space-between', borderBottom: 'none', paddingBottom: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button className="back-btn" onClick={() => navigate('/create')} title="返回">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
            </svg>
          </button>
          <h2 className="detail-title">创作项目总览</h2>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button className="btn-outline" onClick={saveProjectBasics} disabled={savingProject || startingWorkflow}>
            {savingProject ? '保存中...' : '保存项目'}
          </button>
          <button className="btn-outline" onClick={deleteProject} disabled={deletingProject || savingProject || startingWorkflow}>
            {deletingProject ? '删除中...' : '删除项目'}
          </button>
          <button className="btn-primary" onClick={startWorkflow} disabled={startingWorkflow || savingProject || !projectTitle.trim()}>
            {startingWorkflow ? '准备中...' : '开始创作工作流'}
          </button>
        </div>
      </div>
      <ProjectCreationTabs projectId={projectId} activeTab="detail" />

      <div className="detail-body" style={{ maxWidth: '800px', margin: '0 auto' }}>
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
          
          <div className="input-group" style={{ marginTop: '14px' }}>
            <label className="input-label" style={{ marginBottom: '8px', display: 'block' }}>选择画面比例</label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '12px' }}>
              {[
                { value: '9:16', label: '9:16', hint: '竖屏短视频' },
                { value: '16:9', label: '16:9', hint: '横屏宣传 / 演示' }
              ].map((option) => {
                const selected = aspectRatio === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setAspectRatio(option.value as '9:16' | '16:9')}
                    style={{
                      border: selected ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: selected ? '#eff6ff' : '#ffffff',
                      borderRadius: '12px',
                      padding: '14px 12px',
                      cursor: 'pointer',
                      textAlign: 'left',
                      transition: 'all 0.2s ease',
                      boxShadow: selected ? '0 8px 20px rgba(37,99,235,0.12)' : 'none'
                    }}
                  >
                    <div style={{ fontSize: '1rem', fontWeight: 700, color: '#0f172a', marginBottom: '4px' }}>
                      {option.label}
                    </div>
                    <div style={{ fontSize: '0.82rem', color: '#64748b' }}>{option.hint}</div>
                  </button>
                );
              })}
            </div>
            <div style={{ color: '#64748b', fontSize: '0.82rem', marginTop: '8px' }}>
              注意：确定后，大模型将以此画幅硬约束进行素材裁剪与排版。
            </div>
          </div>
          <div className="info-grid" style={{ marginTop: '14px' }}>
            <div className="info-item">
              <span>当前状态</span>
              <strong>{projectStatus || '-'}</strong>
            </div>
          </div>
          {isUnsavedDraft && (
            <div style={{ marginTop: '10px', color: '#b45309', fontSize: '0.82rem' }}>
              当前为未落库草稿。仅在“保存项目”或“开始创作工作流”时落库。
            </div>
          )}
        </div>
      </div>
    </div>
  );
}


