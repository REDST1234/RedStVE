import { Project } from '../types';

export const MOCK_DECONSTRUCT_PROJECTS: Project[] = [
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

export const MOCK_CREATION_PROJECTS: Project[] = [
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
