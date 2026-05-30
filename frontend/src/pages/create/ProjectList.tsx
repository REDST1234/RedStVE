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
    navigate('/create/detail/new');
  };

  const handleEditProject = (proj: CreationProjectData) => {
    navigate(`/create/detail/${proj.projectId}`);
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
              <span className="card-date">{formatDate(proj.updatedAt || proj.createdAt)}</span>
            </div>
          </div>
        ))}
        {!loading && creationProjects.length === 0 && (
          <div style={{ color: '#94a3b8', fontSize: '0.95rem' }}>暂无创作项目，点击“新建装配生成”创建。</div>
        )}
        
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
    </div>
  );
}
