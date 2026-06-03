import { request } from '../services/http';
import {
  ApiResponse,
  CreationAdaptTriggerData,
  CreationAssetData,
  CreationProjectData,
  CreationProjectListData,
  CreationMatchResultData,
  CreationMatchTriggerData,
  CreationTimelineData,
  CreationConfirmAssetsData,
  TemplateSummaryData,
  TemplateRecommendData,
  BgmRecommendData,
  ProjectBgmBindingData
} from '../types';

export const creationApi = {
  getProject: (projectId: string) =>
    request<ApiResponse<CreationProjectData>>(`/v1/creation/projects/${projectId}`, {
      method: 'GET'
    }),

  listProjects: (page = 1, size = 20, keyword?: string) =>
    request<ApiResponse<CreationProjectListData>>('/v1/creation/projects', {
      method: 'GET',
      query: {
        page,
        size,
        keyword: keyword && keyword.trim().length > 0 ? keyword.trim() : undefined
      }
    }),

  createProject: (title: string, description?: string) =>
    request<ApiResponse<CreationProjectData>>('/v1/creation/projects', {
      method: 'POST',
      body: { title, description }
    }),

  updateProject: (projectId: string, title: string, description?: string) =>
    request<ApiResponse<CreationProjectData>>(`/v1/creation/projects/${projectId}`, {
      method: 'PUT',
      body: { title, description }
    }),

  deleteProject: (projectId: string) =>
    request<ApiResponse<boolean>>(`/v1/creation/projects/${projectId}`, {
      method: 'DELETE'
    }),

  listTemplates: () =>
    request<ApiResponse<TemplateSummaryData[]>>('/v1/templates', {
      method: 'GET'
    }),

  uploadAsset: (
    projectId: string,
    materialType: 'VIDEO' | 'IMAGE' | 'TEXT',
    payload: { file?: File; textContent?: string }
  ) => {
    const formData = new FormData();
    formData.append('materialType', materialType);
    if (payload.file) {
      formData.append('file', payload.file);
    }
    if (payload.textContent) {
      formData.append('textContent', payload.textContent);
    }
    return request<ApiResponse<CreationAssetData>>(`/v1/creation/projects/${projectId}/assets`, {
      method: 'POST',
      body: formData
    });
  },

  listAssets: (projectId: string) =>
    request<ApiResponse<CreationAssetData[]>>(`/v1/creation/projects/${projectId}/assets`, {
      method: 'GET'
    }),

  deleteAsset: (projectId: string, materialBizId: string) =>
    request<ApiResponse<boolean>>(`/v1/creation/projects/${projectId}/assets/${materialBizId}`, {
      method: 'DELETE'
    }),

  confirmAssets: (projectId: string, type?: 'VIDEO' | 'IMAGE' | 'TEXT') =>
    request<ApiResponse<CreationConfirmAssetsData>>(`/v1/creation/projects/${projectId}/assets/confirm`, {
      method: 'POST',
      query: type ? { type } : undefined
    }),

  bindTemplate: (projectId: string, templateId: string, templateVersion: number) =>
    request<ApiResponse<CreationProjectData>>(`/v1/creation/projects/${projectId}/bind-template`, {
      method: 'POST',
      body: { templateId, templateVersion }
    }),

  recommendTemplates: (projectId: string, w1: number = 0.6, w2: number = 0.4, topN: number = 10) =>
    request<ApiResponse<TemplateRecommendData>>(`/v1/creation/projects/${projectId}/recommend-templates`, {
      method: 'POST',
      body: { w1, w2, topN }
    }),

  recommendBgm: (projectId: string, w1: number = 0.35, w2: number = 0.5, w3: number = 0.15, topN: number = 5) =>
    request<ApiResponse<BgmRecommendData>>(`/v1/creation/projects/${projectId}/recommend-bgm`, {
      method: 'POST',
      body: { w1, w2, w3, topN }
    }),

  selectBgm: (
    projectId: string,
    payload: {
      audioId: string;
      audioName?: string;
      filePath: string;
      sourceType?: string;
      recommendScore?: number;
      semanticScore?: number;
      energyCurveScore?: number;
      durationBpmScore?: number;
      mixLevel?: 'QUIET' | 'BALANCED' | 'DRIVE';
      loopEnabled?: boolean;
      fadeInFrames?: number;
      fadeOutFrames?: number;
      duckingEnabled?: boolean;
      duckingRatio?: number;
      metadata?: Record<string, unknown>;
    }
  ) =>
    request<ApiResponse<ProjectBgmBindingData>>(`/v1/creation/projects/${projectId}/bgm/select`, {
      method: 'POST',
      body: payload
    }),

  getSelectedBgm: (projectId: string) =>
    request<ApiResponse<ProjectBgmBindingData | null>>(`/v1/creation/projects/${projectId}/bgm`, {
      method: 'GET'
    }),

  clearSelectedBgm: (projectId: string) =>
    request<ApiResponse<boolean>>(`/v1/creation/projects/${projectId}/bgm`, {
      method: 'DELETE'
    }),

  triggerMatch: (projectId: string, versionId?: string) =>
    request<ApiResponse<CreationMatchTriggerData>>(`/v1/creation/projects/${projectId}/match`, {
      method: 'POST',
      body: versionId ? { versionId } : {}
    }),

  getMatchResult: (projectId: string, versionId?: string) =>
    request<ApiResponse<CreationMatchResultData>>(`/v1/creation/projects/${projectId}/match-result`, {
      method: 'GET',
      query: versionId ? { versionId } : undefined
    }),

  triggerAdapt: (projectId: string, versionId?: string) =>
    request<ApiResponse<CreationAdaptTriggerData>>(`/v1/creation/projects/${projectId}/adapt`, {
      method: 'POST',
      body: versionId ? { versionId } : {}
    }),

  getTimeline: (projectId: string, versionId?: string) =>
    request<ApiResponse<CreationTimelineData>>(`/v1/creation/projects/${projectId}/timeline`, {
      method: 'GET',
      query: versionId ? { versionId } : undefined
    }),

  generateVideo: (projectId: string, payload?: { aspectRatio?: string }) =>
    request<ApiResponse<boolean>>(`/v1/creation/projects/${projectId}/generate`, {
      method: 'POST',
      body: payload ?? {}
    }),

  regenerateVideo: (projectId: string, payload?: { aspectRatio?: string }) =>
    request<ApiResponse<boolean>>(`/v1/creation/projects/${projectId}/regenerate`, {
      method: 'POST',
      body: payload ?? {}
    }),

  getRenderStatus: (projectId: string) =>
    request<ApiResponse<{ taskId: string; status: string; progress: number; outputPath?: string; error?: string }>>(`/v1/creation/projects/${projectId}/render-status`, {
      method: 'GET'
    })
};
