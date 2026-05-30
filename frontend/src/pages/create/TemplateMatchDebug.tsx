import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { creationApi } from '../../api/creation';
import { TemplateRecommendItemData } from '../../types';

export const TemplateMatchDebug: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  
  const [w1, setW1] = useState<number>(0.6);
  const [w2, setW2] = useState<number>(0.4);
  const [topN, setTopN] = useState<number>(10);
  const [results, setResults] = useState<TemplateRecommendItemData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleTest = async () => {
    if (!projectId) return;
    setLoading(true);
    setError('');
    try {
      const res = await creationApi.recommendTemplates(projectId, w1, w2, topN);
      setResults(res.data.recommendations || []);
    } catch (err: any) {
      setError(err.message || '获取推荐失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-8 max-w-7xl mx-auto text-gray-800 h-screen flex flex-col">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold text-gray-900">模板匹配推荐测试床 (Project: {projectId})</h1>
        <button
          onClick={() => navigate(`/create/detail/${projectId}/gap-detection`)}
          className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
        >
          返回项目
        </button>
      </div>

      <div className="bg-white p-6 rounded-lg shadow mb-8">
        <h2 className="text-lg font-medium mb-4">参数调优</h2>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              语义匹配权重 (W1): {w1.toFixed(2)}
            </label>
            <input 
              type="range" min="0" max="1" step="0.05" 
              value={w1} 
              onChange={e => {
                const val = parseFloat(e.target.value);
                setW1(val);
                setW2(Math.round((1 - val) * 100) / 100);
              }}
              className="w-full"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              镜头结构匹配权重 (W2): {w2.toFixed(2)}
            </label>
            <input 
              type="range" min="0" max="1" step="0.05" 
              value={w2} 
              onChange={e => {
                const val = parseFloat(e.target.value);
                setW2(val);
                setW1(Math.round((1 - val) * 100) / 100);
              }}
              className="w-full"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              返回候选数量 (Top N): {topN}
            </label>
            <input 
              type="number" min="1" max="50"
              value={topN} 
              onChange={e => setTopN(parseInt(e.target.value))}
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
            />
          </div>
        </div>

        <button
          onClick={handleTest}
          disabled={loading}
          className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
        >
          {loading ? '计算中...' : '运行匹配算法'}
        </button>
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </div>

      <div className="flex-1 bg-white rounded-lg shadow overflow-hidden flex flex-col">
        <div className="p-4 border-b bg-gray-50">
          <h2 className="text-lg font-medium text-gray-900">推荐结果 (Top {results.length})</h2>
        </div>
        <div className="flex-1 overflow-auto p-4">
          {results.length === 0 && !loading ? (
            <div className="text-center text-gray-500 mt-10">无匹配结果，请调整参数后重试</div>
          ) : (
            <div className="space-y-4">
              {results.map((item) => (
                <div key={item.templateId} className="border rounded-lg p-4 flex flex-col md:flex-row md:items-center justify-between hover:border-indigo-300 transition-colors">
                  <div className="flex items-center mb-4 md:mb-0">
                    <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-800 font-bold mr-4">
                      {item.rank}
                    </div>
                    <div>
                      <h3 className="text-lg font-medium text-gray-900">{item.templateName || item.templateId}</h3>
                      <p className="text-sm text-gray-500">品类: {item.categoryId} | 镜头数: {item.segmentCount}</p>
                      <p className="text-xs text-gray-400 mt-1">ID: {item.templateId} (v{item.templateVersion})</p>
                    </div>
                  </div>
                  
                  <div className="flex gap-4">
                    <div className="text-center p-2 bg-blue-50 rounded">
                      <div className="text-xs text-blue-600 mb-1">语义 (Chroma)</div>
                      <div className="text-lg font-semibold text-blue-800">{item.semanticScore.toFixed(3)}</div>
                    </div>
                    <div className="text-center p-2 bg-green-50 rounded">
                      <div className="text-xs text-green-600 mb-1">结构 (Math)</div>
                      <div className="text-lg font-semibold text-green-800">{item.structureScore.toFixed(3)}</div>
                    </div>
                    <div className="text-center p-2 bg-indigo-50 rounded border border-indigo-100 shadow-sm">
                      <div className="text-xs text-indigo-600 mb-1">最终得分</div>
                      <div className="text-xl font-bold text-indigo-800">{item.finalScore.toFixed(3)}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
