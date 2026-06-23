package com.bytedance.aivideo.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.account.entity.AccountTransactionEntity;
import com.bytedance.aivideo.account.entity.UserAccountEntity;
import com.bytedance.aivideo.account.mapper.AccountTransactionMapper;
import com.bytedance.aivideo.account.mapper.UserAccountMapper;
import com.bytedance.aivideo.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserAccountMapper userAccountMapper;
    private final AccountTransactionMapper accountTransactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductPoints(String userId, Integer amount, String bizReferenceId, String description) {
        // 1. 幂等性防御：尝试插入流水表。
        // 如果同样的 bizReferenceId + CONSUME 类型已经存在，MySQL 唯一索引会抛出 DuplicateKeyException
        AccountTransactionEntity transaction = new AccountTransactionEntity();
        transaction.setUserId(userId);
        transaction.setTransactionType("CONSUME");
        transaction.setAmount(amount);
        transaction.setBizReferenceId(bizReferenceId);
        transaction.setDescription(description);

        try {
            accountTransactionMapper.insert(transaction);
        } catch (DuplicateKeyException e) {
            log.warn("幂等性拦截：检测到重复的扣费请求。业务凭证：{}", bizReferenceId);
            throw new RuntimeException("重复的扣费请求，已被拦截：" + bizReferenceId);
        }

        // 2. 查询账户余额
        UserAccountEntity account = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>().eq(UserAccountEntity::getUserId, userId)
        );
        
        if (account == null) {
            throw new RuntimeException("账户不存在：" + userId);
        }

        if (account.getPoints() < amount) {
            throw new RuntimeException("余额不足，当前余额：" + account.getPoints());
        }

        // 3. 扣减余额并执行乐观锁更新
        account.setPoints(account.getPoints() - amount);
        
        // updateById 时，MyBatis Plus 乐观锁插件会自动加上 WHERE version = ? 并将 version + 1
        int updatedRows = userAccountMapper.updateById(account);
        
        if (updatedRows == 0) {
            // 这说明在 select 和 update 之间，有其他线程修改了这条记录，版本号过期了
            log.error("乐观锁冲突：用户 {} 的余额在扣减期间被其他请求修改，扣费失败！", userId);
            throw new RuntimeException("系统繁忙，请稍后重试 (Optimistic Lock Failed)");
        }

        log.info("成功扣除用户 {} 积分：{}。当前剩余：{}", userId, amount, account.getPoints());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundPoints(String userId, Integer amount, String bizReferenceId, String description) {
        // 1. 幂等性防御：防止同一个失败任务被重复退钱
        AccountTransactionEntity transaction = new AccountTransactionEntity();
        transaction.setUserId(userId);
        transaction.setTransactionType("REFUND");
        transaction.setAmount(amount);
        transaction.setBizReferenceId(bizReferenceId);
        transaction.setDescription(description);

        try {
            accountTransactionMapper.insert(transaction);
        } catch (DuplicateKeyException e) {
            log.warn("幂等性拦截：检测到重复的退费补偿。业务凭证：{}", bizReferenceId);
            // 这里不抛出异常，因为对于退费补偿来说，重复退费相当于已经成功退过了，我们直接 return 即可。
            return;
        }

        // 2. 查询账户并加钱
        UserAccountEntity account = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>().eq(UserAccountEntity::getUserId, userId)
        );

        if (account == null) {
            log.error("退费失败：账户不存在 {}", userId);
            return;
        }

        account.setPoints(account.getPoints() + amount);

        // 3. 执行乐观锁更新。退钱遇到并发冲突可以重试，但为了简单起见，这里抛出异常让上层重试
        int updatedRows = userAccountMapper.updateById(account);
        if (updatedRows == 0) {
            log.error("乐观锁冲突：退费失败！");
            throw new RuntimeException("系统繁忙，请稍后重试 (Optimistic Lock Failed)");
        }

        log.info("成功为用户 {} 退还积分：{}。当前剩余：{}", userId, amount, account.getPoints());
    }
}
