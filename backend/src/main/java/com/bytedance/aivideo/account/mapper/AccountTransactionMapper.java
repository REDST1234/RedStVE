package com.bytedance.aivideo.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.account.entity.AccountTransactionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountTransactionMapper extends BaseMapper<AccountTransactionEntity> {
}
