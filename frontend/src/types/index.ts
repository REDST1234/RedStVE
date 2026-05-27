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
