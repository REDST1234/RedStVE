import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { deconstructApi } from '../../api/deconstruct';
import { videoApi } from '../../api/video';

export default function VisualDashboard() {
  const { id } = useParams();
  const navigate = useNavigate();
  
  const [loading, setLoading] = useState(true);
  const [timelineData, setTimelineData] = useState<any>(null);
  const [templateData, setTemplateData] = useState<any>(null);
  const [rawSceneData, setRawSceneData] = useState<any>(null);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
        if (!id) return;
        
        // 1. Get Project
        const projResp = await deconstructApi.getProject(id);
        if (cancelled) return;
        
        let targetTaskId = '';
        if (projResp.data && projResp.data.materials && projResp.data.materials.length > 0) {
          targetTaskId = projResp.data.materials[0].taskId || '';
        }
        
        if (!targetTaskId) {
          console.warn("Project has no materials to visualize");
          setLoading(false);
          return;
        }
        
        // 2. Fetch Result with Timeline
        const resultResp = await videoApi.getTaskResult(targetTaskId, true);
        if (cancelled) return;
        
        let rTimeline = resultResp.data?.refinedTimeline;
        let vTemplate = resultResp.data?.videoStructureTemplate;
        
        // Try fallback parsing if they are strings
        if (typeof rTimeline === 'string') {
            try { rTimeline = JSON.parse(rTimeline); } catch (e) {}
        }
        if (typeof vTemplate === 'string') {
            try { vTemplate = JSON.parse(vTemplate); } catch (e) {}
        }
        
        // 3. (Optional) Fetch DeconstructTemplate published data if exists, 
        //    but videoStructureTemplate inside taskResult is already the latest model output.
        //    Let's check if the user specifically wanted template_version from deconstruct_template.
        //    If we don't have an API exposed for fetch by sourceTaskId, we can just use the taskResult one.
        
        setTimelineData(rTimeline);
        setTemplateData(vTemplate);
        
        try {
            const rawSceneRes = await videoApi.getRawSceneResult(targetTaskId);
            if (rawSceneRes.code === "0") {
                setRawSceneData(rawSceneRes.data);
            }
        } catch (e) {
            console.warn("Failed to fetch raw scene result", e);
        }
        
      } catch (err) {
        console.error("Failed to load visualization data", err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    
    fetchData();
    return () => { cancelled = true; };
  }, [id]);

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#0f172a', color: 'white' }}>加载中...</div>;
  }

  // --- Parse Timeline Data from API ---
  let parsedEvents: any[] = [];
  if (timelineData && timelineData.timelineSegments && Array.isArray(timelineData.timelineSegments)) {
      timelineData.timelineSegments.forEach((seg: any) => {
          const timeRangeStr = seg.timeRange || "0.0s";
          const timeRangeParts = timeRangeStr.split('-').map((s: string) => {
              const str = s.replace(/s/g, '').trim();
              const parts = str.split(':');
              if (parts.length === 2) {
                  return parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
              }
              return parseFloat(str) || 0;
          });
          const aStart = timeRangeParts[0] || 0;
          const aEnd = timeRangeParts.length > 1 ? timeRangeParts[1] : aStart;

          if (seg.audioAndText && seg.audioAndText.length > 0) {
              seg.audioAndText.forEach((a: any) => {
                  if (a.text) {
                      const aTimeStr = a.timestamp || seg.timeRange || "0.0s";
                      const aTimeParts = aTimeStr.split('-').map((s: string) => {
                          const str = s.replace(/s/g, '').trim();
                          const parts = str.split(':');
                          if (parts.length === 2) {
                              return parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
                          }
                          return parseFloat(str) || 0;
                      });
                      const asStart = aTimeParts[0] || 0;
                      const asEnd = aTimeParts.length > 1 ? aTimeParts[1] : asStart;
                      parsedEvents.push({ timeRange: [asStart, asEnd], type: "asr", content: a.text, rawTime: a.timestamp });
                  }
              });
          }
          if (seg.visualDynamics) {
              const action = seg.visualDynamics.translatedAction || '';
              
              // 提取 avgScore 和 vel 用于高频毛刺线
              let avgScoreMatch = action.match(/avgScore=([0-9.]+)/);
              let avgScore = avgScoreMatch ? parseFloat(avgScoreMatch[1]) : 0;
              
              let velMatch = action.match(/([0-9.]+)次\/秒/);
              let vel = velMatch ? parseFloat(velMatch[1]) : (seg.visualDynamics.cuttingVelocity || 0);

              const cuts = seg.visualDynamics.rawCutsCount || 0;
              const translated = action || `发生画面变动 (${cuts}次切分)`;
              parsedEvents.push({ timeRange: [aStart, aEnd], type: "physics", content: translated, vel, avgScore, cuts, rawTime: seg.timeRange });
          }
      });
      
      // 按 start time 重新对所有事件进行升序排序，保证穿插正确
      parsedEvents.sort((a, b) => a.timeRange[0] - b.timeRange[0]);
  }

  // 计算总时长用于高中低频对齐
  let totalVideoDuration = timelineData?.systemMeta?.totalDuration || 1;
  if (totalVideoDuration === 1 && parsedEvents.length > 0) {
      totalVideoDuration = Math.max(...parsedEvents.map(e => e.timeRange[1])) || 1;
  }

  // 提取高频 OpenCV 变动率数据 (用于 ECharts 第二轴锯齿毛刺线)
  let highFreqData: [number, number][] = [];
  if (rawSceneData && rawSceneData.shots && Array.isArray(rawSceneData.shots)) {
      highFreqData = rawSceneData.shots.map((shot: any) => {
          const percent = (shot.startTime / totalVideoDuration) * 100;
          // 直接使用底层的 sceneScore，放大倍数以便在图表上呈现强烈的锯齿毛刺感
          return [percent, shot.sceneScore * 100];
      });
  } else {
      highFreqData = parsedEvents
          .filter(e => e.type === 'physics')
          .map(e => {
              const percent = (e.timeRange[0] / totalVideoDuration) * 100;
              // 混合 avgScore 和 vel 放大抖动幅度，制造工业级锯齿感
              const jitterValue = (e.vel * 0.5) + (e.avgScore * 10);
              return [percent, jitterValue];
          });
  }
      
  // --- Fallback Mock Data generation if empty ---
  const finalTimeline = parsedEvents.length > 0 ? { events: parsedEvents } : {
      events: [
          { timeRange: [0, 3.2], type: "asr", content: "大一早八顶不住？那是..." },
          { timeRange: [1.2, 1.2], type: "physics", content: "检测到像素突变 + 转场音效波峰" },
          { timeRange: [3.2, 12], type: "asr", content: "纯正手作咖啡，10秒瞬间让你清醒回到巅峰状态！" },
          { timeRange: [8.5, 8.5], type: "physics", content: "人物骨骼大幅度变化 (挥手动作)" }
      ]
  };

  const finalTemplate = (templateData && templateData.rhythmStructure && templateData.scriptStructure) ? templateData : {
      rhythmStructure: {
          paceCurve: [
              { timePercent: 0, pace: "medium", label: "开篇钩子建立高燃预期" },
              { timePercent: 10, pace: "medium" },
              { timePercent: 30, pace: "fast" },
              { timePercent: 45, pace: "fast" },
              { timePercent: 55, pace: "very_fast" },
              { timePercent: 85, pace: "very_fast", label: "🔥 核心效果演示高潮区" },
              { timePercent: 90, pace: "slow" },
              { timePercent: 100, pace: "slow", label: "收尾引导" }
          ],
          beatSyncPoints: [
              { timePercent: 12, beatType: "text_beat", description: "疑问字幕弹出" },
              { timePercent: 22, beatType: "text_beat", description: "痛点字幕弹出" },
              { timePercent: 48, beatType: "speed_beat", description: "2倍速画面快放" },
              { timePercent: 60, beatType: "climax_beat", description: "高燃转场" },
              { timePercent: 80, beatType: "text_beat", description: "利益点强调" }
          ]
      },
      scriptStructure: {
          segments: [
              { role: "Hook", durationWeight: 10, description: "建立痛点共鸣", requiredElements: ["困倦的人物", "大字体疑问"] },
              { role: "Development", durationWeight: 35, description: "引出产品解决方案", requiredElements: ["产品特写", "功能讲解"] },
              { role: "Climax", durationWeight: 45, description: "核心效果密集展示", requiredElements: ["快节奏切换", "动感音效", "前后对比"] },
              { role: "Call To Action", durationWeight: 10, description: "行动号召", requiredElements: ["购物车指示", "利益诱导"] }
          ]
      }
  };

  // ECharts Option
  const paceToValue: Record<string, number> = {
      'slow': 1,
      'medium': 2,
      'fast': 3,
      'very_fast': 4
  };
  
  const chartData = (finalTemplate.rhythmStructure?.paceCurve || []).map((pt: any) => [
      pt.timePercent, 
      paceToValue[pt.pace] || 2
  ]);

  // 计算微观镜头的密铺矩阵 (Shot Matrix)
  const shotsList = finalTemplate.shots || [];
  let totalShotsDuration = shotsList.reduce((acc: number, shot: any) => {
      return acc + (((shot.durationRange?.min || 1) + (shot.durationRange?.max || 2)) / 2);
  }, 0);
  // 兜底防 0 除
  if (totalShotsDuration <= 0) totalShotsDuration = 1;

  let accumulatedTime = 0;
  const shotTicks = shotsList.map((shot: any) => {
      const avgDuration = ((shot.durationRange?.min || 1) + (shot.durationRange?.max || 2)) / 2;
      const tickPercent = (accumulatedTime / totalShotsDuration) * 100;
      accumulatedTime += avgDuration;
      return {
          ...shot,
          timePercent: Math.min(tickPercent, 100),
          shotIndex: shot.shotIndex,
          type: shot.shotType || shot.type
      };
  });

  const rhythm = finalTemplate.rhythmStructure || {};
  let climaxAreas: any[] = [];
  if (rhythm.climaxPositions && Array.isArray(rhythm.climaxPositions)) {
      climaxAreas = rhythm.climaxPositions.map((pos: any) => [
          { 
              name: '🔥 高潮区', 
              xAxis: pos.startPercent ?? 0, 
              label: { color: '#fca5a5', fontSize: 16, fontWeight: 'bold' } 
          },
          { xAxis: pos.endPercent ?? 100 }
      ]);
  } else if (rhythm.climaxPosition) {
      climaxAreas = [
          [
              { 
                  name: '🔥 高潮区', 
                  xAxis: rhythm.climaxPosition.startPercent ?? 45, 
                  label: { color: '#fca5a5', fontSize: 16, fontWeight: 'bold' } 
              },
              { xAxis: rhythm.climaxPosition.endPercent ?? 90 }
          ]
      ];
  } else {
      climaxAreas = [
          [
              { 
                  name: '🔥 高潮区', 
                  xAxis: 45, 
                  label: { color: '#fca5a5', fontSize: 16, fontWeight: 'bold' } 
              },
              { xAxis: 90 }
          ]
      ];
  }

  const option = {
    backgroundColor: 'transparent',
    grid: { left: 40, right: 40, top: 40, bottom: 30 },
    tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15, 23, 42, 0.95)',
        borderColor: '#334155',
        textStyle: { color: '#e2e8f0', fontSize: 13 },
        padding: 12,
        formatter: (params: any) => {
            if (params.seriesName === '微观物理切分毛刺') {
                return `<div style="font-weight: bold; color: #fbbf24; margin-bottom: 4px;">⚡ 物理微观数据溯源</div>
                        <div>位置: ${Number(params.value[0]).toFixed(2)}%</div>
                        <div>切分强度分 (sceneScore): ${(Number(params.value[1]) / 100).toFixed(4)}</div>`;
            } else if (params.seriesName === '宏观节奏') {
                if (params.componentType === 'markPoint') {
                    return `<div style="font-weight: bold; color: #38bdf8; margin-bottom: 4px;">🤖 LLM 宏观推断溯源</div>
                            <div style="max-width: 300px; white-space: normal; line-height: 1.5;">${params.data.value}</div>`;
                }
                const paceMap: Record<number, string> = { 1: '慢 (slow)', 2: '中 (medium)', 3: '快 (fast)', 4: '极快 (very_fast)' };
                return `<div style="font-weight: bold; color: #38bdf8; margin-bottom: 4px;">🌊 宏观节奏曲线</div>
                        <div>进度: ${Number(params.value[0]).toFixed(2)}%</div>
                        <div>步调级别: ${paceMap[Number(params.value[1])] || '未知'}</div>`;
            }
            return '';
        }
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#94a3b8' },
      splitLine: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        max: 5,
        splitLine: { lineStyle: { color: '#334155', type: 'dashed' } },
        axisLabel: {
            formatter: (val: number) => {
                if (val === 1) return '慢';
                if (val === 2) return '中';
                if (val === 3) return '快';
                if (val === 4) return '极快';
                return '';
            },
            color: '#94a3b8'
        }
      },
      {
        type: 'value',
        show: false, // 隐藏副轴刻度
        min: 0,
        max: 85 // 基于业务数据，快节奏最高约为0.8(即80)，将满量程定在85可让爆发点直逼图表顶部
      }
    ],
    series: [
      {
        name: '微观物理切分毛刺',
        type: 'line',
        yAxisIndex: 1,
        smooth: false,
        data: highFreqData,
        lineStyle: { width: 1.5, color: 'rgba(234, 179, 8, 0.8)' }, // 赛博亮黄，高频抖动
        itemStyle: { color: 'rgba(234, 179, 8, 1)' },
        showSymbol: true,
        symbolSize: 4,
        areaStyle: {
            color: {
                type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                colorStops: [
                    { offset: 0, color: 'rgba(234, 179, 8, 0.2)' },
                    { offset: 1, color: 'rgba(234, 179, 8, 0)' }
                ]
            }
        }
      },
      {
        name: '宏观节奏',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        data: chartData,
        lineStyle: {
            width: 4,
            color: '#38bdf8',
            shadowColor: 'rgba(56, 189, 248, 0.5)',
            shadowBlur: 10
        },
        itemStyle: { color: '#38bdf8' },
        areaStyle: {
            color: {
                type: 'linear',
                x: 0, y: 0, x2: 0, y2: 1,
                colorStops: [
                    { offset: 0, color: 'rgba(56, 189, 248, 0.3)' },
                    { offset: 1, color: 'rgba(56, 189, 248, 0)' }
                ]
            }
        },
        markArea: {
            itemStyle: {
                color: {
                    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                        { offset: 0, color: 'rgba(239, 68, 68, 0.4)' }, // 霓虹红亮色
                        { offset: 1, color: 'rgba(239, 68, 68, 0.05)' } // 往下渐隐
                    ]
                }
            },
            data: climaxAreas
        },
        markPoint: {
            data: (finalTemplate.rhythmStructure?.paceCurve || [])
                    .filter((pt: any) => pt.label || pt.note)
                    .map((pt: any) => ({
                        coord: [pt.timePercent, paceToValue[pt.pace] || 2],
                        value: pt.label || pt.note,
                        symbol: 'circle',
                        symbolSize: 14,
                        itemStyle: { 
                            color: '#f8fafc', // solid white inside
                            borderColor: '#38bdf8', // cyan border to match main line
                            borderWidth: 3,
                            shadowColor: 'rgba(56, 189, 248, 0.8)',
                            shadowBlur: 10
                        },
                        label: {
                            show: false, // 隐藏常驻文字，依赖 Tooltip 显示
                            color: '#fff',
                            formatter: (p: any) => {
                                let str = p.value || '';
                                if (str.length > 12) {
                                    return str.substring(0, 12) + '...';
                                }
                                return str;
                            },
                            position: 'top',
                            backgroundColor: 'rgba(15, 23, 42, 0.85)',
                            padding: [6, 10],
                            borderRadius: 4,
                            borderWidth: 1,
                            borderColor: 'rgba(245, 158, 11, 0.4)'
                        }
                    }))
        }
      }
    ]
  };

  return (
    <div style={{ background: '#0f172a', minHeight: '100vh', display: 'flex', flexDirection: 'column', color: '#e2e8f0', fontFamily: 'Inter, sans-serif' }}>
      
      {/* Header */}
      <div style={{ padding: '20px 32px', borderBottom: '1px solid #1e293b', display: 'flex', alignItems: 'center', gap: '16px' }}>
        <button 
            onClick={() => navigate(-1)}
            style={{ background: '#1e293b', border: 'none', color: '#cbd5e1', cursor: 'pointer', padding: '8px', borderRadius: '50%', display: 'flex' }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
        </button>
        <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 600, background: 'linear-gradient(to right, #a855f7, #3b82f6)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
          多模态全景解构视图 (Dashboard)
        </h2>
      </div>

      {/* Main Content */}
      <div style={{ flex: 1, display: 'flex', padding: '32px', gap: '32px', overflow: 'hidden' }}>
        
        {/* Left Column: Timeline */}
        <div style={{ flex: '0 0 400px', background: '#1e293b', borderRadius: '16px', border: '1px solid #334155', padding: '24px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <h3 style={{ margin: '0 0 24px 0', fontSize: '1.1rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '1.4rem' }}>⏱️</span> 多模态时序日志
            </h3>
            
            <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', paddingRight: '12px', paddingLeft: '8px' }} className="custom-scroll">
                <div style={{ 
                    position: 'relative', 
                    paddingLeft: '24px', 
                    paddingRight: '24px', 
                    borderLeft: '2px solid rgba(245, 158, 11, 0.3)', 
                    borderRight: '2px solid rgba(59, 130, 246, 0.3)' 
                }}>
                    {finalTimeline.events.map((ev: any, idx: number) => {
                        const isAsr = ev.type === 'asr';
                        return (
                        <div key={idx} style={{ 
                            position: 'relative', 
                            marginBottom: '20px',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: isAsr ? 'flex-end' : 'flex-start',
                            textAlign: isAsr ? 'right' : 'left'
                        }}>
                            {/* 轴上的圆点 */}
                            <div style={{ 
                                position: 'absolute', 
                                left: isAsr ? 'auto' : '-30px', 
                                right: isAsr ? '-30px' : 'auto',
                                top: '4px', 
                                width: '10px', 
                                height: '10px', 
                                borderRadius: '50%', 
                                background: isAsr ? '#3b82f6' : '#f59e0b',
                                boxShadow: `0 0 8px ${isAsr ? '#3b82f6' : '#f59e0b'}`,
                                zIndex: 2 
                            }} />
                            
                            {/* 轻量化胶囊与文本 */}
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', width: '100%', alignItems: isAsr ? 'flex-end' : 'flex-start' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexDirection: isAsr ? 'row-reverse' : 'row' }}>
                                    <span style={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: '0.8rem' }}>
                                        [{ev.rawTime || `${ev.timeRange[0]}s`}]
                                    </span>
                                    {ev.type === 'physics' && (
                                        <span style={{ 
                                            background: 'rgba(245, 158, 11, 0.15)', 
                                            color: '#fbbf24', 
                                            padding: '2px 8px', 
                                            borderRadius: '4px', 
                                            fontSize: '0.75rem', 
                                            fontWeight: 'bold',
                                            border: '1px solid rgba(245, 158, 11, 0.3)'
                                        }}>
                                            ⚡️ 画面硬切 {ev.vel !== undefined ? `(Vel:${ev.vel}/s)` : ''}
                                        </span>
                                    )}
                                </div>
                                <div style={{ 
                                    color: isAsr ? '#f8fafc' : '#cbd5e1', 
                                    fontSize: '0.9rem', 
                                    lineHeight: 1.5,
                                    fontWeight: isAsr ? 500 : 400,
                                    maxWidth: '90%'
                                }}>
                                    {isAsr ? `💬 ${ev.content}` : ev.content}
                                </div>
                            </div>
                        </div>
                    )})}
                </div>

            </div>
        </div>

        {/* Right Column: Visual Dashboard */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '24px' }}>
            
            {/* 1. Rhythm Waveform */}
            <div style={{ flex: '0 0 350px', background: '#1e293b', borderRadius: '16px', border: '1px solid #334155', padding: '24px', position: 'relative', overflow: 'hidden' }}>
                <h3 style={{ margin: '0 0 16px 0', fontSize: '1.1rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '1.4rem' }}>📈</span> 视频情绪与节奏波形
                </h3>
                <div style={{ position: 'absolute', top: 50, left: 0, right: 0, bottom: 0 }}>
                    <ReactECharts option={option} style={{ height: '100%', width: '100%' }} />
                </div>
            </div>

            {/* 2. Script Blocks */}
            <div style={{ flex: 1, background: '#1e293b', borderRadius: '16px', border: '1px solid #334155', padding: '24px', display: 'flex', flexDirection: 'column' }}>
                <h3 style={{ margin: '0 0 32px 0', fontSize: '1.1rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '1.4rem' }}>🎞️</span> 四段式多模态剧本主轴
                </h3>
                
                <div style={{ position: 'relative', height: '48px', borderRadius: '24px', boxShadow: '0 4px 20px rgba(0,0,0,0.3)', marginBottom: '40px' }}>
                    {/* 1. 底层：宏观四大阶段彩色基带 */}
                    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', zIndex: 1 }}>
                        {finalTemplate.scriptStructure.segments.map((seg: any, idx: number) => {
                            const gradients = [
                                'linear-gradient(135deg, #6366f1, #8b5cf6)', // Indigo to Purple
                                'linear-gradient(135deg, #3b82f6, #06b6d4)', // Blue to Cyan
                                'linear-gradient(135deg, #ec4899, #f43f5e)', // Pink to Rose
                                'linear-gradient(135deg, #f59e0b, #ea580c)'  // Amber to Orange
                            ];
                            return (
                                <div 
                                    key={`seg-${idx}`} 
                                    className="script-block-hover"
                                    style={{ 
                                        width: `${seg.durationWeight <= 1 ? seg.durationWeight * 100 : seg.durationWeight}%`, 
                                        background: gradients[idx % gradients.length],
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: 'white',
                                        fontWeight: 'bold',
                                        position: 'relative',
                                        cursor: 'pointer',
                                        transition: 'all 0.3s ease',
                                        borderRight: idx < finalTemplate.scriptStructure.segments.length - 1 ? '2px solid #0f172a' : 'none',
                                        boxShadow: 'inset 0 2px 10px rgba(255,255,255,0.1)',
                                        borderTopLeftRadius: idx === 0 ? '24px' : '0',
                                        borderBottomLeftRadius: idx === 0 ? '24px' : '0',
                                        borderTopRightRadius: idx === finalTemplate.scriptStructure.segments.length - 1 ? '24px' : '0',
                                        borderBottomRightRadius: idx === finalTemplate.scriptStructure.segments.length - 1 ? '24px' : '0'
                                    }}
                                >
                                    <span style={{ 
                                        position: 'relative', 
                                        zIndex: 10, 
                                        textShadow: '0 2px 4px rgba(0,0,0,0.8)',
                                        whiteSpace: 'normal',
                                        display: '-webkit-box',
                                        WebkitLineClamp: 2,
                                        WebkitBoxOrient: 'vertical',
                                        overflow: 'hidden',
                                        padding: '0 4px',
                                        fontSize: '0.9rem',
                                        lineHeight: '1.2',
                                        textAlign: 'center'
                                    }}>
                                        {seg.label || seg.role}
                                    </span>
                                    <div className="script-block-tooltip" style={{ zIndex: 20 }}>
                                        <div style={{ fontWeight: 'bold', fontSize: '1.1rem', marginBottom: '8px' }}>{seg.label || seg.role}</div>
                                        <div style={{ fontSize: '0.9rem', color: '#e2e8f0', marginBottom: '12px' }}>{seg.description}</div>
                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                            {(seg.requiredElements || []).map((el: string, i: number) => (
                                                <span key={i} style={{ background: 'rgba(255,255,255,0.2)', padding: '2px 8px', borderRadius: '4px', fontSize: '0.8rem' }}>{el}</span>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {/* 3. 覆盖层B：黄金视觉 Anchor Shots (Milestones) 下挂显示 */}
                    {shotTicks.flatMap((shot: any, idx: number) => {
                        let _acc = 0;
                        let colorIdx = 0;
                        for (let i = 0; i < finalTemplate.scriptStructure.segments.length; i++) {
                            const seg = finalTemplate.scriptStructure.segments[i];
                            const w = seg.durationWeight <= 1 ? seg.durationWeight * 100 : seg.durationWeight;
                            _acc += w;
                            if (shot.timePercent <= _acc + 0.1) {
                                colorIdx = i;
                                break;
                            }
                        }
                        const baseColors = ['#8b5cf6', '#06b6d4', '#f43f5e', '#ea580c'];
                        const dotColor = baseColors[colorIdx % baseColors.length];

                        return [
                            // 1. 背后下垂的虚线 (zIndex: 0)
                            <div key={`shot-line-${idx}`} style={{ 
                                position: 'absolute', 
                                left: `${shot.timePercent}%`, 
                                top: '50%', // 从主轴正中心开始
                                transform: 'translateX(-50%)',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                zIndex: 0 // 置于主轴色块下方
                            }}>
                                <div style={{
                                    width: '2px',
                                    height: '48px', // 24px隐藏在背后 + 24px露出
                                    borderLeft: `2px dashed ${dotColor}b3`
                                }} />
                            </div>,
                            
                            // 2. 前景小圆点和自定义悬浮详情 (zIndex: 20)
                            <div key={`shot-dot-${idx}`} className="script-block-hover" style={{ 
                                position: 'absolute', 
                                left: `${shot.timePercent}%`, 
                                top: 'calc(50% + 45px)', // 半高24 + 伸出24 - 重叠3
                                transform: 'translateX(-50%)',
                                display: 'flex',
                                justifyContent: 'center',
                                zIndex: 20
                            }}>
                                {/* 里程碑节点 */}
                                <div style={{
                                    width: '14px', height: '14px',
                                    borderRadius: '50%',
                                    background: '#fff',
                                    boxShadow: `0 0 10px ${dotColor}, 0 0 20px ${dotColor}`,
                                    border: `3px solid ${dotColor}`,
                                    cursor: 'pointer'
                                }} />

                                {/* 悬浮详情卡片 (复用磨砂玻璃特效) */}
                                <div className="script-block-tooltip" style={{
                                    bottom: 'calc(100% + 12px)',
                                    width: '280px',
                                    borderTop: `3px solid ${dotColor}`
                                }}>
                                    <div style={{ fontWeight: 'bold', fontSize: '1.05rem', marginBottom: '8px', color: dotColor, display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span style={{ fontSize: '1.2rem' }}>🎬</span> 
                                        镜号 {shot.shotIndex !== undefined ? (shot.shotIndex === 0 ? 1 : shot.shotIndex) : (idx + 1)}: {shot.type}
                                    </div>
                                    {shot.description && (
                                        <div style={{ fontSize: '0.9rem', color: '#cbd5e1', marginBottom: '12px', lineHeight: '1.5' }}>
                                            {shot.description}
                                        </div>
                                    )}
                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', fontSize: '0.8rem', color: '#94a3b8' }}>
                                        {shot.cameraMovement && (
                                            <span style={{ background: 'rgba(255,255,255,0.08)', padding: '4px 8px', borderRadius: '4px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                                🎥 {shot.cameraMovement}
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ];
                    })}
                </div>


                {/* 4. Metadata & Creative Tags (创作手法溯源) */}
                <h3 style={{ margin: '48px 0 16px 0', fontSize: '1.1rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '1.4rem' }}>🎨</span> 多模态创作手法溯源
                </h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
                    {(finalTemplate.meta?.styles || finalTemplate.meta?.style ? [].concat(finalTemplate.meta?.styles || finalTemplate.meta?.style) : []).map((t: any, i: number) => {
                        const isObj = typeof t === 'object';
                        const label = isObj ? JSON.stringify(t, null, 2) : String(t);
                        return (
                            <div key={`style-${i}`} style={{ width: isObj ? '100%' : 'auto', background: 'rgba(99, 102, 241, 0.1)', border: '1px solid rgba(99, 102, 241, 0.3)', color: '#a5b4fc', padding: isObj ? '12px 16px' : '6px 14px', borderRadius: isObj ? '12px' : '20px', fontSize: '0.85rem', whiteSpace: 'pre-wrap', lineHeight: '1.5' }}>
                                {isObj ? (
                                    <>
                                        <div style={{ fontWeight: 'bold', marginBottom: '8px' }}>🎭 风格:</div>
                                        <div style={{ fontFamily: 'monospace', background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>{label}</div>
                                    </>
                                ) : (
                                    <span style={{ fontWeight: 'bold' }}>🎭 风格: {label}</span>
                                )}
                            </div>
                        );
                    })}
                    {(finalTemplate.rhythmStructure?.transitionStyles || finalTemplate.rhythmStructure?.transitionStyle ? [].concat(finalTemplate.rhythmStructure?.transitionStyles || finalTemplate.rhythmStructure?.transitionStyle) : []).map((t: any, i: number) => {
                        const isObj = typeof t === 'object';
                        const label = isObj ? JSON.stringify(t, null, 2) : String(t);
                        return (
                            <div key={`trans-${i}`} style={{ width: isObj ? '100%' : 'auto', background: 'rgba(236, 72, 153, 0.1)', border: '1px solid rgba(236, 72, 153, 0.3)', color: '#f9a8d4', padding: isObj ? '12px 16px' : '6px 14px', borderRadius: isObj ? '12px' : '20px', fontSize: '0.85rem', whiteSpace: 'pre-wrap', lineHeight: '1.5' }}>
                                {isObj ? (
                                    <>
                                        <div style={{ fontWeight: 'bold', marginBottom: '8px' }}>✨ 转场:</div>
                                        <div style={{ fontFamily: 'monospace', background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>{label}</div>
                                    </>
                                ) : (
                                    <span style={{ fontWeight: 'bold' }}>✨ 转场: {label}</span>
                                )}
                            </div>
                        );
                    })}
                    {(finalTemplate.viralFactors || []).map((t: any, i: number) => {
                        if (typeof t === 'object' && t.factorName) {
                            return (
                                <div key={`viral-${i}`} style={{ width: '100%', background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)', color: '#6ee7b7', padding: '12px 16px', borderRadius: '12px', fontSize: '0.9rem', lineHeight: '1.5' }}>
                                    <div style={{ fontWeight: 'bold', marginBottom: '8px', fontSize: '1rem', display: 'flex', justifyContent: 'space-between' }}>
                                        <span>🔥 爆款因子：{t.factorName}</span>
                                        {t.weight !== undefined && <span style={{ fontSize: '0.8rem', background: 'rgba(16, 185, 129, 0.2)', padding: '2px 8px', borderRadius: '12px' }}>权重: {t.weight}</span>}
                                    </div>
                                    <div style={{ color: '#a7f3d0' }}>{t.description}</div>
                                </div>
                            );
                        }
                        const isObj = typeof t === 'object';
                        const label = isObj ? JSON.stringify(t, null, 2) : String(t);
                        return (
                            <div key={`viral-${i}`} style={{ width: '100%', background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)', color: '#6ee7b7', padding: '12px 16px', borderRadius: '12px', fontSize: '0.85rem', whiteSpace: 'pre-wrap', lineHeight: '1.5' }}>
                                <div style={{ fontWeight: 'bold', marginBottom: isObj ? '8px' : 0 }}>🔥 爆款因子 {i + 1}{isObj ? ':' : `: ${label}`}</div>
                                {isObj && <div style={{ fontFamily: 'monospace', background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>{label}</div>}
                            </div>
                        );
                    })}
                    {Object.keys(finalTemplate.categoryExtensions || {}).map((key, i) => {
                        const val = finalTemplate.categoryExtensions[key];
                        if (val === null || val === undefined) return null;
                        
                        // Handle dynamicExtensionFields specifically
                        if (key === 'dynamicExtensionFields' && Array.isArray(val)) {
                            return (
                                <div key={`ext-${i}`} style={{ width: '100%', background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.3)', color: '#fcd34d', padding: '16px', borderRadius: '12px', fontSize: '0.9rem', lineHeight: '1.5' }}>
                                    <div style={{ fontWeight: 'bold', marginBottom: '12px', fontSize: '1.05rem', color: '#fbbf24' }}>
                                        💡 动态扩展字段 (Dynamic Extension Fields)
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                        {val.map((field: any, j: number) => (
                                            <div key={`field-${j}`} style={{ background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px', borderLeft: '3px solid #f59e0b' }}>
                                                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                                                    <span style={{ fontWeight: 'bold', color: '#fde68a' }}>{field.fieldName}</span>
                                                    <span style={{ fontSize: '0.8rem', color: '#fbbf24', background: 'rgba(245, 158, 11, 0.2)', padding: '2px 8px', borderRadius: '4px' }}>
                                                        类型: {field.fieldType}
                                                    </span>
                                                </div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                                                    <span style={{ color: '#d97706' }}>取值:</span>
                                                    <span style={{ fontFamily: 'monospace', background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>
                                                        {typeof field.fieldValue === 'object' ? JSON.stringify(field.fieldValue) : String(field.fieldValue)}
                                                    </span>
                                                </div>
                                                <div style={{ color: '#fef3c7', fontSize: '0.85rem' }}>{field.description}</div>
                                                {field.allowedValues && (
                                                    <div style={{ marginTop: '6px', fontSize: '0.8rem', color: '#fcd34d' }}>
                                                        可选范围: {Array.isArray(field.allowedValues) ? field.allowedValues.join(', ') : String(field.allowedValues)}
                                                    </div>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            );
                        }

                        let displayKey = key;
                        if (key === 'discoveredCategoryId') displayKey = '识别品类 ID';
                        if (key === 'discoveredCategoryName') displayKey = '识别品类名称';
                        if (key === 'discoveredPromptOverrides') displayKey = '品类专属提示词覆盖';

                        const isObj = typeof val === 'object';
                        const label = isObj ? JSON.stringify(val, null, 2) : String(val);
                        return (
                            <div key={`ext-${i}`} style={{ width: isObj ? '100%' : 'auto', background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.3)', color: '#fcd34d', padding: isObj ? '12px 16px' : '6px 14px', borderRadius: isObj ? '12px' : '20px', fontSize: '0.85rem', whiteSpace: 'pre-wrap', lineHeight: '1.5' }}>
                                {isObj ? (
                                    <>
                                        <div style={{ fontWeight: 'bold', marginBottom: '8px' }}>💡 {displayKey}:</div>
                                        <div style={{ fontFamily: 'monospace', background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>{label}</div>
                                    </>
                                ) : (
                                    <span style={{ fontWeight: 'bold' }}>💡 {displayKey}: {label}</span>
                                )}
                            </div>
                        )
                    })}
                </div>

            </div>

        </div>
      </div>

      <style>{`
        .custom-scroll::-webkit-scrollbar {
          width: 6px;
        }
        .custom-scroll::-webkit-scrollbar-track {
          background: rgba(15, 23, 42, 0.5); 
          border-radius: 4px;
        }
        .custom-scroll::-webkit-scrollbar-thumb {
          background: #475569; 
          border-radius: 4px;
        }
        .custom-scroll::-webkit-scrollbar-thumb:hover {
          background: #64748b; 
        }

        .script-block-tooltip {
            position: absolute;
            bottom: calc(100% + 15px);
            left: 50%;
            transform: translateX(-50%) translateY(10px);
            background: rgba(15, 23, 42, 0.85);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255,255,255,0.1);
            border-radius: 12px;
            padding: 16px;
            width: max-content;
            max-width: 300px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            opacity: 0;
            visibility: hidden;
            transition: all 0.2s ease;
            z-index: 10;
            text-align: left;
        }
        
        .script-block-tooltip::after {
            content: '';
            position: absolute;
            top: 100%;
            left: 50%;
            margin-left: -8px;
            border-width: 8px;
            border-style: solid;
            border-color: rgba(15, 23, 42, 0.85) transparent transparent transparent;
        }

        .script-block-hover:hover {
            filter: brightness(1.1);
        }
        
        .script-block-hover:hover .script-block-tooltip {
            opacity: 1;
            visibility: visible;
            transform: translateX(-50%) translateY(0);
        }

        @keyframes pulseGlow {
            0% { transform: scale(0.8); opacity: 0.7; }
            100% { transform: scale(1.2); opacity: 1; }
        }
      `}</style>
    </div>
  );
}
