package com.bytedance.aivideo.account.service;

public interface AccountService {

    /**
     * 扣减积分
     *
     * @param userId         用户ID
     * @param amount         扣除数量
     * @param bizReferenceId 外部业务唯一凭证（防止重复扣费）
     * @param description    描述
     */
    void deductPoints(String userId, Integer amount, String bizReferenceId, String description);

    /**
     * 退还积分（死信补偿）
     *
     * @param userId         用户ID
     * @param amount         退还数量
     * @param bizReferenceId 外部业务唯一凭证（对应的原交易凭证）
     * @param description    描述
     */
    void refundPoints(String userId, Integer amount, String bizReferenceId, String description);
}
