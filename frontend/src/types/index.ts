export interface Project {
  id: string;
  title: string;
  cover: string;
  status: 'working' | 'pending' | 'completed' | 'ai-fill';
  date: string;
  description: string;
  tags: string[];
}

export interface MediaInfo {
  duration?: number;
  width?: number;
  height?: number;
  fps?: number;
  codec?: string;
  bitrate?: number;
  hasAudio?: boolean;
  audioCodec?: string;
  format?: string;
}

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface DeconstructProjectApiItem {
  id: string;
  title: string;
  description?: string;
  tags?: string[];
  coverUrl?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DeconstructProjectMaterialApiItem {
  materialBizId?: string;
  taskId?: string;
  originalFileName?: string;
  filePath?: string;
  coverCandidate?: string;
  duration?: number;
  width?: number;
  height?: number;
  format?: string;
  createdAt?: string;
}

export interface DeconstructProjectDetailApiData extends DeconstructProjectApiItem {
  materials?: DeconstructProjectMaterialApiItem[];
}

export interface DeconstructProjectListApiData {
  total: number;
  page: number;
  size: number;
  list: DeconstructProjectApiItem[];
}

export interface SingleUploadData {
  materialBizId: string;
  taskId: string;
  originalFileName?: string;
  status: string;
  estimatedDuration: number;
  mediaInfo: MediaInfo;
}

export interface BatchUploadData {
  taskIds: string[];
  fileCount: number;
  status: string;
  estimatedDuration: number;
  mediaInfos: MediaInfo[];
  items?: { materialBizId: string; taskId: string; originalFileName: string; mediaInfo: MediaInfo }[];
}

export interface UploadedFileView {
  materialBizId: string;
  name: string;
  size: string;
  taskId: string;
  mediaInfo: MediaInfo;
}

export interface TaskStage {
  stageType: string;
  stageStatus: string;
  stageProgress: number;
  startedAt?: string;
  endedAt?: string;
  errorMessage?: string;
}

export interface VideoTaskResultData {
  taskId: string;
  status: string;
  videoInfo?: any;
  transcript?: any[];
  timelineLog?: any;
  categoryId?: string;
  partialFailedDimensions?: string[];
  llmAnalysis?: any;
  fatTimeline?: any;
  refinedTimeline?: any;
  videoStructureTemplate?: any;
  stages?: TaskStage[];
}

export interface CreationProjectData {
  projectId: string;
  title: string;
  description?: string;
  status: string;
  templateId?: string;
  aspectRatio?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreationProjectListData {
  total: number;
  page: number;
  size: number;
  list: CreationProjectData[];
}

export interface CreationAssetData {
  materialBizId: string;
  materialType: 'VIDEO' | 'IMAGE' | 'TEXT';
  originalFileName?: string;
  status: string;
  filePath?: string;
  fileSize?: number;
  duration?: number;
  width?: number;
  height?: number;
  format?: string;
  textContent?: string;
  profileJson?: string;
  gridPages?: CreationGridPageData[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CreationGridPageData {
  pageIndex: number;
  status: string;
  filePath?: string;
  cacheKey?: string;
  llmIncluded?: boolean;
}

export interface CreationMatchTriggerData {
  projectId: string;
  versionId: string;
  status: string;
  matchedSegmentCount: number;
  missingSegmentCount: number;
}

export interface CreationMatchResultItem {
  segmentIndex: number;
  segmentRole: string;
  matchedAssetId?: string;
  matchedHighlightId?: string;
  matchScore?: number;
  matchStatus: string;
  matchReason?: string;
  vetoReason?: string;
  adaptationPlanJson?: string;
  adaptedFilePath?: string;
}

export interface CreationMatchResultData {
  projectId: string;
  versionId: string;
  status: string;
  overallCoverage: number;
  items: CreationMatchResultItem[];
}

export interface CreationAdaptTriggerData {
  projectId: string;
  versionId: string;
  status: string;
  adaptedCount: number;
  failedCount: number;
}

export interface CreationTimelineSegmentData {
  segmentIndex: number;
  segmentRole: string;
  matchedAssetId?: string;
  matchStatus: string;
  sourcePath?: string;
  adaptedPath?: string;
  durationSeconds?: number;
}

export interface CreationTimelineData {
  projectId: string;
  versionId: string;
  status: string;
  segments: CreationTimelineSegmentData[];
}

export interface CreationConfirmAssetsData {
  projectId: string;
  totalAssets: number;
  triggeredCount: number;
  status: string;
}

export interface TemplateSummaryData {
  templateId: string;
  templateVersion: number;
  templateName: string;
  categoryId?: string;
  status: string;
  sourceTaskId?: string;
  snapshotHash?: string;
  templateJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TemplateRecommendItemData {
  rank: number;
  templateId: string;
  templateVersion: number;
  templateName: string;
  categoryId: string;
  segmentCount: number;
  finalScore: number;
  semanticScore: number;
  structureScore: number;
}

export interface TemplateRecommendData {
  projectId: string;
  recommendations: TemplateRecommendItemData[];
}

export interface BgmRecommendItemData {
  rank: number;
  audioId: string;
  audioName: string;
  bpm: number;
  overallStyle: string;
  durationSeconds: number;
  filePath: string;
  semanticScore: number;
  energyCurveScore: number;
  durationBpmScore: number;
  finalScore: number;
}

export interface BgmRecommendData {
  projectId: string;
  recommendations: BgmRecommendItemData[];
}

export interface ProjectBgmBindingData {
  projectId: string;
  versionId?: string | null;
  audioId: string;
  audioName?: string | null;
  srcPath: string;
  previewUrl?: string | null;
  sourceType: string;
  recommendScore?: number | null;
  semanticScore?: number | null;
  energyCurveScore?: number | null;
  durationBpmScore?: number | null;
  mixLevel: 'QUIET' | 'BALANCED' | 'DRIVE';
  volume: number;
  loopEnabled: boolean;
  fadeInFrames: number;
  fadeOutFrames: number;
  duckingEnabled: boolean;
  duckingRatio?: number | null;
  metadata?: Record<string, unknown> | null;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}
