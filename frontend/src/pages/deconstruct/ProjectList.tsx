import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Project, DeconstructProjectApiItem } from '../../types';
import { deconstructApi } from '../../api/deconstruct';
import { MOCK_DECONSTRUCT_PROJECTS } from '../../mock/data';

export default function ProjectList() {
  const navigate = useNavigate();
  const [deconstructProjects, setDeconstructProjects] = useState<Project[]>(MOCK_DECONSTRUCT_PROJECTS);

  const loadProjects = async () => {
    try {
      const resp = await deconstructApi.getProjects();
      if (resp.code !== '0' && resp.code !== '200') {
        throw new Error(resp.message || '获取列表失败');
      }
      const mapped: Project[] = (resp.data.list || []).map((item: DeconstructProjectApiItem) => ({
        id: item.id,
        title: item.title,
        description: item.description || '',
        tags: item.tags || [],
        cover: item.coverUrl || 'https://images.unsplash.com/photo-1611162617474-5b21e879e113?ixlib=rb-4.0.3',
        status: item.status === 'COMPLETED' ? 'completed' : item.status === 'PENDING' ? 'pending' : 'working',
        date: item.createdAt ? item.createdAt.split('T')[0] : new Date().toISOString().split('T')[0]
      }));
      setDeconstructProjects(mapped);
    } catch (err) {
      console.warn('API 未就绪，使用 Mock 列表数据', err);
      // Keep using MOCK_DECONSTRUCT_PROJECTS
    }
  };

  useEffect(() => {
    void loadProjects();
  }, []);

  const handleCreateNew = () => {
    navigate('/deconstruct/detail/new');
  };

  const handleEditProject = (proj: Project) => {
    navigate(`/deconstruct/detail/${proj.id}`);
  };

  return (
    <div className="view-container">
      <div className="project-header">
        <h2>{/* we don't have workflow prop anymore but it's okay */}🎯 结构拆解看板</h2>
      </div>

        {/* Project Grid */}
        <div className="project-grid">
          {deconstructProjects.map(proj => (
            <div 
              key={proj.id} 
              className={`project-card`} 
              onClick={() => handleEditProject(proj)}
            >
              <div className="card-cover">
                <img src={proj.cover} alt="cover" />
                <div className={`status-badge ${proj.status}`}>
                  {proj.status === 'working' ? '工作中' : 
                   proj.status === 'completed' ? '已完成' : 
                   proj.status === 'ai-fill' ? 'AI补全中' : '待设定'}
                </div>
              </div>
              <div className="card-info">
                <h3 className="card-title">{proj.title}</h3>
                <span className="card-date">{proj.date}</span>
              </div>
            </div>
          ))}
          
          {/* Create Card at the end */}
          <div 
            className={`create-project-card`} 
            onClick={handleCreateNew}
          >
            <div className="plus-icon-circle">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="3" viewBox="0 0 24 24">
                <line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
            </div>
            <span className="create-card-text">新建拆解项目</span>
          </div>
        </div>
    </div>
  );
}
