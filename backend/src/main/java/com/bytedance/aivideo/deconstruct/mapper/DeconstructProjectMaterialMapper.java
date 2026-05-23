package com.bytedance.aivideo.deconstruct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.deconstruct.entity.DeconstructProjectMaterialEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 拆解项目与素材关联 Mapper。
 */
@Mapper
public interface DeconstructProjectMaterialMapper extends BaseMapper<DeconstructProjectMaterialEntity> {
}
