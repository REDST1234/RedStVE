import { useState, useEffect } from 'react';
import './index.css';

// --- Mock Data & Types ---
interface Project {
  id: string;
  title: string;
  cover: string;
  status: 'working' | 'pending' | 'completed' | 'ai-fill';
  date: string;
  description: string;
  tags: string[];
}

const MOCK_DECONSTRUCT_PROJECTS: Project[] = [
  {
    id: 'd1',
    title: '苹果春季发布会混剪拆解',
    cover: 'https://images.unsplash.com/photo-1611162617474-5b21e879e113?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
    status: 'working',
    date: '2026-05-20',
    description: '核心提炼苹果发布会的节奏感与卡点技巧，用于数码区评测。',
    tags: ['混剪', '营销']
  },
  {
    id: 'd2',
    title: 'B站百大UP主影视解说结构',
    cover: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
    status: 'completed',
    date: '2026-05-18',
    description: '分析前三分钟的黄金悬念设置，以及情绪曲线推进。',
    tags: ['影视', '从零']
  }
];

const MOCK_CREATION_PROJECTS: Project[] = [
  {
    id: 'c1',
    title: '耳机新品发售-节奏混剪版',
    cover: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
    status: 'ai-fill',
    date: '2026-05-21',
    description: '应用 [苹果春季发布会] 结构，目前素材存在缺口，正在使用 AI 补全。',
    tags: ['营销', '结构迁移']
  }
];

export default function App() {
  // App Core State
  const [workflow, setWorkflow] = useState<'deconstruct' | 'create'>('deconstruct');
  const [currentView, setCurrentView] = useState<'list' | 'detail'>('list');
  const [isAiAnalyzed, setIsAiAnalyzed] = useState(false);
  const [creationStep, setCreationStep] = useState<'info' | 'workspace'>('info');
  
  const [activeMenu, setActiveMenu] = useState('项目看板');
  
  // Data State
  const [deconstructProjects, setDeconstructProjects] = useState<Project[]>(MOCK_DECONSTRUCT_PROJECTS);
  const [creationProjects, setCreationProjects] = useState<Project[]>(MOCK_CREATION_PROJECTS);
  
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  
  // Search state
  const [searchFilter, setSearchFilter] = useState('全部状态');
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  // Browser History Management
  useEffect(() => {
    const handlePopState = () => {
      if (currentView === 'detail') {
        if (workflow === 'create' && creationStep === 'workspace') {
          setCreationStep('info');
          // re-push state to prevent actual navigation if we only wanted to go back a step
          window.history.pushState({ view: 'detail' }, '', '#detail');
        } else {
          setCurrentView('list');
        }
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [currentView, workflow, creationStep]);

  const navigateToDetail = () => {
    window.history.pushState({ view: 'detail' }, '', '#detail');
    setCurrentView('detail');
  };

  const navigateToList = () => {
    if (window.location.hash === '#detail') {
      window.history.back(); // This will trigger popstate and set currentView to 'list'
    } else {
      setCurrentView('list');
    }
  };

  // --- Handlers ---
  const handleMenuClick = (targetWorkflow: 'deconstruct' | 'create', menuName: string) => {
    setWorkflow(targetWorkflow);
    setActiveMenu(menuName);
    setCurrentView('list');
  };

  const handleCreateNew = () => {
    setEditingProject(null);
    setIsAiAnalyzed(false);
    setCreationStep('info');
    navigateToDetail();
  };

  const handleEditProject = (proj: Project) => {
    setEditingProject(proj);
    setIsAiAnalyzed(proj.status === 'ai-fill' || proj.status === 'completed');
    setCreationStep('info');
    navigateToDetail();
  };

  const handleSaveAndReturn = () => {
    if (!editingProject) {
      if (workflow === 'deconstruct') {
        const newProj: Project = {
          id: Date.now().toString(),
          title: '全新导入的视频拆解项目',
          cover: 'https://images.unsplash.com/photo-1579546929518-9e396f3cc809?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
          status: 'pending',
          date: new Date().toISOString().split('T')[0],
          description: '等待进行结构提取与片段解析...',
          tags: ['混剪']
        };
        setDeconstructProjects([...deconstructProjects, newProj]);
      } else {
        const newProj: Project = {
          id: Date.now().toString(),
          title: '未命名的装配生成项目',
          cover: 'https://images.unsplash.com/photo-1498050108023-c5249f4df085?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
          status: 'working',
          date: new Date().toISOString().split('T')[0],
          description: '基于所选结构模板进行的全新内容拼装。',
          tags: ['结构迁移']
        };
        setCreationProjects([...creationProjects, newProj]);
      }
    }
    navigateToList();
  };

  // Render Helpers
  const renderProjectGrid = (projects: Project[], isCreation: boolean) => (
    <div className="project-grid">
      {projects.map(proj => (
        <div 
          key={proj.id} 
          className={`project-card ${isCreation ? 'creation' : ''}`} 
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
        className={`create-project-card ${isCreation ? 'creation-mode' : ''}`} 
        onClick={handleCreateNew}
      >
        <div className="plus-icon-circle">
          <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="3" viewBox="0 0 24 24">
            <line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
        </div>
        <span className="create-card-text">
          {isCreation ? '新建装配生成' : '新建拆解项目'}
        </span>
      </div>
    </div>
  );

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
            <span className="user-name">创作者 Alita</span>
            <span className="user-role">Pro 账户</span>
          </div>
        </div>

        <div className="nav-section-title">🎯 结构拆解中心</div>
        <nav className="nav-menu">
          <div 
            className={`nav-item ${workflow === 'deconstruct' && activeMenu === '项目看板' ? 'active' : ''}`}
            onClick={() => handleMenuClick('deconstruct', '项目看板')}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
            拆解看板
          </div>
          <div className="nav-item"><svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M4 22h14a2 2 0 0 0 2-2V7.5L14.5 2H6a2 2 0 0 0-2 2v4"></path><polyline points="14 2 14 8 20 8"></polyline><path d="M2 15h10"></path><path d="M9 18l3-3-3-3"></path></svg> 拆解资产库</div>
        </nav>

        <div className="nav-section-title" style={{marginTop: '24px'}}>🎬 视频创作工坊</div>
        <nav className="nav-menu">
          <div 
            className={`nav-item ${workflow === 'create' && activeMenu === '创作看板' ? 'active' : ''}`}
            onClick={() => handleMenuClick('create', '创作看板')}
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
          {/* ======================================= */}
          {/* LIST VIEW                               */}
          {/* ======================================= */}
          {currentView === 'list' && (
            <div className="view-container">
              <div className="project-header">
                <h2>{workflow === 'deconstruct' ? '🎯 结构拆解看板' : '🎬 创作与装配看板'}</h2>
              </div>
              {renderProjectGrid(workflow === 'deconstruct' ? deconstructProjects : creationProjects, workflow === 'create')}
            </div>
          )}

          {/* ======================================= */}
          {/* DETAIL VIEW                             */}
          {/* ======================================= */}
          {currentView === 'detail' && workflow === 'deconstruct' && (
            /* --- P_003 拆解结构面板 --- */
            <div className="detail-page">
              <div className="detail-header">
                <button className="back-btn" onClick={() => setCurrentView('list')} title="返回项目看板">
                  <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                    <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
                  </svg>
                </button>
                <h2 className="detail-title">{editingProject ? '编辑拆解详情' : '新建结构拆解项目'}</h2>
                
                <button className="btn-jump-flat" style={{marginLeft: 'auto'}} title="前往提取核心要素">
                  <span>结构提取</span>
                  <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                </button>
              </div>

              <div className="detail-body">
                <div className="detail-left">
                  <div className="cover-preview">
                    {editingProject ? (
                      <img src={editingProject.cover} alt="cover" />
                    ) : (
                      <div className="cover-placeholder">
                        <svg width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                        <span>暂无封面</span>
                      </div>
                    )}
                  </div>
                  
                  <div className="params-box">
                    <div className="params-title">详细参数 (Details)</div>
                    <div className="param-row"><span className="param-label">时长</span><span className="param-val">00:00:00</span></div>
                    <div className="param-row"><span className="param-label">分辨率</span><span className="param-val">---- x ----</span></div>
                    <div className="param-row"><span className="param-label">FPS</span><span className="param-val">--</span></div>
                    <div className="param-row"><span className="param-label">镜头数</span><span className="param-val">0</span></div>
                  </div>
                </div>

                <div className="detail-mid" style={{flexDirection: 'row', gap: '32px'}}>
                  <div className="input-group" style={{flex: 1}}>
                    <div className="input-group">
                      <label className="input-label">项目标题 (Title)</label>
                      <input type="text" className="input-field" placeholder="输入爆款视频解析项目标题..." defaultValue={editingProject?.title} />
                    </div>
                    
                    <div className="input-group" style={{marginTop: '24px'}}>
                      <label className="input-label">项目描述 (Description)</label>
                      <textarea className="input-field" placeholder="记录提取与结构拆解的核心目标..." defaultValue={editingProject?.description}></textarea>
                    </div>

                    <div className="input-group" style={{marginTop: '24px'}}>
                      <label className="input-label">品类标签 (Tags)</label>
                      <div className="tags-container">
                        {['混剪', '营销', '影视', '从零', '电商', 'Vlog'].map(tag => (
                          <div key={tag} className={`tag-btn ${(editingProject?.tags.includes(tag)) || (!editingProject && tag === '混剪') ? 'selected' : ''}`}>
                            {tag}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* 📜 多路归一日志 (Timeline Log) */}
                  <div className="timeline-log-panel">
                    <div className="log-header">
                      <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                      样例多路归一日志 (Timeline Log)
                    </div>
                    <div className="log-body">
                      <div className="log-row">
                        <span className="log-time">[0.0s - 3.2s]</span>
                        <div className="log-badge asr">🔵 ASR台词</div>
                        <span className="log-text">“大一早八顶不住？那是...”</span>
                      </div>
                      <div className="log-row">
                        <span className="log-time">[1.2s]</span>
                        <div className="log-badge physics">🟠 物理异动</div>
                        <span className="log-text highlight">检测到像素突变 + 转场音效波峰</span>
                      </div>
                      <div className="log-row">
                        <span className="log-time">[3.2s - 12.0s]</span>
                        <div className="log-badge asr">🔵 ASR台词</div>
                        <span className="log-text">“纯正手作咖啡，10秒瞬间让你清醒回到巅峰状态！”</span>
                      </div>
                      <div className="log-row">
                        <span className="log-time">[8.5s]</span>
                        <div className="log-badge physics">🟠 物理异动</div>
                        <span className="log-text">人物骨骼大幅度变化 (挥手动作)</span>
                      </div>
                      <div className="log-row">
                        <span className="log-time">[12.0s - 15.0s]</span>
                        <div className="log-badge asr">🔵 ASR台词</div>
                        <span className="log-text">“点击下方链接，享受专属于你的清晨...”</span>
                      </div>
                      <div className="log-row">
                        <span className="log-time">[13.5s]</span>
                        <div className="log-badge physics">🟠 物理异动</div>
                        <span className="log-text highlight">购物车弹框 UI 识别提取</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="detail-footer" style={{justifyContent: 'flex-end'}}>
                <div className="footer-actions">
                  <button className="btn-primary" onClick={handleSaveAndReturn}>保存拆解项目</button>
                </div>
              </div>
            </div>
          )}

          {currentView === 'detail' && workflow === 'create' && (
            /* --- P_Create_003 视频创作工坊专属面板 --- */
            <div className="detail-page fade-in" style={{borderColor: '#e0e7ff'}}>
              <div className="detail-header" style={{background: '#f8fafc', paddingBottom: '20px'}}>
                <button className="back-btn" onClick={() => {
                  if (creationStep === 'workspace') {
                    setCreationStep('info');
                  } else {
                    navigateToList();
                  }
                }} title="返回">
                  <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
                </button>
                <h2 className="detail-title">{editingProject ? '编辑装配生成项目' : '新建结构装配项目'}</h2>
              </div>

              <div className="detail-body" style={{paddingTop: '20px', display: 'block'}}>
                
                {creationStep === 'info' ? (
                  /* --- 阶段一：创作基本信息态 --- */
                  <div style={{display: 'flex', flexDirection: 'column', gap: '24px', maxWidth: '800px', margin: '0 auto', paddingTop: '20px'}}>
                    <div className="input-group">
                      <label className="input-label">创作项目标题 (Title)</label>
                      <input type="text" className="input-field" placeholder="例如：某品牌降噪耳机新品发售..." defaultValue={editingProject?.title} />
                    </div>
                    
                    <div className="input-group">
                      <label className="input-label">创作目标描述 (Description)</label>
                      <textarea className="input-field" style={{minHeight: '120px'}} placeholder="记录本次装配生成的核心目标..." defaultValue={editingProject?.description}></textarea>
                    </div>

                    <div className="input-group">
                      <label className="input-label">选择目标视频比例 (Aspect Ratio)</label>
                      <div className="ratio-group" style={{width: 'fit-content', background: '#f1f5f9', padding: '6px', borderRadius: '8px'}}>
                        <button className="ratio-btn" style={{padding: '8px 24px'}}>1:1</button>
                        <button className="ratio-btn" style={{padding: '8px 24px'}}>3:4</button>
                        <button className="ratio-btn active" style={{padding: '8px 24px', background: 'white', boxShadow: '0 1px 3px rgba(0,0,0,0.1)'}}>9:16 (竖屏)</button>
                      </div>
                    </div>

                    <div style={{marginTop: '40px', display: 'flex', justifyContent: 'flex-end', gap: '16px'}}>
                      <button className="btn-outline" onClick={handleSaveAndReturn}>保存信息</button>
                      <button className="btn-primary" style={{background: '#6366f1', padding: '12px 32px', fontSize: '1.05rem'}} onClick={() => setCreationStep('workspace')}>
                        🚀 开启创作视频工作流
                      </button>
                    </div>
                  </div>
                ) : (
                  /* --- 阶段二：装配工作区 --- */
                  <div className="creation-workspace fade-in">
                    <div className="creation-layout">
                      {/* Left: Structure Timeline Slots (AI Strategy Tree) */}
                    <div className="creation-timeline" style={{width: '380px'}}>
                      <div style={{fontSize: '0.9rem', fontWeight: 600, color: '#0f172a', marginBottom: '16px'}}>
                        {isAiAnalyzed ? '✨ AI 智能匹配与补全方案' : '📐 模板骨架 (等待填充)'}
                      </div>
                      
                      {!isAiAnalyzed ? (
                        /* 初始状态：只展示空骨架 */
                        <>
                          <div className="timeline-slot">
                            <div className="slot-header"><span className="slot-title">1. 悬念引入</span><span className="slot-duration">00:00 - 00:03</span></div>
                            <span className="slot-desc">高燃节奏，提出痛点问题。</span>
                            <div className="slot-content-indicator" style={{color: '#94a3b8'}}>等待匹配...</div>
                          </div>
                          <div className="timeline-slot">
                            <div className="slot-header"><span className="slot-title">2. 痛点放大</span><span className="slot-duration">00:03 - 00:10</span></div>
                            <span className="slot-desc">结合特定场景，展示负面现状。</span>
                            <div className="slot-content-indicator" style={{color: '#94a3b8'}}>等待匹配...</div>
                          </div>
                          <div className="timeline-slot">
                            <div className="slot-header"><span className="slot-title">3. 卖点反转</span><span className="slot-duration">00:10 - 00:15</span></div>
                            <span className="slot-desc">产品出场，快速混剪核心功能。</span>
                            <div className="slot-content-indicator" style={{color: '#94a3b8'}}>等待匹配...</div>
                          </div>
                        </>
                      ) : (
                        /* 分析后状态：展示匹配结果与 AI 生成方案 */
                        <>
                          <div className="timeline-slot filled">
                            <div className="slot-header"><span className="slot-title">1. 悬念引入</span><span className="slot-duration">00:00 - 00:03</span></div>
                            <span className="slot-desc">高燃节奏，提出痛点问题。</span>
                            <div className="slot-content-indicator success">
                              <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
                              已匹配: VID_001_开场悬念.mp4
                            </div>
                          </div>
                          
                          <div className="timeline-slot gap">
                            <div className="slot-header"><span className="slot-title">2. 痛点放大</span><span className="slot-duration">00:03 - 00:10</span></div>
                            <span className="slot-desc">结合特定场景，展示负面现状。</span>
                            <div className="slot-content-indicator gap-warning">
                              <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
                              素材不足，已部署 AI 补全策略
                            </div>
                            <div className="ai-plan-box">
                              <div className="ai-plan-title">
                                <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
                                AIGC 生成任务
                              </div>
                              基于您的核心卖点，将调用视频模型生成一段 7s 的产品环境痛点展示特写，并配以压抑情绪的音效。
                            </div>
                          </div>
                        </>
                      )}
                    </div>

                    {/* Right: User Input and Assets Assembly */}
                    <div className="creation-canvas">
                      <div className="canvas-header" style={{marginBottom: '16px'}}>
                        <div className="canvas-title">基础素材与核心卖点配置</div>
                        <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                          <span style={{fontSize: '0.85rem', color: '#64748b'}}>目标模板:</span>
                          <select className="input-field" style={{padding: '6px 12px', minWidth: '200px'}}>
                            <option>苹果春季发布会 (混剪)</option>
                            <option>百大UP主解说结构 (影视)</option>
                          </select>
                        </div>
                      </div>

                      <div className="upload-dropzone">
                        <div className="upload-icon">
                          <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                        </div>
                        <div>
                          <div style={{fontWeight: 600, color: '#0f172a', marginBottom: '4px'}}>拖拽上传基础素材</div>
                          <div style={{fontSize: '0.85rem'}}>支持 视频 / 图片 / 文案片段 (上限 500MB)</div>
                        </div>
                      </div>

                      <div className="creation-form">
                        <div className="input-group">
                          <label className="input-label">核心卖点 (Core Selling Points)</label>
                          <textarea className="input-field" style={{minHeight: '80px'}} placeholder="请列出最希望视频突出的卖点，AI将在缺口补全时深度结合这些信息..."></textarea>
                        </div>
                        
                        {!isAiAnalyzed && (
                          <button className="btn-magic" style={{marginTop: 'auto', alignSelf: 'flex-start'}} onClick={() => setIsAiAnalyzed(true)}>
                            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
                            运行 AI 智能匹配与缺口识别
                          </button>
                        )}
                      </div>

                      <div className="creation-footer">
                        <div className="creation-stats">
                          {isAiAnalyzed && (
                            <>
                              <div className="stat-chip">预计总时长: <strong>00:45</strong></div>
                              <div className="stat-chip">需补全槽位: <strong style={{color:'#d97706'}}>1</strong></div>
                            </>
                          )}
                        </div>
                        
                        <div className="footer-actions">
                          <button className="btn-outline" onClick={handleSaveAndReturn}>保存草稿</button>
                          <button 
                            className="btn-primary" 
                            style={{background: isAiAnalyzed ? '#10b981' : '#cbd5e1', cursor: isAiAnalyzed ? 'pointer' : 'not-allowed'}} 
                            onClick={() => { if(isAiAnalyzed) handleSaveAndReturn(); }}
                          >
                            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                            前往生成视频阶段
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Bottom: Uploaded Materials Library */}
                  <div className="assets-library-section">
                      <div className="assets-library-header">
                        <div style={{fontSize: '1rem', fontWeight: 600, color: '#0f172a', display: 'flex', alignItems: 'center', gap: '8px'}}>
                          <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
                          本地素材库 (Uploaded Assets)
                        </div>
                        <span style={{fontSize: '0.85rem', color: '#64748b', background: '#f1f5f9', padding: '4px 10px', borderRadius: '12px'}}>共计 3 个可用素材</span>
                      </div>
                      
                      <div className="assets-grid">
                        <div className="asset-card">
                          <div className="asset-thumb">
                            <img src="https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?auto=format&fit=crop&w=300&q=80" alt="thumb" />
                            <span className="asset-badge">0:05</span>
                          </div>
                          <div className="asset-info">
                            <span className="asset-name" title="VID_001_开场悬念.mp4">VID_001_开场悬念.mp4</span>
                            <span className="asset-type">视频片段</span>
                          </div>
                        </div>

                        <div className="asset-card">
                          <div className="asset-thumb">
                            <img src="https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=300&q=80" alt="thumb" />
                          </div>
                          <div className="asset-info">
                            <span className="asset-name" title="IMG_产品特写_01.jpg">IMG_产品特写_01.jpg</span>
                            <span className="asset-type">静态图片</span>
                          </div>
                        </div>

                        <div className="asset-card">
                          <div className="asset-thumb text-thumb">
                            <svg width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                          </div>
                          <div className="asset-info">
                            <span className="asset-name" title="口播文案_第一版.txt">口播文案_第一版.txt</span>
                            <span className="asset-type">"全新降噪，让世界..."</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

              </div>
            </div>
          )}

        </section>
      </main>
    </div>
  );
}
