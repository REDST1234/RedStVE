import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { useToast } from '../../contexts/ToastContext';
import { CreationProjectData } from '../../types';

export default function CreateProjectList() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [creationProjects, setCreationProjects] = useState<CreationProjectData[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingProjectId, setEditingProjectId] = useState<string | null>(null);
  const [editProjectTitle, setEditProjectTitle] = useState('');
  const [editProjectDescription, setEditProjectDescription] = useState('');
  const [editAspectRatio, setEditAspectRatio] = useState('9:16');
  const [savingProject, setSavingProject] = useState(false);

  const coverPalette = useMemo(
    () => [
      'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1496171367470-9ed9a91ea931?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1460355976672-71c3f0a4bdac?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80'
    ],
    []
  );

  useEffect(() => {
    void loadProjects();
  }, []);

  const loadProjects = async () => {
    setLoading(true);
    try {
      const resp = await creationApi.listProjects(1, 50, keyword);
      setCreationProjects(resp.data?.list || []);
    } catch (error: any) {
      showToast(error?.message || '加载创作项目失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateNew = () => {
    setEditingProjectId(null);
    setEditProjectTitle('');
    setEditProjectDescription('');
    setEditAspectRatio('9:16');
    setShowEditModal(true);
  };

  const handleEditBasicInfo = (e: React.MouseEvent, proj: CreationProjectData) => {
    e.stopPropagation();
    setEditingProjectId(proj.projectId);
    setEditProjectTitle(proj.title);
    setEditProjectDescription(proj.description || '');
    setEditAspectRatio(proj.renderAspectRatio || proj.aspectRatio || '9:16');
    setShowEditModal(true);
  };

  const submitProjectBasicInfo = async () => {
    if (!editProjectTitle.trim()) {
      showToast('项目名称不能为空', 'error');
      return;
    }
    setSavingProject(true);
    try {
      if (editingProjectId) {
        await creationApi.updateProject(editingProjectId, editProjectTitle.trim(), editProjectDescription.trim() || undefined, editAspectRatio);
        showToast('项目信息已更新', 'success');
        setShowEditModal(false);
        await loadProjects();
      } else {
        const resp = await creationApi.createProject(editProjectTitle.trim(), editProjectDescription.trim() || undefined, editAspectRatio);
        const newId = resp.data?.projectId;
        if (newId) {
          showToast('创建成功，进入素材提取工作流', 'success');
          setShowEditModal(false);
          navigate(`/create/detail/${newId}/workflow`);
        }
      }
    } catch (error: any) {
      showToast(error?.message || '保存失败', 'error');
    } finally {
      setSavingProject(false);
    }
  };

  const handleEditProject = (proj: CreationProjectData) => {
    navigate(`/create/detail/${proj.projectId}/workflow`);
  };

  const formatStatusLabel = (status?: string) => {
    switch ((status || '').toUpperCase()) {
      case 'COMPOSED':
        return '已完成';
      case 'ADAPTING':
      case 'MATCHING':
        return '工作中';
      case 'FAILED':
        return '失败';
      case 'DRAFT':
      default:
        return '待设定';
    }
  };

  const mapStatusClass = (status?: string): 'working' | 'pending' | 'completed' | 'ai-fill' => {
    switch ((status || '').toUpperCase()) {
      case 'COMPOSED':
        return 'completed';
      case 'ADAPTING':
      case 'MATCHING':
        return 'working';
      case 'FAILED':
        return 'ai-fill';
      case 'DRAFT':
      default:
        return 'pending';
    }
  };

  const formatDate = (time?: string) => {
    if (!time) return '-';
    const d = new Date(time);
    if (Number.isNaN(d.getTime())) return '-';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  return (
    <div className="view-container">
      <div className="project-header">
        <h2>🎬 创作与装配看板</h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            className="input-field"
            style={{ minWidth: '220px', padding: '8px 12px' }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索项目标题/描述"
          />
          <button className="btn-outline" onClick={() => void loadProjects()} disabled={loading}>
            {loading ? '加载中...' : '查询'}
          </button>
        </div>
      </div>

      {!loading && creationProjects.length === 0 && (
        <div style={{ color: '#94a3b8', fontSize: '0.95rem', marginBottom: '16px' }}>
          暂无创作项目，点击“新建装配生成”创建。
        </div>
      )}

      <div className="project-grid">
        {creationProjects.map(proj => (
          <div 
            key={proj.projectId} 
            className="project-card creation" 
            onClick={() => handleEditProject(proj)}
          >
            <div className="card-cover">
              <img src={coverPalette[Math.abs(proj.projectId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)) % coverPalette.length]} alt="cover" />
              <div className={`status-badge ${mapStatusClass(proj.status)}`}>
                {formatStatusLabel(proj.status)}
              </div>
            </div>
            <div className="card-info">
              <h3 className="card-title">{proj.title}</h3>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="card-date">{formatDate(proj.updatedAt || proj.createdAt)}</span>
                <button 
                  style={{
                    background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: '4px',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '4px'
                  }}
                  title="编辑项目信息"
                  onClick={(e) => handleEditBasicInfo(e, proj)}
                  onMouseOver={(e) => e.currentTarget.style.color = '#3b82f6'}
                  onMouseOut={(e) => e.currentTarget.style.color = '#94a3b8'}
                >
                  <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                    <path d="M12 20h9"></path>
                    <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        ))}
        
        <div 
          className="create-project-card creation-mode" 
          onClick={handleCreateNew}
        >
          <div className="plus-icon-circle">
            <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="3" viewBox="0 0 24 24">
              <line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </div>
          <span className="create-card-text">
            新建装配生成
          </span>
        </div>
      </div>

      {showEditModal && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: '500px', width: '90%' }}>
            <h3 style={{ marginBottom: '16px' }}>{editingProjectId ? '编辑创作项目' : '新建创作项目'}</h3>
            <div className="input-group">
              <label className="input-label">项目名称</label>
              <input 
                className="input-field" 
                value={editProjectTitle} 
                onChange={(e) => setEditProjectTitle(e.target.value)} 
                placeholder="请输入项目名称" 
                autoFocus 
              />
            </div>
            <div className="input-group" style={{ marginTop: '16px' }}>
              <label className="input-label">项目描述 (可选)</label>
              <textarea 
                className="input-field" 
                style={{ minHeight: '100px' }}
                value={editProjectDescription} 
                onChange={(e) => setEditProjectDescription(e.target.value)} 
                placeholder="例如：一款洗面奶的带货视频..." 
              />
            </div>
            <div className="input-group" style={{ marginTop: '16px' }}>
              <label className="input-label">画面比例</label>
              <div style={{ display: 'flex', gap: '16px', marginTop: '8px' }}>
                <div 
                  onClick={() => setEditAspectRatio('9:16')}
                  style={{
                    padding: '12px', border: '1px solid #e2e8f0', borderRadius: '8px',
                    cursor: 'pointer', flex: 1, textAlign: 'center',
                    borderColor: editAspectRatio === '9:16' ? '#3b82f6' : '#e2e8f0',
                    backgroundColor: editAspectRatio === '9:16' ? '#eff6ff' : 'white',
                    color: editAspectRatio === '9:16' ? '#1e40af' : '#475569',
                    fontWeight: editAspectRatio === '9:16' ? 500 : 400
                  }}
                >
                  9:16 (竖屏)
                </div>
                <div 
                  onClick={() => setEditAspectRatio('16:9')}
                  style={{
                    padding: '12px', border: '1px solid #e2e8f0', borderRadius: '8px',
                    cursor: 'pointer', flex: 1, textAlign: 'center',
                    borderColor: editAspectRatio === '16:9' ? '#3b82f6' : '#e2e8f0',
                    backgroundColor: editAspectRatio === '16:9' ? '#eff6ff' : 'white',
                    color: editAspectRatio === '16:9' ? '#1e40af' : '#475569',
                    fontWeight: editAspectRatio === '16:9' ? 500 : 400
                  }}
                >
                  16:9 (横屏)
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '24px' }}>
              <button className="btn-outline" onClick={() => setShowEditModal(false)} disabled={savingProject}>
                取消
              </button>
              <button className="btn-primary" onClick={submitProjectBasicInfo} disabled={savingProject || !editProjectTitle.trim()}>
                {savingProject ? '保存中...' : '确认'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
