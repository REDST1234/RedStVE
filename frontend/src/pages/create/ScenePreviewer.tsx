import React from 'react';

interface ScenePreviewerProps {
  script: any;
  sceneIndex: number;
  aspectRatio: string;
}

export const ScenePreviewer: React.FC<ScenePreviewerProps> = ({ script, sceneIndex, aspectRatio }) => {
  if (!script || !script.scenes || !script.scenes[sceneIndex]) {
    return <div style={{ color: '#94a3b8' }}>暂无分镜数据</div>;
  }

  const scene = script.scenes[sceneIndex];
  const globalStyle = script.globalStyle || {};
  const mixLevel = script.bgm?.mixLevel || 'BALANCED';
  
  // 预览区容器宽高比
  const width = aspectRatio === '16:9' ? 320 : aspectRatio === '1:1' ? 240 : 200;
  const height = aspectRatio === '16:9' ? 180 : aspectRatio === '1:1' ? 240 : 350;

  // 辅助函数：根据字分层映射实际字体
  const getFontFamily = (tier: string) => {
    switch (tier) {
      case 'title': return '"ZCOOL XiaoWei", serif';
      case 'subtitle': return '"Noto Sans SC", sans-serif';
      case 'accent': return '"Ma Shan Zheng", cursive';
      case 'ui': return 'Inter, sans-serif';
      case 'number': return '"Bebas Neue", cursive';
      case 'bodySerif': return '"Noto Serif SC", serif';
      case 'kaiStyle': return '"LXGW WenKai TC", serif';
      default: return '"Noto Sans SC", sans-serif';
    }
  };

  // 辅助函数：解析 positionPreset
  const getPositionStyles = (preset: string): React.CSSProperties => {
    switch (preset) {
      case 'hero_top': return { top: '20%', left: '50%', transform: 'translate(-50%, -50%)' };
      case 'hero_center': return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' };
      case 'hero_lower': return { bottom: '25%', left: '50%', transform: 'translate(-50%, 50%)' };
      case 'left_focus': return { top: '50%', left: '30%', transform: 'translate(-50%, -50%)' };
      case 'right_focus': return { top: '50%', right: '30%', transform: 'translate(50%, -50%)' };
      default: return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }; // 默认居中
    }
  };

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <h4 style={{ marginBottom: '16px', color: '#64748b' }}>👁️ 动态分镜预览镜 (Scene Previewer)</h4>
      
      {/* 画面预览框 */}
      <div style={{
        width: `${width}px`,
        height: `${height}px`,
        backgroundColor: globalStyle.backgroundColor || '#050F1E',
        borderRadius: '12px',
        position: 'relative',
        overflow: 'hidden',
        boxShadow: '0 10px 25px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0',
        transition: 'background-color 0.3s ease',
      }}>
        {/* 图层按顺序叠加 */}
        {scene.layers?.map((layer: any, idx: number) => {
          const { preset, params } = layer;
          
          // 渲染衬底层 backing.*
          if (preset?.startsWith('backing.')) {
            return (
              <div key={idx} style={{
                position: 'absolute',
                top: 0, left: 0, right: 0, bottom: 0,
                margin: 'auto',
                width: params?.width || '80%',
                height: params?.height || '40%',
                backgroundColor: params?.color || 'rgba(0,0,0,0.5)',
                opacity: params?.opacity || 1,
                borderRadius: `${params?.borderRadius || 8}px`,
                border: params?.borderColor ? `${params?.borderWidth || 1}px solid ${params.borderColor}` : 'none',
                backdropFilter: preset === 'backing.glass_plate' ? `blur(${params?.blurAmount || 10}px)` : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none' // 让背后的层点击透过
              }} />
            );
          }

          // 渲染文字层 text.* 和 caption.subtitle
          if (preset?.startsWith('text.') || preset === 'caption.subtitle' || preset === 'overlay.badge_pop') {
            const posStyles = getPositionStyles(params?.positionPreset);
            const fontFamily = getFontFamily(params?.fontTier || globalStyle.fontTier);
            
            // 对多文本数组 (hero_billboard) 和单文本的处理
            const textContent = params?.texts ? params.texts.join('\n') : (params?.text || '');
            
            // 针对数字滚动做特殊预览
            const displayValue = preset === 'text.counter_number' ? 
              `${params?.prefix || ''}${params?.value || 0}${params?.suffix || ''}` : textContent;

            // 针对标签胶囊的渲染
            if (preset === 'text.label_chip') {
                return (
                    <div key={idx} style={{
                        position: 'absolute',
                        ...posStyles,
                        backgroundColor: params?.bgColor || 'rgba(255,255,255,0.1)',
                        border: params?.variant === 'outlined' ? '1px solid #fff' : 'none',
                        color: params?.color || '#fff',
                        padding: '4px 8px',
                        borderRadius: `${params?.borderRadius || 16}px`,
                        fontFamily,
                        fontSize: `${(params?.fontSize || 24) / 3}px`,
                        fontWeight: params?.fontWeight || 'normal',
                        textAlign: params?.textAlign || 'center',
                        whiteSpace: 'pre-wrap',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px'
                      }}>
                        {params?.icon && <span>{params.icon}</span>}
                        <span>{params?.text || ''}</span>
                      </div>
                )
            }

            return (
              <div key={idx} style={{
                position: 'absolute',
                ...posStyles,
                color: params?.color || '#ffffff',
                fontSize: `${(params?.fontSize || 40) / 3}px`, // 缩小比例展示
                fontFamily,
                fontWeight: params?.fontWeight || 'bold',
                textAlign: params?.textAlign || 'center',
                textShadow: params?.textShadow || 'none',
                whiteSpace: 'pre-wrap',
                width: '90%',
              }}>
                {displayValue || <span style={{ opacity: 0.5 }}>(无文本)</span>}
              </div>
            );
          }

          // 如果是背景或素材图层，仅做一个标记
          if (preset?.startsWith('media.') && preset !== 'media.audio') {
             return (
                 <div key={idx} style={{
                    position: 'absolute',
                    top: '10px',
                    left: '10px',
                    backgroundColor: 'rgba(255,255,255,0.8)',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontSize: '10px',
                    color: '#333'
                 }}>
                     🖼️ {preset === 'media.video' ? '视频' : '图像'}
                 </div>
             )
          }

          return null;
        })}
      </div>
      
      {/* 底部信息栏 */}
      <div style={{ marginTop: '16px', fontSize: '0.85rem', color: '#64748b', display: 'flex', gap: '12px' }}>
        <span>分镜 {sceneIndex + 1}</span>
        <span>·</span>
        <span>{(scene.durationInFrames / 30).toFixed(1)}s</span>
        <span>·</span>
        <span style={{ color: '#8b5cf6' }}>🎵 {mixLevel}</span>
      </div>
      <div style={{ marginTop: '8px', fontSize: '0.75rem', color: '#94a3b8' }}>
        * 左侧修改参数，右侧画面实时渲染
      </div>
    </div>
  );
};
