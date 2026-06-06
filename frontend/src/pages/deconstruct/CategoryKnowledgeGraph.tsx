import React, { useEffect, useState, useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { useToast } from '../../contexts/ToastContext';

import { request } from '../../services/http';
import { ApiResponse } from '../../types';
import BgmKnowledgeBase from './BgmKnowledgeBase';

export default function CategoryKnowledgeGraph() {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState<'category' | 'bgm'>('category');
  const [loading, setLoading] = useState(true);
  const [graphData, setGraphData] = useState<{ categories: any[]; templates: any[] }>({ categories: [], templates: [] });
  const [selectedNode, setSelectedNode] = useState<any>(null);

  useEffect(() => {
    fetchGraphData();
  }, []);

  const fetchGraphData = async () => {
    try {
      const res = await request<ApiResponse<{ categories: any[]; templates: any[] }>>('/v1/categories/graph-data');
      if (res.code !== '0') throw new Error(res.message);
      setGraphData(res.data);
    } catch (err: any) {
      showToast(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const option = useMemo(() => {
    const nodes: any[] = [];
    const links: any[] = [];

    // Categories
    graphData.categories.forEach((cat) => {
      nodes.push({
        id: `cat-${cat.categoryId}`,
        name: cat.categoryName || cat.categoryId,
        category: 0,
        symbolSize: 45,
        itemStyle: { 
          color: '#3b82f6',
          borderColor: '#ffffff',
          borderWidth: 1.5,
          shadowBlur: 15,
          shadowColor: '#3b82f6'
        },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          color: 'rgba(255,255,255,0.9)',
          fontSize: 14,
          textBorderColor: 'rgba(0,0,0,0.8)',
          textBorderWidth: 2
        },
        raw: { ...cat, type: 'CATEGORY' }
      });
    });

    // Templates
    graphData.templates.forEach((tpl) => {
      const nodeId = `tpl-${tpl.id}`;
      nodes.push({
        id: nodeId,
        name: tpl.templateName || tpl.templateId,
        category: 1,
        symbolSize: 25,
        itemStyle: { 
          color: '#10b981',
          borderColor: '#ffffff',
          borderWidth: 1.5,
          shadowBlur: 10,
          shadowColor: '#10b981'
        },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          color: 'rgba(255,255,255,0.8)',
          fontSize: 12,
          textBorderColor: 'rgba(0,0,0,0.8)',
          textBorderWidth: 2
        },
        raw: { ...tpl, type: 'TEMPLATE' }
      });
      // Link to Category
      if (tpl.categoryId) {
        links.push({
          source: nodeId,
          target: `cat-${tpl.categoryId}`,
          lineStyle: {
            width: 2,
            opacity: 0.6
          }
        });
      }
    });

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15, 23, 42, 0.9)',
        borderColor: 'rgba(255, 255, 255, 0.1)',
        textStyle: { color: '#fff' },
        formatter: (params: any) => {
          if (params.dataType === 'node') {
            return `${params.data.raw.type === 'CATEGORY' ? '品类' : '模板'}: ${params.data.name}`;
          }
        }
      },
      legend: {
        data: ['品类 (Category)', '模板 (Template)'],
        textStyle: { color: '#94a3b8' },
        bottom: 24,
        left: 24
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          data: nodes,
          links: links,
          categories: [
            { name: '品类 (Category)' },
            { name: '模板 (Template)' }
          ],
          roam: true,
          draggable: true,
          force: {
            repulsion: 400,
            gravity: 0.1,
            edgeLength: [80, 150],
            layoutAnimation: true
          },
          lineStyle: {
            color: 'source',
            curveness: 0.2
          },
          emphasis: {
            focus: 'adjacency',
            lineStyle: {
              width: 4
            }
          }
        }
      ]
    };
  }, [graphData]);

  const onEvents = {
    click: (params: any) => {
      if (params.dataType === 'node') {
        setSelectedNode(params.data.raw);
      }
    }
  };

  return (
    <div 
      className="detail-page fade-in" 
      style={{ 
        position: 'relative',
        width: '100%', 
        height: 'calc(100vh - 60px)',
        backgroundColor: '#0f172a',
        backgroundImage: 'radial-gradient(circle at 15% 50%, rgba(59, 130, 246, 0.15), transparent 25%), radial-gradient(circle at 85% 30%, rgba(139, 92, 246, 0.15), transparent 25%)',
        color: '#f8fafc',
        overflow: 'hidden'
      }}
    >
      {/* Absolute Header Glassmorphism */}
      <div style={{
        position: 'absolute',
        top: '24px',
        left: '24px',
        background: 'rgba(30, 41, 59, 0.7)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        border: '1px solid rgba(255, 255, 255, 0.1)',
        borderRadius: '16px',
        padding: '20px 24px',
        zIndex: 10,
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.3)'
      }}>
        <div style={{ display: 'flex', gap: '24px', marginBottom: '8px' }}>
          <div 
            onClick={() => setActiveTab('category')}
            style={{ 
              cursor: 'pointer', 
              fontSize: '18px', 
              fontWeight: activeTab === 'category' ? 700 : 500, 
              color: activeTab === 'category' ? '#f8fafc' : '#64748b',
              paddingBottom: '6px',
              borderBottom: activeTab === 'category' ? '2px solid #3b82f6' : '2px solid transparent',
              transition: 'all 0.3s'
            }}>
            品类知识库图谱
          </div>
          <div 
            onClick={() => setActiveTab('bgm')}
            style={{ 
              cursor: 'pointer', 
              fontSize: '18px', 
              fontWeight: activeTab === 'bgm' ? 700 : 500, 
              color: activeTab === 'bgm' ? '#f8fafc' : '#64748b',
              paddingBottom: '6px',
              borderBottom: activeTab === 'bgm' ? '2px solid #a855f7' : '2px solid transparent',
              transition: 'all 0.3s'
            }}>
            BGM知识库
          </div>
        </div>
        <p style={{ margin: 0, fontSize: '13px', color: '#94a3b8' }}>
          {activeTab === 'category' ? '全维度品类与模板可视化关联展示' : '基于大模型的纯音乐/环境音深度分析与归档'}
        </p>
      </div>

      {activeTab === 'category' ? (
        <>
          {/* Graph Area */}
      <div style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0, zIndex: 1 }}>
        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
            <div style={{ width: '40px', height: '40px', border: '3px solid rgba(255,255,255,0.1)', borderTopColor: '#3b82f6', borderRadius: '50%', animation: 'spin 1s ease-in-out infinite', marginBottom: '16px' }} />
            <div style={{ color: '#94a3b8' }}>正在加载图谱数据...</div>
            <style>{`
              @keyframes spin {
                to { transform: rotate(360deg); }
              }
            `}</style>
          </div>
        ) : (
          <ReactECharts
            option={option}
            onEvents={onEvents}
            theme="dark"
            style={{ width: '100%', height: '100%' }}
          />
        )}
      </div>

      {/* Details Panel Floating Glassmorphism */}
      <div 
        className="custom-scrollbar"
        style={{
        position: 'absolute',
        right: '24px',
        top: '24px',
        bottom: '24px',
        width: '420px',
        background: 'rgba(30, 41, 59, 0.7)',
        backdropFilter: 'blur(16px)',
        WebkitBackdropFilter: 'blur(16px)',
        border: '1px solid rgba(255, 255, 255, 0.1)',
        borderRadius: '20px',
        padding: '24px',
        zIndex: 10,
        overflowY: 'auto',
        boxShadow: '-5px 0 25px rgba(0,0,0,0.4)',
        display: 'flex',
        flexDirection: 'column',
        transform: selectedNode ? 'translateX(0)' : 'translateX(120%)',
        opacity: selectedNode ? 1 : 0,
        transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)'
      }}>
        {selectedNode && <NodeDetailPanel node={selectedNode} onClose={() => setSelectedNode(null)} />}
      </div>
      
      {/* Empty State Overlay */}
      {!selectedNode && !loading && (
        <div style={{
          position: 'absolute',
          right: '24px',
          top: '24px',
          background: 'rgba(30, 41, 59, 0.5)',
          backdropFilter: 'blur(8px)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          borderRadius: '12px',
          padding: '12px 24px',
          color: '#94a3b8',
          fontSize: '13px',
          zIndex: 9,
          pointerEvents: 'none'
        }}>
          ✨ 点击左侧图谱中的节点查看详情
        </div>
      )}
      </>
      ) : (
        <div style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0, zIndex: 1, boxSizing: 'border-box', paddingTop: '100px' }}>
          <BgmKnowledgeBase />
        </div>
      )}

      {/* Global Style for Custom Scrollbar */}
      <style>{`
        .custom-scrollbar {
          scrollbar-width: thin;
          scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
        }
        .custom-scrollbar::-webkit-scrollbar {
          width: 6px;
          height: 6px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.2);
          border-radius: 10px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: rgba(255, 255, 255, 0.3);
        }
        .custom-scrollbar::-webkit-scrollbar-corner {
          background: transparent;
        }
      `}</style>
    </div>
  );
}

function NodeDetailPanel({ node, onClose }: { node: any, onClose: () => void }) {
  const CloseBtn = () => (
    <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: '4px', display: 'flex' }}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
    </button>
  );

  if (node.type === 'CATEGORY') {
    return (
      <div style={{ color: '#f8fafc' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <div>
            <span style={{ display: 'inline-block', padding: '4px 10px', background: 'rgba(59, 130, 246, 0.15)', color: '#60a5fa', border: '1px solid rgba(59, 130, 246, 0.3)', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600, marginBottom: '12px' }}>
              ● 品类节点
            </span>
            <h3 style={{ margin: 0, fontSize: '1.5rem', color: '#f8fafc', fontWeight: 700 }}>{node.categoryName || node.categoryId}</h3>
          </div>
          <CloseBtn />
        </div>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', paddingBottom: '24px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
          <DetailItem label="Category ID" value={node.categoryId} />
          <DetailItem label="场景拆解阈值" value={node.sceneThreshold} />
          <div style={{ display: 'flex', gap: '24px' }}>
            <DetailItem label="置信度" value={node.confidenceScore} />
            <DetailItem label="使用频次" value={node.usageCount} />
          </div>
          <DetailItem label="发现方式" value={node.discoveredByLlm ? '大模型自动总结' : '人工/系统初始化'} />
        </div>
        
        <div style={{ marginTop: '24px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#94a3b8', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
            动态结构知识 (Dynamic Fields)
          </div>
          <pre 
            className="custom-scrollbar" 
            style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Courier New", monospace', background: 'rgba(15, 23, 42, 0.6)', padding: '16px', borderRadius: '12px', fontSize: '0.75rem', overflowX: 'auto', color: '#cbd5e1', border: '1px solid rgba(255,255,255,0.05)', margin: 0, maxHeight: '300px', overflowY: 'auto' }}
            dangerouslySetInnerHTML={{ __html: formatJsonAndHighlight(node.dynamicFields) }}
          />
        </div>

        {node.promptOverrides && (
          <div style={{ marginTop: '24px' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#fca5a5', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
              模型提示词干预 (Overrides)
            </div>
            <pre 
              className="custom-scrollbar" 
              style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Courier New", monospace', background: 'rgba(127, 29, 29, 0.2)', padding: '16px', borderRadius: '12px', fontSize: '0.75rem', overflowX: 'auto', color: '#fecaca', border: '1px solid rgba(248, 113, 113, 0.2)', margin: 0 }}
              dangerouslySetInnerHTML={{ __html: formatJsonAndHighlight(node.promptOverrides) }}
            />
          </div>
        )}
      </div>
    );
  }

  // TEMPLATE
  let parsedTpl: any = null;
  let dynamicTplObj: any = null;
  if (node.templateJson) {
    try {
      const rawStr = node.templateJson.replace(/[“”]/g, '"');
      parsedTpl = JSON.parse(rawStr);
      dynamicTplObj = { ...parsedTpl };
      delete dynamicTplObj.meta;
      delete dynamicTplObj.scriptStructure;
      delete dynamicTplObj.shots;
    } catch (e) {}
  }

  return (
    <div style={{ color: '#f8fafc' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <span style={{ display: 'inline-block', padding: '4px 10px', background: 'rgba(16, 185, 129, 0.15)', color: '#34d399', border: '1px solid rgba(16, 185, 129, 0.3)', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600, marginBottom: '12px' }}>
            ● 模板节点
          </span>
          <h3 style={{ margin: 0, fontSize: '1.5rem', color: '#f8fafc', fontWeight: 700 }}>{node.templateName || node.templateId}</h3>
        </div>
        <CloseBtn />
      </div>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', paddingBottom: '24px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
        <DetailItem label="Template ID" value={node.templateId} />
        <div style={{ display: 'flex', gap: '24px' }}>
          <DetailItem label="Version" value={`v${node.templateVersion}`} />
          <DetailItem label="所属品类" value={node.categoryId} />
          <DetailItem label="状态" value={node.status} />
        </div>
        <DetailItem label="来源解析任务" value={node.sourceTaskId} />
      </div>
      
      {parsedTpl ? (
        <>
          {parsedTpl.meta && (
            <div style={{ marginTop: '24px' }}>
               <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#94a3b8', marginBottom: '12px' }}>🎬 基础元数据 (Meta)</div>
               <div style={{ display: 'flex', gap: '16px', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)' }}>
                  <DetailItem label="画幅 (AspectRatio)" value={parsedTpl.meta.aspectRatio} />
                  <DetailItem label="声学环境 (AcousticEnv)" value={parsedTpl.meta.acousticEnvironment} />
               </div>
            </div>
          )}

          {parsedTpl.scriptStructure?.segments && (
            <div style={{ marginTop: '24px' }}>
               <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#94a3b8', marginBottom: '12px' }}>🎞️ 分段结构 (Segments)</div>
               <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                 {parsedTpl.scriptStructure.segments.map((seg: any, idx: number) => (
                   <div key={idx} style={{ background: 'rgba(59, 130, 246, 0.05)', padding: '12px', borderRadius: '8px', border: '1px solid rgba(59, 130, 246, 0.1)' }}>
                     <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                       <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#38bdf8' }}>段落 {seg.segmentIndex ?? idx} : {seg.role?.toUpperCase()}</span>
                       {seg.durationRange?.min && <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>≥ {seg.durationRange.min}s</span>}
                     </div>
                     <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                       {seg.preferredCameraMovements?.map((m: string) => <span key={'m'+m} style={{ background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px', fontSize: '0.65rem' }}>{m}</span>)}
                       {seg.preferredShotTypes?.map((s: string) => <span key={'s'+s} style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#34d399', padding: '2px 6px', borderRadius: '4px', fontSize: '0.65rem' }}>{s}</span>)}
                       {seg.requiredVisualFunctions?.map((f: string) => <span key={'f'+f} style={{ background: 'rgba(244, 114, 182, 0.1)', color: '#f472b6', padding: '2px 6px', borderRadius: '4px', fontSize: '0.65rem' }}>{f}</span>)}
                     </div>
                   </div>
                 ))}
               </div>
            </div>
          )}

          {Object.keys(dynamicTplObj).length > 0 && (
            <div style={{ marginTop: '24px' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#94a3b8', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
                动态扩展属性 (Dynamic JSON)
              </div>
              <pre 
                className="custom-scrollbar" 
                style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Courier New", monospace', background: 'rgba(15, 23, 42, 0.6)', padding: '16px', borderRadius: '12px', fontSize: '0.75rem', overflowX: 'auto', color: '#cbd5e1', border: '1px solid rgba(255,255,255,0.05)', margin: 0, maxHeight: '300px', overflowY: 'auto' }}
                dangerouslySetInnerHTML={{ __html: formatJsonAndHighlight(JSON.stringify(dynamicTplObj)) }}
              />
            </div>
          )}
        </>
      ) : (
        <div style={{ marginTop: '24px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#94a3b8', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
            模板结构 (Template JSON)
          </div>
          <pre 
            className="custom-scrollbar" 
            style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Courier New", monospace', background: 'rgba(15, 23, 42, 0.6)', padding: '16px', borderRadius: '12px', fontSize: '0.75rem', overflowX: 'auto', color: '#cbd5e1', border: '1px solid rgba(255,255,255,0.05)', margin: 0, maxHeight: '400px', overflowY: 'auto' }}
            dangerouslySetInnerHTML={{ __html: formatJsonAndHighlight(node.templateJson) }}
          />
        </div>
      )}
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: any }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{label}</div>
      <div style={{ fontSize: '0.9rem', color: '#f1f5f9', fontWeight: 500 }}>{value == null ? '—' : String(value)}</div>
    </div>
  );
}

function formatJsonAndHighlight(str: string) {
  if (!str) return '—';
  
  // Convert full-width/smart quotes to standard double quotes
  let safeStr = str.replace(/[“”]/g, '"');
  
  let jsonStr = safeStr;
  try {
    const obj = JSON.parse(safeStr);
    jsonStr = JSON.stringify(obj, null, 2);
  } catch {
    jsonStr = safeStr;
  }
  
  // HTML Escape
  jsonStr = jsonStr.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  
  // Highlight
  const highlighted = jsonStr.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
    let color = '#cbd5e1'; 
    if (/^"/.test(match)) {
      if (/:$/.test(match)) {
        color = '#38bdf8'; // Sky Blue for keys (highly visible)
      } else {
        color = '#a3e635'; // Lime Green for string values (distinct from keys)
      }
    } else if (/true|false/.test(match)) {
      color = '#f472b6'; // Pink for booleans
    } else if (/null/.test(match)) {
      color = '#94a3b8'; // Slate Gray for null
    } else {
      color = '#fbbf24'; // Amber/Yellow for numbers
    }
    return `<span style="color:${color};">${match}</span>`;
  });
  
  return highlighted;
}
