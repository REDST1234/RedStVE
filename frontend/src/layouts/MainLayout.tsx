import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';

export default function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isCreation = location.pathname.startsWith('/create');
  
  const [searchFilter, setSearchFilter] = useState('全部状态');
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  const handleMenuClick = (targetWorkflow: 'deconstruct' | 'create') => {
    navigate(`/${targetWorkflow}`);
  };

  return (
    <div className="app-container">
      {/* --- Sidebar --- */}
      <aside className="sidebar">
        <div className="logo-area">
          <div className="logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
          </div>
          <span className="logo-text">VideoDecon</span>
        </div>

        <div className="user-profile">
          <img className="avatar" src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=facearea&facepad=2&w=100&h=100&q=80" alt="User" />
          <div className="user-info">
            <span className="user-name">Creator Admin</span>
            <span className="user-role">Pro 账户</span>
          </div>
        </div>

        <div className="nav-section-title">🎯 结构拆解中心</div>
        <nav className="nav-menu">
          <div 
            className={`nav-item ${!isCreation ? 'active' : ''}`}
            onClick={() => handleMenuClick('deconstruct')}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
            拆解看板
          </div>
          <div className="nav-item"><svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M4 22h14a2 2 0 0 0 2-2V7.5L14.5 2H6a2 2 0 0 0-2 2v4"></path><polyline points="14 2 14 8 20 8"></polyline><path d="M2 15h10"></path><path d="M9 18l3-3-3-3"></path></svg> 拆解资产库</div>
        </nav>

        <div className="nav-section-title" style={{marginTop: '24px'}}>🎬 视频创作工坊</div>
        <nav className="nav-menu">
          <div 
            className={`nav-item ${isCreation ? 'active' : ''}`}
            onClick={() => handleMenuClick('create')}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
            装配看板
          </div>
          <div className="nav-item"><svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg> 导出与发布</div>
        </nav>
      </aside>

      {/* --- Main Content --- */}
      <main className="main-wrapper">
        {/* Global Header */}
        <header className="top-header">
          <div className="search-bar">
            <svg className="search-icon" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input type="text" placeholder="全网搜索关键词、匹配视频..." />
            
            <div className="search-divider"></div>
            
            <div className="search-filter" onClick={() => setIsFilterOpen(!isFilterOpen)}>
              <div className="filter-display-content">
                {searchFilter !== '全部状态' && <div className={`status-dot ${searchFilter === '工作中' ? 'working' : searchFilter === '已完成' ? 'completed' : 'pending'}`}></div>}
                <span>{searchFilter}</span>
              </div>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"></polyline></svg>
              
              {isFilterOpen && (
                <div className="filter-dropdown">
                  {['全部状态', '待设定', '工作中', '已完成', '已归档'].map(f => (
                    <div 
                      key={f} 
                      className={`filter-option ${searchFilter === f ? 'selected' : ''}`}
                      onClick={(e) => { 
                        e.stopPropagation(); 
                        setSearchFilter(f); 
                        setIsFilterOpen(false); 
                      }}
                    >
                      {f !== '全部状态' && <div className={`status-dot ${f === '工作中' ? 'working' : f === '已完成' ? 'completed' : 'pending'}`}></div>}
                      {f}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          
          <div className="header-actions">
            <button className="action-btn">
              <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
              </svg>
            </button>
            <img className="avatar" style={{width: 32, height: 32}} src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=facearea&facepad=2&w=100&h=100&q=80" alt="User" />
          </div>
        </header>

        <section className="content-area">
          {/* This renders the child routes (ProjectList, ProjectDetail, etc.) */}
          <Outlet />
        </section>
      </main>
    </div>
  );
}
