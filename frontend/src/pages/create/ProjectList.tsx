import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Project } from '../../types';
import { MOCK_CREATION_PROJECTS } from '../../mock/data';

export default function CreateProjectList() {
  const navigate = useNavigate();
  const [creationProjects] = useState<Project[]>(MOCK_CREATION_PROJECTS);

  const handleCreateNew = () => {
    navigate('/create/detail/new');
  };

  const handleEditProject = (proj: Project) => {
    navigate(`/create/detail/${proj.id}`);
  };

  return (
    <div className="view-container">
      <div className="project-header">
        <h2>🎬 创作与装配看板</h2>
      </div>

      <div className="project-grid">
        {creationProjects.map(proj => (
          <div 
            key={proj.id} 
            className="project-card creation" 
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
