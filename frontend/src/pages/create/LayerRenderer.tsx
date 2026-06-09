import React from 'react';

interface LayerRendererProps {
  layer: any;
  sceneIdx: number;
  layerIdx: number;
  updateLayerParam: (sceneIndex: number, layerIndex: number, paramKey: string, value: any) => void;
  updateLayerText: (sceneIndex: number, layerIndex: number, text: string) => void;
}

export const LayerRenderer: React.FC<LayerRendererProps> = ({ layer, sceneIdx, layerIdx, updateLayerParam, updateLayerText }) => {
  const { preset, params } = layer;

  // -- 提取公共属性 --
  const renderTextProps = () => (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center', marginTop: '8px' }}>
      <input type="color" value={params?.color || '#ffffff'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'color', e.target.value)} style={{ width: '28px', height: '28px', padding: 0, border: 'none', borderRadius: '4px', cursor: 'pointer' }} title="颜色" />
      <input type="number" value={params?.fontSize || 40} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'fontSize', parseInt(e.target.value))} style={{ width: '60px', fontSize: '0.75rem', padding: '4px', borderRadius: '4px', border: '1px solid #cbd5e1' }} title="字号" />
      <select value={params?.textAlign || 'center'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'textAlign', e.target.value)} style={{ fontSize: '0.75rem', padding: '4px', borderRadius: '4px', border: '1px solid #cbd5e1' }}>
        <option value="left">左对齐</option><option value="center">居中对齐</option><option value="right">右对齐</option>
      </select>
      <select value={params?.positionPreset || 'hero_center'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'positionPreset', e.target.value)} style={{ fontSize: '0.75rem', padding: '4px', borderRadius: '4px', border: '1px solid #cbd5e1' }}>
        <option value="hero_top">位置: 偏上</option><option value="hero_center">位置: 居中</option><option value="hero_lower">位置: 偏下</option><option value="left_focus">位置: 左侧</option><option value="right_focus">位置: 右侧</option>
      </select>
      <select value={params?.fontTier || 'subtitle'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'fontTier', e.target.value)} style={{ fontSize: '0.75rem', padding: '4px', borderRadius: '4px', border: '1px solid #cbd5e1' }}>
        <option value="title">艺术大字 (Title)</option><option value="subtitle">常规字幕 (Subtitle)</option><option value="accent">手写强调 (Accent)</option><option value="ui">无衬线标签 (UI)</option><option value="number">数字展示 (Number)</option><option value="bodySerif">文学衬线 (Serif)</option><option value="kaiStyle">复古楷体 (Kai)</option>
      </select>
    </div>
  );

  const renderReadOnlyTag = (icon: string, label: string, value: any) => (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', background: '#f1f5f9', color: '#475569', fontSize: '0.75rem', padding: '2px 8px', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
      {icon} {label}: {String(value)}
    </span>
  );

  const renderPreset = () => {
    // ---------------------------------------------------------
    // 1. 🌈 背景层 (Background)
    // ---------------------------------------------------------
    if (preset === 'bg.mesh_gradient') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>🌈 网格渐变背景</div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ display: 'flex', gap: '4px' }}>
              {(params?.colors || []).map((c: string, i: number) => <div key={i} style={{ width: '16px', height: '16px', background: c, borderRadius: '4px' }}/>)}
            </div>
            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>强度:</span>
            <input type="number" min="0" max="2" step="0.05" value={params?.intensity ?? 0.92} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'intensity', parseFloat(e.target.value))} style={{ width: '55px', fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1' }} />
          </div>
        </div>
      );
    }
    if (preset === 'bg.tech_grid') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>🔳 科技网格背景</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              主色: <input type="color" value={params?.backgroundColor || '#06111F'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'backgroundColor', e.target.value)} style={{ width: '22px', height: '22px', border: 'none', padding: 0, cursor: 'pointer' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              线色: <input type="color" value={params?.lineColor || '#67E8F9'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'lineColor', e.target.value)} style={{ width: '22px', height: '22px', border: 'none', padding: 0, cursor: 'pointer' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              尺寸: <input type="number" min="10" max="200" value={params?.gridSize || 76} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'gridSize', parseInt(e.target.value))} style={{ width: '48px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              漂移速度: <input type="number" min="0" max="100" value={params?.driftSpeed || 18} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'driftSpeed', parseInt(e.target.value))} style={{ width: '48px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'bg.noise_grain') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>🌫️ 噪点纹理叠加</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              不透明度: <input type="range" min="0" max="0.5" step="0.01" value={params?.grainOpacity || 0.08} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'grainOpacity', parseFloat(e.target.value))} style={{ width: '60px' }} />
              <span>{params?.grainOpacity || 0.08}</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#475569' }}>
              缩放: <input type="number" min="0.1" max="5" step="0.1" value={params?.scale || 1.0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'scale', parseFloat(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }

    // ---------------------------------------------------------
    // 2. 📹 媒体层 (Media)
    // ---------------------------------------------------------
    if (preset === 'media.image') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>🖼️ 静态图片素材</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', wordBreak: 'break-all', marginBottom: '8px' }}>{params?.src || '无素材链接'}</div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={{ fontSize: '0.75rem', color: '#475569' }}>填充模式:</span>
            <select value={params?.style?.objectFit || 'cover'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'style', { ...params?.style, objectFit: e.target.value })} style={{ fontSize: '0.75rem', padding: '2px 4px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
              <option value="cover">裁剪铺满 (Cover)</option>
              <option value="contain">完整包含 (Contain)</option>
              <option value="fill">拉伸填充 (Fill)</option>
              <option value="none">原始比例 (None)</option>
            </select>
          </div>
        </div>
      );
    }
    if (preset === 'media.video') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>📹 视频动态素材</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', wordBreak: 'break-all', marginBottom: '8px' }}>{params?.src || '无素材链接'}</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
              <input type="checkbox" checked={params?.muted || false} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'muted', e.target.checked)} /> 静音
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
              <input type="checkbox" checked={params?.loop || false} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'loop', e.target.checked)} /> 循环
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              倍速: 
              <select value={params?.playbackRate || 1} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'playbackRate', parseFloat(e.target.value))} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="0.5">0.5x</option>
                <option value="1">1.0x</option>
                <option value="1.5">1.5x</option>
                <option value="2">2.0x</option>
              </select>
            </label>
            {(params?.trimBefore || params?.trimAfter) && renderReadOnlyTag('✂️', '裁切', '已设置')}
          </div>
        </div>
      );
    }

    // ---------------------------------------------------------
    // 3. 🎬 运镜动画层 (Motion)
    // ---------------------------------------------------------
    if (preset === 'motion.ken_burns') {
      return (
        <div style={{ background: '#f8fafc', borderLeft: '3px solid #8b5cf6', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#8b5cf6', marginBottom: '8px' }}>🎥 Ken Burns 推拉运镜</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              起点缩放: <input type="number" min="0.5" max="3" step="0.05" value={params?.startScale ?? 1.0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'startScale', parseFloat(e.target.value))} style={{ width: '50px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              终点缩放: <input type="number" min="0.5" max="3" step="0.05" value={params?.endScale ?? 1.1} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'endScale', parseFloat(e.target.value))} style={{ width: '50px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'motion.float_2d5') {
      return (
        <div style={{ background: '#f8fafc', borderLeft: '3px solid #8b5cf6', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#8b5cf6', marginBottom: '8px' }}>🎈 2.5D 悬浮视效</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              浮动幅度: <input type="number" value={params?.floatAmplitude ?? 10} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'floatAmplitude', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              摇摆: <input type="number" value={params?.swayAmount ?? 2} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'swayAmount', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              透视: <input type="number" value={params?.perspective ?? 1000} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'perspective', parseInt(e.target.value))} style={{ width: '55px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
              <input type="checkbox" checked={params?.shadowEnabled || false} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'shadowEnabled', e.target.checked)} /> 开启阴影
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'motion.parallax_drift') {
      return (
        <div style={{ background: '#f8fafc', borderLeft: '3px solid #8b5cf6', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#8b5cf6', marginBottom: '8px' }}>🌊 视差漂移运镜</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              X轴范围: <input type="number" value={params?.driftRangeX ?? 0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'driftRangeX', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              Y轴范围: <input type="number" value={params?.driftRangeY ?? 0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'driftRangeY', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'motion.perspective_tilt') {
      return (
        <div style={{ background: '#f8fafc', borderLeft: '3px solid #8b5cf6', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#8b5cf6', marginBottom: '8px' }}>📐 3D 透视倾斜</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              倾斜 X: <input type="number" value={params?.rotateX ?? 0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'rotateX', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />°
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              倾斜 Y: <input type="number" value={params?.rotateY ?? 0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'rotateY', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />°
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
              <input type="checkbox" checked={params?.dynamicEnabled || false} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'dynamicEnabled', e.target.checked)} /> 动态微动
            </label>
          </div>
        </div>
      );
    }

    // ---------------------------------------------------------
    // 4. 🅰️ 文字层 (Text)
    // ---------------------------------------------------------
    if (preset === 'text.fade_title') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🅰️ 淡入大标题</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
        </div>
      );
    }
    if (preset === 'text.kinetic_pop') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>💥 弹跳爆裂强调字</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              起始缩放: <input type="number" min="0.5" max="3" step="0.1" value={params?.scaleFrom ?? 1.9} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'scaleFrom', parseFloat(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              终止缩放: <input type="number" min="0.5" max="3" step="0.1" value={params?.scaleTo ?? 1.0} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'scaleTo', parseFloat(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              起始角度: <input type="number" value={params?.rotationFrom ?? -8} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'rotationFrom', parseInt(e.target.value))} style={{ width: '40px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />°
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              进场帧数: <input type="number" min="1" max="100" value={params?.enterFrames ?? 14} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'enterFrames', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'text.hero_billboard') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🏆 英雄多句大字排版</div>
          {params?.texts?.map((t: string, i: number) => (
            <input key={i} type="text" value={t} onChange={(e) => {
              const newTexts = [...(params.texts || [])];
              newTexts[i] = e.target.value;
              updateLayerParam(sceneIdx, layerIdx, 'texts', newTexts);
            }} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px', marginBottom: '4px' }} />
          ))}
          {!params?.texts && (
             <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px', marginBottom: '4px' }} />
          )}
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              排版结构:
              <select value={params?.layoutPattern || 'center_focus'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'layoutPattern', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="center_focus">居中聚焦</option>
                <option value="four_corners">四角分布</option>
                <option value="triangle_stack">三角堆叠</option>
                <option value="top_bottom_split">上下对分</option>
                <option value="left_right_balance">左右平衡</option>
              </select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              动画模式:
              <select value={params?.animationMode || 'char_stagger'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'animationMode', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="whole_pop">整句弹出</option>
                <option value="char_stagger">单字交错</option>
                <option value="type_reveal">打字显现</option>
              </select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              动画缓动:
              <select value={params?.easingPreset || 'spring_bounce'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'easingPreset', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="spring_bounce">Q弹果冻</option>
                <option value="expo_out">指数减速</option>
                <option value="linear_fade">线性淡入</option>
              </select>
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'text.typewriter') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>⌨️ 打字机字幕</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              打字速度(帧/字): <input type="number" min="1" max="10" value={params?.charIntervalFrames ?? 2} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'charIntervalFrames', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              光标样式: <input type="text" value={params?.cursor || '|'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'cursor', e.target.value)} style={{ width: '30px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px', textAlign: 'center' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'text.mask_reveal') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🎭 遮罩揭示文字</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              揭示方向:
              <select value={params?.revealDirection || 'bottom_to_top'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'revealDirection', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="left_to_right">从左到右</option>
                <option value="right_to_left">从右到左</option>
                <option value="bottom_to_top">从下到上</option>
                <option value="top_to_bottom">从上到下</option>
              </select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              揭示时长(帧): <input type="number" min="5" max="90" value={params?.revealFrames ?? 15} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'revealFrames', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'text.word_highlight') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🖍️ 逐词高亮</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <span>高亮词词库:</span>
            <input type="text" placeholder="逗号分隔，如：极速, 体验" value={(params?.highlightWords || []).join(', ')} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'highlightWords', e.target.value.split(',').map(w => w.trim()).filter(Boolean))} className="input-field" style={{ flex: 1, fontSize: '0.75rem', padding: '4px 8px' }} />
          </div>
        </div>
      );
    }
    if (preset === 'caption.subtitle') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>💬 底部字幕</div>
          <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} className="input-field" style={{ width: '100%', fontSize: '0.85rem', padding: '6px 10px' }} />
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <span style={{ fontSize: '0.75rem', color: '#475569' }}>字幕位置:</span>
            <select value={params?.position || 'bottom_center'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'position', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px 4px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
              <option value="bottom_center">底部居中</option>
              <option value="top_center">顶部居中</option>
              <option value="center">绝对居中</option>
            </select>
          </div>
        </div>
      );
    }
    if (preset === 'text.counter_number') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🔢 滚动数字</div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '8px' }}>
            <input type="text" value={params?.prefix || ''} onChange={(e) => updateLayerParam(sceneIdx, layerIdx, 'prefix', e.target.value)} placeholder="前缀" style={{ width: '50px', fontSize: '0.8rem', padding: '4px' }} />
            <input type="number" value={params?.value || 0} onChange={(e) => updateLayerParam(sceneIdx, layerIdx, 'value', parseFloat(e.target.value))} placeholder="数值" style={{ width: '80px', fontSize: '0.8rem', padding: '4px' }} />
            <input type="text" value={params?.suffix || ''} onChange={(e) => updateLayerParam(sceneIdx, layerIdx, 'suffix', e.target.value)} placeholder="后缀" style={{ width: '50px', fontSize: '0.8rem', padding: '4px' }} />
          </div>
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              滚动时长(帧): <input type="number" min="5" max="150" value={params?.scrollFrames ?? 30} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'scrollFrames', parseInt(e.target.value))} style={{ width: '50px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }
    if (preset === 'text.label_chip') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#0f172a', marginBottom: '8px' }}>🏷️ 标签胶囊</div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '8px' }}>
            <input type="text" value={params?.icon || ''} onChange={(e) => updateLayerParam(sceneIdx, layerIdx, 'icon', e.target.value)} placeholder="图标" style={{ width: '40px', fontSize: '0.8rem', padding: '4px' }} />
            <input type="text" value={params?.text || ''} onChange={(e) => updateLayerText(sceneIdx, layerIdx, e.target.value)} placeholder="标签内容" style={{ flex: 1, fontSize: '0.8rem', padding: '4px' }} />
          </div>
          {renderTextProps()}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '8px', fontSize: '0.75rem', color: '#475569', alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              胶囊样式:
              <select value={params?.variant || 'filled'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'variant', e.target.value)} style={{ fontSize: '0.75rem', padding: '2px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff' }}>
                <option value="filled">实色填充 (Filled)</option>
                <option value="outlined">透明描边 (Outlined)</option>
                <option value="soft">半透背景 (Soft)</option>
              </select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              底色: <input type="color" value={params?.bgColor || '#3b82f6'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'bgColor', e.target.value)} style={{ width: '22px', height: '22px', border: 'none', padding: 0, cursor: 'pointer' }} />
            </label>
          </div>
        </div>
      );
    }

    // ---------------------------------------------------------
    // 5. 🔲 装饰层 (Overlay / Backing)
    // ---------------------------------------------------------
    if (preset === 'overlay.light_leak') return <div style={{ fontSize: '0.8rem', color: '#f59e0b' }}>💡 光斑泄漏特效 ({params?.durationInFrames}帧)</div>;
    if (preset === 'overlay.flash') return <div style={{ fontSize: '0.8rem', color: '#eab308' }}>⚡ 闪光特效 ({params?.enterFrames}+{params?.holdFrames}+{params?.exitFrames}帧)</div>;
    if (preset === 'overlay.badge_pop') return <div style={{ fontSize: '0.8rem', color: '#ec4899' }}>🏷️ 弹出徽章: {params?.text}</div>;
    if (preset === 'overlay.glow_frame') return <div style={{ fontSize: '0.8rem', color: '#8b5cf6' }}>✨ 发光边框 (模糊: {params?.glowBlur})</div>;
    
    if (preset === 'backing.solid_plate') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#475569', marginBottom: '8px' }}>🟫 实色衬底板</div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <input type="color" value={params?.color || '#000000'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'color', e.target.value)} style={{ width: '28px', height: '28px', padding: 0, border: 'none', borderRadius: '4px' }} />
            <input type="range" min="0" max="1" step="0.05" value={params?.opacity || 1} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'opacity', parseFloat(e.target.value))} style={{ width: '80px' }} />
            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>透明度: {params?.opacity || 1}</span>
          </div>
        </div>
      );
    }
    if (preset === 'backing.capsule') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#475569', marginBottom: '8px' }}>💊 胶囊衬底</div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <input type="color" value={params?.color || '#000000'} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'color', e.target.value)} style={{ width: '28px', height: '28px', padding: 0, border: 'none', borderRadius: '4px' }} />
            <input type="range" min="0" max="1" step="0.05" value={params?.opacity || 1} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'opacity', parseFloat(e.target.value))} style={{ width: '80px' }} />
          </div>
        </div>
      );
    }
    if (preset === 'backing.glass_plate') {
      return (
        <div style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 500, color: '#475569', marginBottom: '8px' }}>🪟 毛玻璃衬底</div>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '0.75rem', color: '#475569' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              模糊度: <input type="number" min="0" max="40" value={params?.blurAmount ?? 12} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'blurAmount', parseInt(e.target.value))} style={{ width: '45px', padding: '2px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />px
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              背景着色: <input type="text" placeholder="rgba(255,255,255,0.12)" value={params?.tintColor || ''} onChange={e => updateLayerParam(sceneIdx, layerIdx, 'tintColor', e.target.value)} style={{ width: '130px', padding: '2px 6px', border: '1px solid #cbd5e1', borderRadius: '4px' }} />
            </label>
          </div>
        </div>
      );
    }

    // ---------------------------------------------------------
    // 6. 音效轨道已经抽离处理，这里做一个兜底
    // ---------------------------------------------------------
    if (preset === 'media.audio') return null;

    return <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>未知预设: {preset}</div>;
  };

  if (preset === 'media.audio') return null; // 音轨外置
  return renderPreset();
};
