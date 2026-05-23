import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/ToastContext';
import { Project } from '../../types';
import { MOCK_CREATION_PROJECTS } from '../../mock/data';

export default function CreateProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [editingProject] = useState<Project | null>(
    MOCK_CREATION_PROJECTS.find(p => p.id === id) || null
  );
  const [creationStep, setCreationStep] = useState<'info' | 'workspace'>('info');
  const [isAiAnalyzed, setIsAiAnalyzed] = useState(false);

  const navigateToList = () => navigate('/create');

  const handleSaveAndReturn = () => {
    showToast('草稿已保存', 'success');
    navigateToList();
  };

  return (
    <div className="detail-page fade-in" style={{borderColor: '#e0e7ff'}}>
      <div className="detail-header" style={{background: '#f8fafc', paddingBottom: '20px'}}>
        <button className="back-btn" onClick={() => {
          if (creationStep === 'workspace') {
            setCreationStep('info');
          } else {
            navigateToList();
          }
        }} title="返回">
          <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
          </svg>
        </button>
        <h2 className="detail-title">{editingProject ? '编辑装配生成项目' : '新建结构装配项目'}</h2>
      </div>

      <div className="detail-body" style={{paddingTop: '20px', display: 'block'}}>
        {creationStep === 'info' ? (
          /* --- 阶段一：创作基本信息 --- */
          <div style={{display: 'flex', flexDirection: 'column', gap: '24px', maxWidth: '800px', margin: '0 auto', paddingTop: '20px'}}>
            <div className="input-group">
              <label className="input-label">视频名称 (Video Title)</label>
              <input type="text" className="input-field" placeholder="例如：2026春季降噪耳机高燃混剪" defaultValue={editingProject?.title} />
            </div>

            <div className="input-group">
              <label className="input-label">视频描述 (Description & Requirements)</label>
              <textarea className="input-field" style={{minHeight: '120px'}} placeholder="简述该视频的最终呈现目标和核心风格..."></textarea>
            </div>

            <div className="footer-actions" style={{justifyContent: 'center', marginTop: '40px'}}>
              <button className="btn-primary" onClick={() => setCreationStep('workspace')}>
                保存并前往工作台
                <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"></polyline></svg>
              </button>
            </div>
          </div>
        ) : (
          /* --- 阶段二：核心装配工作台 --- */
          <div style={{display: 'flex', gap: '24px', height: 'calc(100vh - 200px)'}}>
            {/* Left: AI Blueprint */}
            <div style={{flex: 1, borderRight: '1px solid #e2e8f0', paddingRight: '24px', overflowY: 'auto'}}>
              <div className="canvas-header" style={{marginBottom: '16px'}}>
                <div className="canvas-title">智能结构装配图纸 (AI Blueprint)</div>
              </div>
              <div className="blueprint-container">
                {isAiAnalyzed ? (
                  <div className="blueprint-node filled">
                    <div className="node-icon">✨</div>
                    <div className="node-content">
                      <h4>分析完成，已补全结构</h4>
                      <p>AI 已根据您的核心卖点推荐了相关片段和转场...</p>
                    </div>
                  </div>
                ) : (
                  <div className="blueprint-node empty">
                    <div className="node-icon">?</div>
                    <div className="node-content">
                      <h4>等待 AI 分析</h4>
                      <p>请在右侧输入您的卖点并上传素材，点击运行分析...</p>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Right: User Input and Assets Assembly */}
            <div className="creation-canvas" style={{flex: 1, display: 'flex', flexDirection: 'column', gap: '20px'}}>
              <div className="canvas-header" style={{marginBottom: '16px'}}>
                <div className="canvas-title">基础素材与核心卖点配置</div>
                <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                  <span style={{fontSize: '0.85rem', color: '#64748b'}}>目标模板:</span>
                  <select className="input-field" style={{padding: '6px 12px', minWidth: '200px'}}>
                    <option>苹果春季发布会(混剪)</option>
                    <option>百大UP主解说结构(影视)</option>
                  </select>
                </div>
              </div>

              <div className="upload-dropzone">
                <div className="upload-icon">
                  <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line>
                  </svg>
                </div>
                <div>
                  <div style={{fontWeight: 600, color: '#0f172a', marginBottom: '4px'}}>拖拽上传基础素材</div>
                  <div style={{fontSize: '0.85rem'}}>支持 视频 / 图片 / 文案片段 (上限 500MB)</div>
                </div>
              </div>

              <div className="creation-form" style={{flex: 1, display: 'flex', flexDirection: 'column'}}>
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
        )}
      </div>
    </div>
  );
}
