import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/ToastContext';
import { deconstructApi } from '../../api/deconstruct';
import { videoApi } from '../../api/video';
import { request } from '../../services/http';
import { formatBytes, extractFileName, formatDuration } from '../../utils/format';
import { Project, UploadedFileView, VideoTaskResultData, ApiResponse } from '../../types';

const FALLBACK_COVERS = [
  'https://images.unsplash.com/photo-1611162617474-5b21e879e113?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1536440136628-849c177e76a1?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1485846234645-a62644f84728?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1626814026160-2237a95fc5a0?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80'
];

function getFallbackCover(id: string) {
  if (!id) return FALLBACK_COVERS[0];
  let sum = 0;
  for (let i = 0; i < id.length; i++) {
    sum += id.charCodeAt(i);
  }
  return FALLBACK_COVERS[sum % FALLBACK_COVERS.length];
}

export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [categoryMap, setCategoryMap] = useState<Record<string, string>>({});

  useEffect(() => {
    request<ApiResponse<{ categories: any[]; templates: any[] }>>('/v1/categories/graph-data')
      .then(res => {
        if (res.code === '0' || res.code === '200') {
          const map: Record<string, string> = {};
          res.data.categories.forEach(c => {
            map[c.categoryId] = c.categoryName;
          });
          setCategoryMap(map);
        }
      })
      .catch(e => console.warn('Failed to load categories', e));
  }, []);
  
  // States that were in App.tsx
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [deconstructStep, setDeconstructStep] = useState<'info' | 'upload' | 'processing' | 'result'>('info');
  const [isDebugMode, setIsDebugMode] = useState(false);
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFileView[]>([]);
  const [uploading, setUploading] = useState(false);
  const [startingExtraction, setStartingExtraction] = useState(false);
  const [debugAsrTriggeredTaskIds, setDebugAsrTriggeredTaskIds] = useState<string[]>([]);
  const [triggeringDebugAsr, setTriggeringDebugAsr] = useState(false);
  
  // 用于记录每个阶段在前端进入 RUNNING 状态的本地时间戳，解决跨时区/时钟不同步导致的进度条跳跃
  const stageStartedAtRef = useRef<Record<string, number>>({});

  const [deletingMaterialIds, setDeletingMaterialIds] = useState<string[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [liveTaskResult, setLiveTaskResult] = useState<VideoTaskResultData | null>(null);
  const [timelineEvents, setTimelineEvents] = useState<any[]>([]);
  const [loadingTimeline, setLoadingTimeline] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const latestMediaInfo = uploadedFiles[0]?.mediaInfo || null;

  const [projectTitle, setProjectTitle] = useState('');
  const [projectDesc, setProjectDesc] = useState('');
  const [projectTags, setProjectTags] = useState<string[]>(['混剪']);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [finalizingTemplate, setFinalizingTemplate] = useState(false);
  const [retryingStageType, setRetryingStageType] = useState<string | null>(null);
  const [progressTick, setProgressTick] = useState(() => Date.now());
  const postExtractTriggeredTaskRef = useRef<string | null>(null);
  const failureToastRef = useRef<string | null>(null);
  const visualizeRedirectedTaskRef = useRef<string | null>(null);

  const isStageSuccessStatus = (status?: string) => status === 'SUCCESS' || status === 'COMPLETED';
  const isStageRunningStatus = (status?: string) => status === 'RUNNING';
  const isStageFailedStatus = (status?: string) => status === 'FAILED';

  const getFirstFailedStage = (result: VideoTaskResultData | null) => {
    const orderedStageTypes = ['ASR', 'SCENE', 'KEYFRAME', 'TIMELINE', 'LLM'];
    for (const stageType of orderedStageTypes) {
      const stage = result?.stages?.find((item) => item.stageType === stageType);
      if (isStageFailedStatus(stage?.stageStatus)) {
        return stage;
      }
    }
    return null;
  };

  const getRetryButtonLabel = (stageType?: string) => {
    switch (stageType) {
      case 'ASR':
        return '重试语音转写';
      case 'SCENE':
        return '重试镜头切分';
      case 'KEYFRAME':
        return '重试关键帧抽取';
      case 'TIMELINE':
        return '重试模态对齐';
      case 'LLM':
        return '重试结构分析';
      default:
        return '重试当前阶段';
    }
  };

  const getStageDisplayName = (stageType?: string) => {
    switch (stageType) {
      case 'ASR':
        return '语音转写';
      case 'SCENE':
        return '镜头切分';
      case 'KEYFRAME':
        return '关键帧抽取';
      case 'TIMELINE':
        return '模态对齐';
      case 'LLM':
        return '结构分析';
      default:
        return '当前阶段';
    }
  };

  // API Load Logic
  useEffect(() => {
    if (!id || id === 'new') return;
    let cancelled = false;

    const loadData = async () => {
      try {
        const resp = await deconstructApi.getProject(id);
        if (resp.code !== '0' && resp.code !== '200') throw new Error(resp.message || '获取详情失败');
        if (cancelled) return;
        
        const data = resp.data;
        const proj: Project = {
          id: data.id,
          title: data.title,
          description: data.description || '',
          tags: data.tags || [],
          cover: data.coverUrl || '',
          status: data.status === 'COMPLETED' ? 'completed' : data.status === 'PENDING' ? 'pending' : 'working',
          date: data.createdAt ? data.createdAt.split('T')[0] : ''
        };
        setEditingProject(proj);
        setProjectTitle(proj.title);
        setProjectDesc(proj.description);
        if (proj.tags && proj.tags.length > 0) {
          setProjectTags(proj.tags);
        }
        
        if (data.materials && data.materials.length > 0) {
          const files = data.materials.map(m => ({
            materialBizId: m.materialBizId || '',
            name: m.originalFileName || extractFileName(m.filePath),
            size: '--',
            taskId: m.taskId || '',
            mediaInfo: { duration: m.duration, width: m.width, height: m.height, format: m.format }
          }));
          setUploadedFiles(files);
          
          if (files[0].taskId) {
            setLoadingTimeline(true);
            try {
              const res = await videoApi.getTaskResult(files[0].taskId, true);
              if (res.code === '0' || res.code === '200') {
                setLiveTaskResult(res.data);
              }
            } catch (e) {
              console.warn('Failed to load real timeline', e);
            } finally {
              if (!cancelled) setLoadingTimeline(false);
            }
          }
        }
      } catch (err) {
        console.warn('获取详情失败', err);
      }
    };
    void loadData();
    return () => { cancelled = true; };
  }, [id]);

  useEffect(() => {
    if (!liveTaskResult) return;
    try {
      let rTimeline = liveTaskResult.refinedTimeline;
      if (typeof rTimeline === 'string') {
        try { rTimeline = JSON.parse(rTimeline); } catch (e) {}
      }
      if (!rTimeline && liveTaskResult.videoStructureTemplate) {
        rTimeline = liveTaskResult.videoStructureTemplate;
      }
      
      let parsedEvents: any[] = [];
      if (rTimeline && rTimeline.timelineSegments && Array.isArray(rTimeline.timelineSegments)) {
          rTimeline.timelineSegments.forEach((seg: any) => {
              const timeRangeStr = seg.timeRange || "0.0s";
              const timeRangeParts = timeRangeStr.split('-').map((s: string) => {
                  const str = s.replace(/s/g, '').trim();
                  const parts = str.split(':');
                  if (parts.length === 2) return parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
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
                              if (parts.length === 2) return parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
                              return parseFloat(str) || 0;
                          });
                          const asStart = aTimeParts[0] || 0;
                          const asEnd = aTimeParts.length > 1 ? aTimeParts[1] : asStart;
                          parsedEvents.push({ timeRange: [asStart, asEnd], type: "asr", content: a.text, rawTime: a.timestamp || aTimeStr });
                      }
                  });
              }
              if (seg.visualDynamics) {
                  const action = seg.visualDynamics.translatedAction || '';
                  const cuts = seg.visualDynamics.rawCutsCount || 0;
                  const translated = action || `发生画面变动 (${cuts}次切分)`;
                  parsedEvents.push({ timeRange: [aStart, aEnd], type: "physics", content: translated, rawTime: seg.timeRange || timeRangeStr });
              }
          });
          
          parsedEvents.sort((a, b) => a.timeRange[0] - b.timeRange[0]);
      }
      setTimelineEvents(parsedEvents);
    } catch (e) {
      console.warn('Failed to parse timeline events from liveTaskResult', e);
    }

    // 更新前端记录的各阶段启动时间
    if (liveTaskResult?.stages) {
      liveTaskResult.stages.forEach(stage => {
        if (stage.stageStatus === 'RUNNING' && !stageStartedAtRef.current[stage.stageType]) {
          stageStartedAtRef.current[stage.stageType] = Date.now();
        } else if (stage.stageStatus !== 'RUNNING' && stageStartedAtRef.current[stage.stageType]) {
          delete stageStartedAtRef.current[stage.stageType];
        }
      });
    }
  }, [liveTaskResult]);

  useEffect(() => {
    if (deconstructStep !== 'processing') return;
    const timer = window.setInterval(() => setProgressTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [deconstructStep]);

  const isStageCompleted = (result: VideoTaskResultData | null, stageType: string) => {
    const stage = result?.stages?.find((s) => s.stageType === stageType);
    return isStageSuccessStatus(stage?.stageStatus);
  };

  const runPostExtractionFlow = async (taskId: string) => {
    setFinalizingTemplate(true);
    try {
      const timelineResp = await videoApi.triggerTimelineMatch(taskId);
      if (timelineResp.code !== '0' && timelineResp.code !== '200') {
        throw new Error(timelineResp.message || 'Timeline 组装失败');
      }

      const llmResp = await videoApi.triggerLlmAnalysis(taskId);
      if (llmResp.code !== '0' && llmResp.code !== '200') {
        throw new Error(llmResp.message || 'LLM 结构分析失败');
      }

      showToast('模板结构分析完成，正在进入全景视图', 'success');
      navigate('/deconstruct/detail/' + id + '/visualize');
    } catch (error) {
      const message = error instanceof Error ? error.message : '后置分析触发失败';
      showToast(message, 'error');
      // 失败后允许后续轮询重试触发
      postExtractTriggeredTaskRef.current = null;
    } finally {
      setFinalizingTemplate(false);
    }
  };

  const handleRetryFailedStage = async () => {
    const taskId = uploadedFiles[0]?.taskId;
    const failedStage = getFirstFailedStage(liveTaskResult);
    if (!taskId || !failedStage) {
      showToast('当前没有可重试的失败阶段', 'error');
      return;
    }

    setRetryingStageType(failedStage.stageType);
    setFinalizingTemplate(false);
    try {
      if (failedStage.stageType === 'TIMELINE' || failedStage.stageType === 'LLM') {
        postExtractTriggeredTaskRef.current = taskId;
      } else {
        postExtractTriggeredTaskRef.current = null;
      }
      visualizeRedirectedTaskRef.current = null;
      const resp = await videoApi.retryTask(taskId);
      if (resp.code !== '0' && resp.code !== '200') {
        throw new Error(resp.message || '重试失败');
      }
      failureToastRef.current = null;
      showToast(`${getRetryButtonLabel(failedStage.stageType)}已触发`, 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '重试失败，请稍后再试';
      showToast(message, 'error');
    } finally {
      setRetryingStageType(null);
    }
  };

  // Polling logic for real data
  useEffect(() => {
    if (deconstructStep === 'processing' && uploadedFiles.length > 0) {
      const taskId = uploadedFiles[0].taskId;
      if (!taskId) return;

      const fetchResult = async () => {
        try {
          const resp = await videoApi.getTaskResult(taskId, true);
          if (resp.code === '0' || resp.code === '200') {
            setLiveTaskResult(resp.data);
            const failedStage = getFirstFailedStage(resp.data);

            const extractionDone =
              isStageCompleted(resp.data, 'ASR') &&
              isStageCompleted(resp.data, 'SCENE') &&
              isStageCompleted(resp.data, 'KEYFRAME');
            const timelineDone = isStageCompleted(resp.data, 'TIMELINE');
            const llmDone = isStageCompleted(resp.data, 'LLM');

            if (failedStage) {
              setFinalizingTemplate(false);
              const toastKey = `${taskId}:${failedStage.stageType}:${failedStage.errorMessage || ''}`;
              if (failureToastRef.current !== toastKey) {
                failureToastRef.current = toastKey;
                showToast(
                  `${getStageDisplayName(failedStage.stageType)}失败：${failedStage.errorMessage || '请从当前阶段重试'}`,
                  'error'
                );
              }
              return;
            }

            if (!isDebugMode && extractionDone && !timelineDone && !llmDone && postExtractTriggeredTaskRef.current !== taskId) {
              postExtractTriggeredTaskRef.current = taskId;
              void runPostExtractionFlow(taskId);
            }

            if (!isDebugMode && extractionDone && timelineDone && llmDone) {
              setFinalizingTemplate(false);
              if (visualizeRedirectedTaskRef.current !== taskId) {
                visualizeRedirectedTaskRef.current = taskId;
                failureToastRef.current = null;
                showToast('模板结构分析完成，正在进入全景视图', 'success');
                navigate('/deconstruct/detail/' + id + '/visualize');
              }
              return;
            }

            if (resp.data.status === 'FAILED') {
              const toastKey = `${taskId}:FAILED`;
              if (failureToastRef.current !== toastKey) {
                failureToastRef.current = toastKey;
                showToast('拆解任务执行失败，请从失败阶段重试', 'error');
              }
            }
          }
        } catch (error) {
          console.warn('Failed to poll task result', error);
        }
      };

      fetchResult(); // Initial fetch
      const intervalId = setInterval(fetchResult, 2000);

      return () => clearInterval(intervalId);
    }
  }, [deconstructStep, uploadedFiles, isDebugMode]);
  const getStageInfo = (type: string) => {
    return liveTaskResult?.stages?.find(s => s.stageType === type);
  };
  const failedStageInfo = getFirstFailedStage(liveTaskResult);

  const estimateRunningStageProgress = (
    stage: { stageType?: string; stageProgress?: number; startedAt?: string } | undefined,
    options: { floor: number; cap: number; halfLifeSec: number }
  ) => {
    const reported = Math.max(0, Math.min(100, stage?.stageProgress ?? 10));
    // 优先使用前端记录的时间，如果拿不到，再回退使用后端时间（尝试加上 'Z' 防止被错误解析为本地时间）
    let startedAtMs = Number.NaN;
    if (stage?.stageType && stageStartedAtRef.current[stage.stageType]) {
      startedAtMs = stageStartedAtRef.current[stage.stageType];
    } else if (stage?.startedAt) {
      let timeStr = stage.startedAt;
      if (!timeStr.endsWith('Z') && !timeStr.includes('+')) timeStr += 'Z';
      startedAtMs = Date.parse(timeStr);
    }
    
    if (!Number.isFinite(startedAtMs)) {
      return reported;
    }
    const elapsedSec = Math.max(0, (progressTick - startedAtMs) / 1000);
    const growth = 1 - Math.exp(-elapsedSec / options.halfLifeSec);
    const simulated = options.floor + (options.cap - options.floor) * growth;
    return Math.min(options.cap, Math.max(reported, simulated));
  };

  const calculateProgress = () => {
    if (!liveTaskResult || !liveTaskResult.stages) return 0;
    const asr = getStageInfo('ASR');
    const scene = getStageInfo('SCENE');
    const keyframe = getStageInfo('KEYFRAME');
    const timeline = getStageInfo('TIMELINE');
    const llm = getStageInfo('LLM');

    let total = 0;
    // Base extraction: 40%
    if (isStageSuccessStatus(asr?.stageStatus)) total += 15;
    else if (isStageRunningStatus(asr?.stageStatus)) total += (asr?.stageProgress || 0) * 0.15;

    if (isStageSuccessStatus(scene?.stageStatus)) total += 15;
    else if (isStageRunningStatus(scene?.stageStatus)) total += (scene?.stageProgress || 0) * 0.15;

    if (isStageSuccessStatus(keyframe?.stageStatus)) total += 10;
    else if (isStageRunningStatus(keyframe?.stageStatus)) {
      total += estimateRunningStageProgress(keyframe, { floor: 18, cap: 85, halfLifeSec: 18 }) * 0.10;
    }

    // Timeline: 30%
    if (isStageSuccessStatus(timeline?.stageStatus)) total += 30;
    else if (isStageRunningStatus(timeline?.stageStatus)) {
      total += estimateRunningStageProgress(timeline, { floor: 20, cap: 95, halfLifeSec: 10 }) * 0.30;
    }

    // LLM: 30%
    if (isStageSuccessStatus(llm?.stageStatus)) total += 30;
    else if (isStageRunningStatus(llm?.stageStatus)) {
      // 降低 halfLifeSec 并提高 cap，加快跑条速度避免让用户觉得卡住
      total += estimateRunningStageProgress(llm, { floor: 12, cap: 98, halfLifeSec: 20 }) * 0.30;
    }

    return Math.min(100, Math.floor(total));
  };
  const currentProgress = calculateProgress();

  const toggleTag = (tag: string) => {
    setProjectTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]);
  };

  const handleSaveProject = async (): Promise<boolean> => {
    if (!projectTitle.trim()) {
      showToast('请输入项目标题，不能为空', 'error');
      return false;
    }
    setIsSaving(true);
    try {
      const payload = {
        title: projectTitle,
        description: projectDesc,
        tags: projectTags,
        coverUrl: editingProject?.cover || ''
      };
      
      try {
        const resp = editingProject 
          ? await deconstructApi.updateProject(editingProject.id, payload)
          : await deconstructApi.createProject(payload);
          
        if (resp.code !== '0' && resp.code !== '200') {
           throw new Error(resp.message || '保存失败');
        }

        const savedData = resp.data as any;
        if (savedData && savedData.id) {
          const normalizedProject: Project = {
            id: savedData.id,
            title: savedData.title ?? projectTitle,
            description: savedData.description ?? projectDesc,
            tags: savedData.tags ?? projectTags,
            cover: savedData.coverUrl ?? editingProject?.cover ?? '',
            status: savedData.status === 'COMPLETED' ? 'completed' : savedData.status === 'PENDING' ? 'pending' : 'working',
            date: savedData.createdAt ? String(savedData.createdAt).split('T')[0] : editingProject?.date ?? ''
          };
          setEditingProject(normalizedProject);
          if (!editingProject || id === 'new') {
            navigate(`/deconstruct/detail/${savedData.id}`, { replace: true });
          }
        }
        showToast('✨ 项目保存成功', 'success');
        return true;
      } catch (err) {
        console.warn('API 未就绪，走 Mock 保存逻辑', err);
        showToast('✨ 项目保存成功 (Mock)', 'success');
        return true;
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleStartWorkflow = async () => {
    const saved = await handleSaveProject();
    if (!saved) {
      return;
    }
    setDeconstructStep('upload');
  };

  const handleDeleteProject = async () => {
    if (!editingProject) return;
    setIsDeleting(true);
    try {
      try {
        const resp = await deconstructApi.deleteProject(editingProject.id);
        if (resp.code !== '0' && resp.code !== '200') {
           throw new Error(resp.message || '删除失败');
        }
      } catch (err) {
        console.warn('API 未就绪，走 Mock 删除逻辑', err);
      }
      setShowDeleteConfirm(false);
      navigateToList();
    } finally {
      setIsDeleting(false);
    }
  };

  const navigateToList = () => navigate('/deconstruct');

  const handleStartExtraction = async () => {
    const taskIds = Array.from(new Set(uploadedFiles.map((file) => file.taskId).filter(Boolean)));
    if (taskIds.length === 0) {
      showToast('未找到可执行的任务，请先上传视频', 'error');
      return;
    }
    setStartingExtraction(true);
    try {
      const responses = await Promise.all(taskIds.map((taskId) => videoApi.startExtraction(taskId)));
      const failed = responses.find((resp) => resp.code !== '0' && resp.code !== '200');
      if (failed) {
        throw new Error(failed.message || '触发提取失败');
      }
      setDeconstructStep('processing');
      // 重新开始时重置调试态，避免沿用上一轮任务触发记录。
      setDebugAsrTriggeredTaskIds([]);
      setLiveTaskResult(null);
      postExtractTriggeredTaskRef.current = null;
      showToast('已开始提取，正在执行拆解任务', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '触发提取失败，请稍后重试';
      showToast(message, 'error');
    } finally {
      setStartingExtraction(false);
    }
  };

  const handleRetryLlm = async () => {
    const taskId = uploadedFiles[0]?.taskId;
    if (!taskId) {
      showToast('未找到可执行的任务', 'error');
      return;
    }
    setStartingExtraction(true);
    try {
      const resp = await videoApi.retryLlm(taskId);
      if (resp.code !== '0' && resp.code !== '200') {
        throw new Error(resp.message || '重新拆解失败');
      }
      setDeconstructStep('processing');
      setLiveTaskResult(null);
      postExtractTriggeredTaskRef.current = null;
      showToast('已跳过前置阶段，重新启动 LLM 结构分析', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '重新拆解失败，请稍后重试';
      showToast(message, 'error');
    } finally {
      setStartingExtraction(false);
    }
  };

  const handleNextDebugStep = () => {
    void runNextDebugStep();
  };

  const runNextDebugStep = async () => {
    const unTriggeredTaskIds = uploadedFiles
      .map((file) => file.taskId)
      .filter((taskId) => !!taskId && !debugAsrTriggeredTaskIds.includes(taskId));

    if (unTriggeredTaskIds.length > 0) {
      setTriggeringDebugAsr(true);
      try {
        const triggerResponses = await Promise.all(unTriggeredTaskIds.map((taskId) => videoApi.triggerAsrDebug(taskId)));
        const failedResponse = triggerResponses.find((resp) => resp.code !== '0' && resp.code !== '200');
        if (failedResponse) {
          throw new Error(failedResponse.message || '触发 ASR Debug 分析失败');
        }
        setDebugAsrTriggeredTaskIds((prev) => [...prev, ...unTriggeredTaskIds]);
        showToast('已触发 Debug 分析', 'success');
      } catch (error) {
        const message = error instanceof Error ? error.message : '触发 ASR Debug 分析失败';
        showToast(message, 'error');
        return;
      } finally {
        setTriggeringDebugAsr(false);
      }
    } else {
      setDeconstructStep('result');
    }
  };

  const handleSaveAndReturn = () => navigateToList();

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      if (files.length > 1) showToast('仅支持单文件上传，已自动选择第一个', 'error');
      void uploadSelectedFiles([files[0]]);
    }
  };

  const handleDropUpload = async (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (uploading) return;
    const files = e.dataTransfer.files;
    const videoFiles = Array.from(files).filter(f => f.type.startsWith('video/') || f.name.endsWith('.mp4') || f.name.endsWith('.mov'));
    if (videoFiles.length === 0) {
      showToast('请上传有效的视频文件', 'error');
      return;
    }
    if (videoFiles.length > 1) showToast('仅支持单文件上传，已自动选择第一个', 'error');
    await uploadSelectedFiles([videoFiles[0]]);
  };

  const openFilePicker = () => {
    if (!uploading) fileInputRef.current?.click();
  };

  const uploadSelectedFiles = async (files: File[]) => {
    if (files.length === 0) return;
    if (uploadedFiles.length >= 1) {
      showToast('单个拆解项目仅支持上传 1 个视频文件，请先删除现有素材', 'error');
      return;
    }
    setUploadError(null);
    setUploading(true);
    try {
      const resp = await videoApi.uploadSingle(files[0], editingProject?.id, isDebugMode);
      if (resp.code !== '0') throw new Error(resp.message || '上传失败');
      
      const item: UploadedFileView = {
        materialBizId: resp.data.materialBizId,
        name: files[0].name,
        size: formatBytes(files[0].size),
        taskId: resp.data.taskId,
        mediaInfo: resp.data.mediaInfo
      };
      setUploadedFiles([item]);
    } catch (error) {
      const message = error instanceof Error ? error.message : '上传失败，请重试';
      setUploadError(message);
      showToast(message, 'error');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteUploadedFile = async (materialBizId: string) => {
    if (!materialBizId.trim()) {
      showToast('素材ID无效，无法删除', 'error');
      return;
    }
    if (deletingMaterialIds.includes(materialBizId)) return;
    
    setDeletingMaterialIds((prev) => [...prev, materialBizId]);
    setUploadError(null);
    try {
      const resp = await videoApi.deleteMaterial(materialBizId);
      if (resp.code !== '0' && resp.code !== '200') throw new Error(resp.message || '删除素材失败');

      setUploadedFiles((prev) => {
        const next = prev.filter((item) => item.materialBizId !== materialBizId);
        return next;
      });
      showToast('素材已删除', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '删除素材失败';
      setUploadError(message);
      showToast(message, 'error');
    } finally {
      setDeletingMaterialIds((prev) => prev.filter((id) => id !== materialBizId));
    }
  };

  return (
    <>
      <div className="detail-page fade-in" style={{borderColor: '#e0e7ff', display: 'flex', flexDirection: 'row', gap: '20px', padding: '24px', height: 'calc(100vh - 80px)' }}>
              
              {/* === 左侧主工作流 === */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'hidden', paddingRight: '12px', height: '100%' }}>
                <div className="detail-header" style={{ padding: '0 0 20px 0' }}>
                  <button className="back-btn" onClick={() => {
                    if (deconstructStep !== 'info') {
                      setDeconstructStep('info');
                    } else {
                      navigateToList();
                    }
                  }} title="返回项目看板">
                    <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline>
                    </svg>
                  </button>
                  <h2 className="detail-title">{editingProject ? '编辑拆解详情' : '新建结构拆解项目'}</h2>
                  
                  {deconstructStep === 'info' && (
                    <div style={{ marginLeft: 'auto', display: 'flex', gap: '12px', alignItems: 'center' }}>
                      {editingProject && (
                        <>
                          <button className="btn-outline" style={{ borderColor: '#6366f1', color: '#6366f1' }} onClick={() => navigate(`/deconstruct/detail/${id}/visualize`)}>
                            📊 模版全景视图
                          </button>
                          <button className="btn-outline" style={{ borderColor: '#ef4444', color: '#ef4444' }} onClick={() => setShowDeleteConfirm(true)} disabled={isDeleting}>
                            {isDeleting ? '删除中...' : '🗑️ 删除项目'}
                          </button>
                        </>
                      )}
                      <button className="btn-outline" onClick={handleSaveProject} disabled={isSaving}>
                        {isSaving ? '保存中...' : '💾 保存项目'}
                      </button>
                      <button className="btn-jump-flat" style={{background: '#6366f1', color: 'white', borderColor: '#4f46e5'}} onClick={() => void handleStartWorkflow()} title="进入提取工作流">
                        <span>🚀 开始拆解工作流</span>
                      </button>
                    </div>
                  )}
                </div>

                <div className="detail-body" style={{ padding: 0, display: 'flex', flex: 1, minHeight: 0 }}>
                  {deconstructStep === 'info' && (
                    <>
                      <div className="detail-left">
                        <div className="cover-preview">
                          {editingProject?.cover ? (
                            <img src={editingProject.cover} alt="cover" />
                          ) : (id && id !== 'new') ? (
                            <img src={getFallbackCover(id)} alt="cover fallback" />
                          ) : (
                            <div className="cover-placeholder">
                              <svg width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                              <span>暂无封面</span>
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="detail-mid" style={{ display: 'flex', flex: 1, flexDirection: 'row', gap: '32px', minHeight: 0 }}>
                        <div className="input-group custom-scroll" style={{ flex: 1, overflowY: 'auto', paddingRight: '12px' }}>
                          <div className="input-group">
                            <label className="input-label">项目标题 (Title)</label>
                            <input type="text" className="input-field" placeholder="输入爆款视频解析项目标题..." value={projectTitle} onChange={e => setProjectTitle(e.target.value)} />
                          </div>
                          
                          <div className="input-group" style={{marginTop: '24px'}}>
                            <label className="input-label">项目描述 (Description)</label>
                            <textarea className="input-field" placeholder="记录提取与结构拆解的核心目标..." value={projectDesc} onChange={e => setProjectDesc(e.target.value)}></textarea>
                          </div>

                          <div className="input-group" style={{marginTop: '24px'}}>
                            <label className="input-label">封面地址 (Cover URL)</label>
                            <input
                              type="text"
                              className="input-field"
                              placeholder="支持 http(s)://、/local/path、./relative/path、D:\\images\\cover.jpg"
                              value={editingProject?.cover || ''}
                              onChange={e => setEditingProject(prev => prev ? {...prev, cover: e.target.value} : null)}
                            />
                          </div>

                          {/* 移位补过来的详细参数卡片 */}
                          <div className="params-box" style={{marginTop: '32px'}}>
                            <div className="params-title">详细参数与识别结果 (Details)</div>
                            <div className="param-row"><span className="param-label">品类</span><span className="param-val" style={{color: '#3b82f6', fontWeight: 600}}>
                              {categoryMap[liveTaskResult?.categoryId || liveTaskResult?.videoStructureTemplate?.category || liveTaskResult?.videoStructureTemplate?.categoryId || ''] || liveTaskResult?.videoStructureTemplate?.category || liveTaskResult?.categoryId || '暂未识别 / 待分析'}
                            </span></div>
                            <div className="param-row"><span className="param-label">时长</span><span className="param-val">{formatDuration(latestMediaInfo?.duration)}</span></div>
                            <div className="param-row"><span className="param-label">分辨率</span><span className="param-val">{latestMediaInfo?.width && latestMediaInfo?.height ? `${latestMediaInfo.width} x ${latestMediaInfo.height}` : '---- x ----'}</span></div>
                            <div className="param-row"><span className="param-label">FPS</span><span className="param-val">{latestMediaInfo?.fps ? latestMediaInfo.fps.toFixed(3) : '--'}</span></div>
                            <div className="param-row"><span className="param-label">镜头数</span><span className="param-val">{liveTaskResult?.videoStructureTemplate?.scenes?.length || '0'}</span></div>
                          </div>
                        </div>

                        {/* 📜 多模态时序日志 (Timeline Log) - 采用新版暗黑风格与模拟数据 */}
                        <div className="timeline-log-panel" style={{ background: '#1e293b', borderRadius: '16px', border: '1px solid #334155', padding: '24px', display: 'flex', flexDirection: 'column', overflow: 'hidden', flex: 1, minHeight: 0, height: '100%' }}>
                          <div className="log-header" style={{ margin: '0 0 24px 0', fontSize: '1.1rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '8px', borderBottom: 'none' }}>
                            <span style={{ fontSize: '1.4rem' }}>⏱️</span> 多模态时序日志
                          </div>
                          <div className="log-body custom-scroll" style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', paddingRight: '12px', paddingLeft: '8px', height: '100%', minHeight: 0 }}>
                            {loadingTimeline ? (
                               <div style={{ padding: '20px', color: '#94a3b8', textAlign: 'center', fontSize: '0.9rem' }}>正在拉取底层分析数据...</div>
                            ) : timelineEvents.length === 0 ? (
                               <div style={{ padding: '40px 20px', color: '#64748b', textAlign: 'center', fontSize: '0.95rem' }}>
                                 <div style={{ fontSize: '2rem', marginBottom: '12px', opacity: 0.6 }}>📭</div>
                                 暂无时序数据，请在保存项目后开始拆解工作流
                               </div>
                            ) : (
                               <div style={{ 
                                   position: 'relative', 
                                   paddingLeft: '24px', 
                                   paddingRight: '24px', 
                                   borderLeft: '2px solid rgba(245, 158, 11, 0.3)', 
                                   borderRight: '2px solid rgba(59, 130, 246, 0.3)' 
                               }}>
                                   {timelineEvents.map((ev: any, idx: number) => {
                                    const isAsr = ev.type === 'asr';
                                    return (
                                    <div key={idx} style={{ 
                                        position: 'relative', 
                                        marginBottom: '24px',
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
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%', alignItems: isAsr ? 'flex-end' : 'flex-start' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexDirection: isAsr ? 'row-reverse' : 'row' }}>
                                                <span style={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: '0.85rem' }}>
                                                    [{ev.rawTime || `${ev.timeRange[0]}s`}]
                                                </span>
                                                {ev.type === 'physics' && (
                                                    <span style={{ 
                                                        background: 'rgba(245, 158, 11, 0.15)', 
                                                        color: '#fbbf24', 
                                                        padding: '4px 10px', 
                                                        borderRadius: '6px', 
                                                        fontSize: '0.8rem', 
                                                        fontWeight: 'bold',
                                                        border: '1px solid rgba(245, 158, 11, 0.3)'
                                                    }}>
                                                        ⚡️ 画面硬切 {ev.vel !== undefined ? `(Vel:${ev.vel}/s)` : ''}
                                                    </span>
                                                )}
                                                {ev.type === 'asr' && (
                                                    <span style={{ 
                                                        background: 'rgba(59, 130, 246, 0.15)', 
                                                        color: '#60a5fa', 
                                                        padding: '4px 10px', 
                                                        borderRadius: '6px', 
                                                        fontSize: '0.8rem', 
                                                        fontWeight: 'bold',
                                                        border: '1px solid rgba(59, 130, 246, 0.3)'
                                                    }}>
                                                        🔵 ASR台词
                                                    </span>
                                                )}
                                            </div>
                                            <div style={{ 
                                                color: isAsr ? '#f8fafc' : '#cbd5e1', 
                                                fontSize: '0.95rem', 
                                                lineHeight: 1.6,
                                                fontWeight: isAsr ? 500 : 400,
                                                maxWidth: '90%',
                                                whiteSpace: 'pre-wrap'
                                            }}>
                                                {isAsr ? `💬 ${ev.content}` : ev.content}
                                            </div>
                                        </div>
                                    </div>
                                )})}
                            </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </>
                  )}

                  {deconstructStep === 'upload' && (
                    <div className="extract-view upload-view fade-in">
                      <div className="extract-header">
                        <h3>步骤 1：上传视频素材</h3>
                        <p>请上传需要进行结构拆解和信息提取的原始视频文件。</p>
                      </div>
                      
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".mp4,.mov,video/mp4,video/quicktime"
                        style={{ display: 'none' }}
                        onChange={handleFileInputChange}
                      />

                      <div
                        className="upload-dropzone large"
                        onClick={openFilePicker}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={handleDropUpload}
                      >
                        <div className="upload-icon">
                          <svg width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                        </div>
                        <div className="upload-text">点击或拖拽视频文件到此区域</div>
                        <div className="upload-subtext">支持 MP4, MOV 格式，单文件上限 2GB</div>
                        {uploading && <div className="upload-subtext" style={{marginTop: '8px'}}>上传并探测中...</div>}
                      </div>

                      {uploadError && (
                        <div style={{ marginTop: '12px', color: '#dc2626', fontSize: '0.9rem' }}>
                          {uploadError}
                        </div>
                      )}

                      {uploadedFiles.length > 0 && (
                        <div className="uploaded-list">
                          <h4>已准备好进行提取的文件</h4>
                          {uploadedFiles.map((f, i) => (
                            <div className="uploaded-item has-tooltip" key={`${f.materialBizId}-${f.taskId}-${i}`} style={{ position: 'relative' }}>
                              <svg width="24" height="24" fill="none" stroke="#6366f1" strokeWidth="2" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
                              <span className="file-name">{f.name}</span>
                              <span className="file-size">{f.size}</span>
                              <button
                                type="button"
                                className="btn-outline"
                                disabled={uploading || !f.materialBizId || deletingMaterialIds.includes(f.materialBizId)}
                                onClick={() => void handleDeleteUploadedFile(f.materialBizId)}
                                style={{ marginLeft: '12px', padding: '4px 10px', fontSize: '0.8rem' }}
                              >
                                {deletingMaterialIds.includes(f.materialBizId) ? '删除中...' : '删除素材'}
                              </button>

                              {f.mediaInfo && (
                                <div className="ffprobe-tooltip">
                                  <div style={{ fontWeight: 600, marginBottom: '8px', color: '#334155' }}>FFprobe 基础信息</div>
                                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '0.9rem', color: '#475569' }}>
                                    <div>时长: {f.mediaInfo.duration ? formatDuration(f.mediaInfo.duration) : '--'}</div>
                                    <div>分辨率: {f.mediaInfo.width && f.mediaInfo.height ? `${f.mediaInfo.width}x${f.mediaInfo.height}` : '--'}</div>
                                    <div>FPS: {f.mediaInfo.fps ? f.mediaInfo.fps.toFixed(3) : '--'}</div>
                                    <div>编码: {f.mediaInfo.codec || '--'}</div>
                                  </div>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="extract-footer">
                        <label className="debug-toggle" style={{ display: 'none' }}>
                          <input type="checkbox" checked={isDebugMode} onChange={(e) => setIsDebugMode(e.target.checked)} />
                          <span>开启调试模式 (使用原始 JSON 展出)</span>
                        </label>
                        <div style={{ display: 'flex', gap: '12px' }}>
                          {uploadedFiles[0]?.taskId && (
                            <button
                              className="btn-outline"
                              disabled={startingExtraction}
                              onClick={() => void handleRetryLlm()}
                            >
                              重新拆解模板
                            </button>
                          )}
                          <button
                            className="btn-primary"
                            disabled={uploadedFiles.length === 0 || uploading || startingExtraction}
                            onClick={() => void handleStartExtraction()}
                          >
                            {startingExtraction ? '启动中...' : '开始提取视频'}
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {deconstructStep === 'processing' && (
                    <div className="extract-view processing-view fade-in" style={{ flex: 1, minHeight: '500px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                      <div className="glass-progress-container" style={{
                        background: 'rgba(255, 255, 255, 0.6)',
                        backdropFilter: 'blur(12px)',
                        borderRadius: '24px',
                        padding: '40px 60px',
                        boxShadow: '0 8px 32px rgba(31, 38, 135, 0.07)',
                        border: '1px solid rgba(255, 255, 255, 0.4)',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '32px',
                        width: '100%',
                        maxWidth: '700px'
                      }}>
                        <div style={{ textAlign: 'center' }}>
                          <h3 style={{ margin: '0 0 8px 0', fontSize: '1.5rem', color: failedStageInfo ? '#b91c1c' : '#1e293b' }}>
                            {failedStageInfo ? '拆解阶段异常' : 'AI 深度拆解中...'}
                          </h3>
                          <p style={{ margin: 0, color: failedStageInfo ? '#991b1b' : '#64748b' }}>
                            {failedStageInfo
                              ? `${getStageDisplayName(failedStageInfo.stageType)}执行失败，可从当前阶段继续重试`
                              : '正在进行多模态时空对齐与大模型分析'}
                          </p>
                          {failedStageInfo?.errorMessage && (
                            <p style={{ margin: '10px 0 0 0', color: '#7f1d1d', fontSize: '0.9rem', maxWidth: '560px', lineHeight: 1.6 }}>
                              {failedStageInfo.errorMessage}
                            </p>
                          )}
                          {!failedStageInfo && !isDebugMode && finalizingTemplate && (
                            <p style={{ margin: '8px 0 0 0', color: '#0f766e', fontSize: '0.92rem' }}>
                              基础提取完成，正在执行 Timeline {'->'} LLM 深度分析...
                            </p>
                          )}
                          {!failedStageInfo && isStageRunningStatus(getStageInfo('LLM')?.stageStatus) && (
                            <p style={{ margin: '8px 0 0 0', color: '#6366f1', fontSize: '0.9rem' }}>
                              大模型正在理解镜头节奏、段落结构与包装方式，请稍候片刻...
                            </p>
                          )}
                        </div>
                        
                        <div className="progress-circle-modern" style={{ position: 'relative', width: '180px', height: '180px' }}>
                          <svg viewBox="0 0 36 36" style={{ width: '100%', height: '100%', transform: 'rotate(-90deg)' }}>
                            <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="rgba(99, 102, 241, 0.1)" strokeWidth="3" />
                            <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="url(#gradient)" strokeWidth="3" strokeDasharray={currentProgress + ', 100'} style={{ transition: 'stroke-dasharray 0.5s ease' }} strokeLinecap="round" />
                            <defs>
                              <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                                <stop offset="0%" stopColor="#38bdf8" />
                                <stop offset="100%" stopColor="#6366f1" />
                              </linearGradient>
                            </defs>
                          </svg>
                          <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                            <span style={{ fontSize: '2.5rem', fontWeight: 700, color: '#1e293b', background: 'linear-gradient(90deg, #38bdf8, #6366f1)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>{currentProgress}</span>
                            <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>%</span>
                          </div>
                        </div>

                        <div className="stage-dependency-map" style={{ display: 'flex', width: '100%', justifyContent: 'space-between', position: 'relative', padding: '0 20px' }}>
                          {/* Line */}
                          <div style={{ position: 'absolute', top: '16px', left: '40px', right: '40px', height: '2px', background: '#e2e8f0', zIndex: 0 }}></div>
                          
                          {/* Stages */}
                          {[
                            {
                              key: 'extraction',
                              label: '基础提取',
                              sub: 'ASR / SCENE',
                              active: currentProgress >= 0,
                              done: isStageSuccessStatus(getStageInfo('ASR')?.stageStatus) && isStageSuccessStatus(getStageInfo('SCENE')?.stageStatus)
                            },
                            {
                              key: 'TIMELINE',
                              label: '模态对齐',
                              sub: 'Timeline Match',
                              active: isStageRunningStatus(getStageInfo('TIMELINE')?.stageStatus) || isStageSuccessStatus(getStageInfo('TIMELINE')?.stageStatus),
                              done: isStageSuccessStatus(getStageInfo('TIMELINE')?.stageStatus)
                            },
                            {
                              key: 'LLM',
                              label: '深度推断',
                              sub: 'Structure Analysis',
                              active: isStageRunningStatus(getStageInfo('LLM')?.stageStatus) || isStageSuccessStatus(getStageInfo('LLM')?.stageStatus),
                              done: isStageSuccessStatus(getStageInfo('LLM')?.stageStatus)
                            }
                          ].map((stage, i) => (
                            <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 1, gap: '8px' }}>
                              <div style={{ 
                                width: '34px', height: '34px', borderRadius: '50%', 
                                background: stage.done ? '#6366f1' : stage.active ? '#fff' : '#f8fafc',
                                border: stage.done ? 'none' : stage.active ? '2px solid #6366f1' : '2px solid #e2e8f0',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                boxShadow: stage.active && !stage.done ? '0 0 0 4px rgba(99, 102, 241, 0.1)' : 'none',
                                color: stage.done ? '#fff' : '#cbd5e1',
                                transition: 'all 0.3s ease'
                              }}>
                                {stage.done ? (
                                  <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="3" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                ) : (
                                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: stage.active ? '#6366f1' : 'transparent', transition: 'background 0.3s ease' }}></div>
                                )}
                              </div>
                              <div style={{ textAlign: 'center' }}>
                                <div style={{ fontSize: '0.85rem', fontWeight: 600, color: stage.active ? '#1e293b' : '#94a3b8', transition: 'color 0.3s ease' }}>{stage.label}</div>
                                <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>{stage.sub}</div>
                              </div>
                            </div>
                          ))}
                        </div>
                        
                        {isDebugMode && (
                          <div className="debug-actions" style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
                            <button
                              className="btn-outline"
                              onClick={handleNextDebugStep}
                              disabled={triggeringDebugAsr}
                            >
                              {triggeringDebugAsr ? '触发中...' : '手动触发下一步 (Debug)'}
                            </button>
                          </div>
                        )}

                        {failedStageInfo && (
                          <div style={{
                            width: '100%',
                            borderRadius: '16px',
                            border: '1px solid rgba(239, 68, 68, 0.18)',
                            background: 'rgba(254, 242, 242, 0.95)',
                            padding: '18px 20px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '12px'
                          }}>
                            <div style={{ fontSize: '0.95rem', fontWeight: 700, color: '#991b1b' }}>
                              失败阶段：{getStageDisplayName(failedStageInfo.stageType)}
                            </div>
                            <div style={{ fontSize: '0.88rem', color: '#7f1d1d', lineHeight: 1.6 }}>
                              我们会从这个阶段继续，不会重复执行已经成功的 ASR / SCENE / KEYFRAME。
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'center' }}>
                              <button
                                className="btn-primary"
                                style={{ minWidth: '220px' }}
                                disabled={retryingStageType === failedStageInfo.stageType}
                                onClick={() => void handleRetryFailedStage()}
                              >
                                {retryingStageType === failedStageInfo.stageType
                                  ? '重试中...'
                                  : getRetryButtonLabel(failedStageInfo.stageType)}
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {deconstructStep === 'result' && (
                    <div className="extract-view result-view fade-in">
                      <div className="result-header">
                        <h3>✅ 提取分析完成</h3>
                        <p>多维解构模型已成功分离视频的基础信息、镜头、文本与三维结构。</p>
                      </div>

                      {!isDebugMode ? (
                        <div className="result-dashboard">
                          {/* Left Pane: Media & Timeline */}
                          <div className="result-left">
                            <div className="result-card media-info">
                              <h4>基础元信息 (Media Prober)</h4>
                              <div className="info-grid">
                                <div className="info-item"><span>时长</span><strong>30.5s</strong></div>
                                <div className="info-item"><span>分辨率</span><strong>1080x1920</strong></div>
                                <div className="info-item"><span>帧率</span><strong>30 FPS</strong></div>
                                <div className="info-item"><span>镜头数</span><strong>8 个切片</strong></div>
                              </div>
                            </div>
                            
                            <div className="result-card timeline-mini">
                              <h4>多模态时序树 (Timeline)</h4>
                              <div className="log-body mini">
                                <div className="log-row"><span className="log-time">[0.0s]</span><span className="log-text">ASR: 大一早八顶不住？</span></div>
                                <div className="log-row"><span className="log-time">[1.2s]</span><span className="log-text highlight">物理异动: 像素突变</span></div>
                                <div className="log-row"><span className="log-time">[3.2s]</span><span className="log-text">ASR: 纯正手作咖啡瞬间...</span></div>
                              </div>
                            </div>
                          </div>

                          {/* Right Pane: LLM Structures */}
                          <div className="result-right">
                            <div className="result-card llm-structure">
                              <h4>LLM 三维结构分析报告</h4>
                              
                              <div className="structure-section script">
                                <h5>📜 脚本结构 (Script)</h5>
                                <div className="struct-item"><strong>Hook (0-3s):</strong> 抛出痛点问题（高燃节奏）</div>
                                <div className="struct-item"><strong>中段展开 (3-15s):</strong> 痛点放大 + 卖点反转</div>
                                <div className="struct-item"><strong>结尾 CTA (15s-):</strong> 引导点击购物车</div>
                              </div>
                              
                              <div className="structure-section rhythm">
                                <h5>⏱️ 节奏结构 (Rhythm)</h5>
                                <div className="struct-item"><strong>切换频率:</strong> 快节奏 (2.5次/秒)</div>
                                <div className="struct-item"><strong>高潮区间:</strong> 8.5s - 12.0s</div>
                              </div>
                              
                              <div className="structure-section packaging">
                                <h5>🎨 包装结构 (Packaging)</h5>
                                <div className="struct-item"><strong>字幕风格:</strong> 居中大字，带弹跳入场</div>
                                <div className="struct-item"><strong>转场偏好:</strong> 缩放转场 (Zoom-in) 为主</div>
                              </div>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="result-debug-message" style={{ padding: '48px 24px', background: '#f8fafc', borderRadius: '12px', color: '#64748b', textAlign: 'center', border: '1px dashed #cbd5e1' }}>
                          <svg width="48" height="48" fill="none" stroke="#94a3b8" strokeWidth="1.5" viewBox="0 0 24 24" style={{ marginBottom: '16px' }}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                          <div style={{ fontSize: '1.1rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>调试模式已开启</div>
                          <div>提取分析结果已在右侧 JSON 视图中展出，可供前端逻辑绑定参考。</div>
                        </div>
                      )}
                      
                      <div className="extract-footer">
                        <button className="btn-outline" onClick={() => setDeconstructStep('info')}>返回详情页</button>
                        <button className="btn-primary" onClick={handleSaveAndReturn}>保存结构化模板</button>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* === 右侧常驻的 JSON 大盘 (Debug 模式下专属) === */}
              {isDebugMode && deconstructStep !== 'info' && (
                <div className="debug-json-panel fade-in" style={{ width: '420px', background: '#0f172a', borderRadius: '12px', padding: '20px', display: 'flex', flexDirection: 'column', color: '#e2e8f0', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)', flexShrink: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px', borderBottom: '1px solid #334155', paddingBottom: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <svg width="20" height="20" fill="none" stroke="#38bdf8" strokeWidth="2" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
                      <h3 style={{ fontSize: '1.05rem', margin: 0, color: '#f8fafc' }}>Extraction JSON (Live)</h3>
                    </div>
                    <span style={{ fontSize: '0.8rem', color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 8px', borderRadius: '12px' }}>
                      {deconstructStep === 'result' ? 'Finished' : 'Syncing'}
                    </span>
                  </div>
                  
                  <div style={{ flex: 1, overflow: 'auto', fontSize: '0.85rem', fontFamily: 'Consolas, monospace', color: '#38bdf8', lineHeight: '1.6' }}>
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(liveTaskResult || { status: 'idle', message: 'Waiting for start...' }, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </div>
            
      {showDeleteConfirm && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3>⚠️ 确认删除项目</h3>
            <p>删除后相关的所有分析数据、时间轴切片将被永久清除。此操作不可撤销。</p>
            <div className="modal-actions">
              <button className="btn-outline" onClick={() => setShowDeleteConfirm(false)} disabled={isDeleting}>取消</button>
              <button className="btn-primary" style={{background: '#ef4444', borderColor: '#ef4444'}} onClick={handleDeleteProject} disabled={isDeleting}>
                {isDeleting ? '删除中...' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
