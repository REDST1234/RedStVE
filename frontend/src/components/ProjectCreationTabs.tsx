import React from 'react';
import { useNavigate } from 'react-router-dom';

type ProjectCreationTabsProps = {
  projectId: string;
  activeTab: 'detail' | 'workflow' | 'recommendation' | 'gap-detection' | 'generation';
};

export const ProjectCreationTabs: React.FC<ProjectCreationTabsProps> = ({ projectId, activeTab }) => {
  const navigate = useNavigate();

  const tabs = [
    { id: 'detail', label: '1. 创作项目总览', path: `/create/detail/${projectId}` },
    { id: 'workflow', label: '2. 素材提取', path: `/create/detail/${projectId}/workflow` },
    { id: 'recommendation', label: '3. 智能推荐匹配', path: `/create/detail/${projectId}/recommendation` },
    { id: 'gap-detection', label: '4. 素材缺口识别', path: `/create/detail/${projectId}/gap-detection` },
    { id: 'generation', label: '5. 视频生成', path: `/create/detail/${projectId}/generation` },
  ];

  const handleTabClick = (path: string, id: string) => {
    // If it is a new project, only 'detail' is available.
    if (!projectId || projectId === 'new') {
      if (id !== 'detail') {
        alert('请先保存项目基础信息');
        return;
      }
    }
    navigate(path);
  };

  return (
    <div style={{ display: 'flex', gap: '8px', padding: '16px 32px 0', borderBottom: '1px solid #e2e8f0', marginBottom: '16px' }}>
      {tabs.map((tab) => {
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => handleTabClick(tab.path, tab.id)}
            style={{
              padding: '10px 20px',
              background: 'transparent',
              border: 'none',
              borderBottom: isActive ? '2px solid #0ea5e9' : '2px solid transparent',
              color: isActive ? '#0ea5e9' : '#64748b',
              fontWeight: isActive ? 600 : 500,
              fontSize: '0.95rem',
              cursor: 'pointer',
              transition: 'all 0.2s',
              marginBottom: '-1px'
            }}
            onMouseOver={(e) => { if (!isActive) e.currentTarget.style.color = '#0f172a'; }}
            onMouseOut={(e) => { if (!isActive) e.currentTarget.style.color = '#64748b'; }}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
};
