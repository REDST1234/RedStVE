package com.bytedance.aivideo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.mockito.Answers;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地专属测试类：用于强校验“远端开发数据库”与“本地源码”的一致性。
 * 
 * 注意：由于这个测试会通过网络直接连接到你的远端 MySQL，并且去核对状态。
 * 如果网络波动可能会失败。所以加上 @Disabled，只在需要“扫雷”时手动点击 Run 执行。
 */
@SpringBootTest
@ActiveProfiles("dev") // 强制使用 dev 配置，连接远端开发库
@Disabled("这是手动挡测试：仅在怀疑别人篡改了开发库，或者合代码前手动跑一次")
public class DevDatabaseSyncTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    // 以下是剥离干扰项：我们只测数据库，所以把 Redis、MQ、向量库全部短路掉
    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    @Test
    void testDatabaseSchemaDriftAndFlywaySync() throws Exception {
        System.out.println("====== 开始进行远端开发库一致性扫描 ======");

        // 第一关：验证迁移历史有没有被篡改（防小人）
        ValidateResult validateResult = flyway.validateWithResult();
        if (!validateResult.validationSuccessful) {
            Assertions
                    .fail("❌ 危险！远端库的迁移历史与你本地的 SQL 脚本严重不符。可能是历史文件被篡改！\n详情: " + validateResult.errorDetails.errorMessage);
        }
        System.out.println("✅ 第一关通过：历史脚本校验和（Checksum）完美匹配，无篡改痕迹。");

        // 第二关：验证有没有遗漏未执行的脚本（防漏网之鱼）
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            StringBuilder sb = new StringBuilder("❌ 发现 " + pending.length + " 个尚未执行到远端库的脚本：\n");
            for (MigrationInfo info : pending) {
                sb.append("  - ").append(info.getScript()).append("\n");
            }
            Assertions.fail(sb.toString() + "请检查是否是你刚写的脚本还未触发执行，或者是同事实时提交了新脚本！");
        }
        System.out.println("✅ 第二关通过：所有本地 SQL 脚本均已完美同步到远端开发库。");

        // 第三关：纯手工打造的 MyBatis Plus 物理元数据防篡改校验（防小动作加字段）
        System.out.println("====== 开始第三关：物理表与 Java @Entity 深度比对 ======");
        List<TableInfo> tableInfos = TableInfoHelper.getTableInfos();
        if (tableInfos == null || tableInfos.isEmpty()) {
            System.out.println("⚠️ 警告：MyBatis Plus 未加载到任何实体类");
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<String> allDriftErrors = new ArrayList<>();

            for (TableInfo tableInfo : tableInfos) {
                String tableName = tableInfo.getTableName();

                // 1. 提取 Java 中定义的所有字段名和类型 (全部转小写比对)
                Map<String, Class<?>> javaColumns = new HashMap<>();
                if (tableInfo.getKeyColumn() != null) {
                    javaColumns.put(tableInfo.getKeyColumn().toLowerCase(), tableInfo.getKeyType());
                }
                tableInfo.getFieldList()
                        .forEach(field -> javaColumns.put(field.getColumn().toLowerCase(), field.getPropertyType()));

                // 2. 读取 MySQL 中该表的实际字段和类型
                Map<String, String> dbColumns = new HashMap<>();
                // getCatalog() 能够精确锁定当前连的库名（比如 aivideo），防止扫到系统表
                try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, null)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME").toLowerCase();
                        String typeName = rs.getString("TYPE_NAME"); // e.g., VARCHAR, BIGINT
                        dbColumns.put(colName, typeName);
                    }
                }

                if (dbColumns.isEmpty()) {
                    allDriftErrors.add("❌ 危险！远端库中找不到表: [" + tableName + "]，请检查 Flyway 脚本是否包含了该表的建表语句！");
                    continue;
                }

                // 3. 核心比对：找出数据库中有，但 Java 实体里没有的字段（被偷加字段）
                List<String> extraInDb = dbColumns.keySet().stream()
                        .filter(col -> !javaColumns.containsKey(col))
                        .toList();

                // 4. 核心比对：找出 Java 实体里有，但数据库里没有的字段（忘了写 Flyway 脚本，或者拼写错误）
                List<String> missingInDb = javaColumns.keySet().stream()
                        .filter(col -> !dbColumns.containsKey(col))
                        .toList();

                // 5. 核心比对：类型冲突检查
                List<String> typeMismatches = new ArrayList<>();
                for (String col : javaColumns.keySet()) {
                    if (dbColumns.containsKey(col)) {
                        Class<?> javaType = javaColumns.get(col);
                        String dbType = dbColumns.get(col);
                        if (!isTypeCompatible(javaType, dbType)) {
                            typeMismatches
                                    .add(String.format("%s (Java: %s, DB: %s)", col, javaType.getSimpleName(), dbType));
                        }
                    }
                }

                if (!extraInDb.isEmpty() || !missingInDb.isEmpty() || !typeMismatches.isEmpty()) {
                    StringBuilder errorMsg = new StringBuilder();
                    errorMsg.append(String.format("❌ 危险！检测到表 [%s] 发生 Schema Drift (架构偏离)！\n", tableName));
                    if (!extraInDb.isEmpty())
                        errorMsg.append("  - 数据库里多出的字段 (疑似被偷加): ").append(extraInDb).append("\n");
                    if (!missingInDb.isEmpty())
                        errorMsg.append("  - 数据库里缺失的字段 (疑似忘了写 SQL): ").append(missingInDb).append("\n");
                    if (!typeMismatches.isEmpty())
                        errorMsg.append("  - 数据类型严重不匹配 (容易引发反序列化报错): ").append(typeMismatches).append("\n");

                    Assertions.fail(errorMsg.toString());
                }
            }
        }
        System.out.println("✅ 第三关通过：远端物理表结构与 Java @TableName 实体类严丝合缝，没有任何篡改！");

        System.out.println("====== 扫描结束：开发库非常纯洁！ ======");
    }

    /**
     * 简易类型宽容匹配器：判断 Java 实体类的类型是否与 MySQL 的列类型相容
     */
    private boolean isTypeCompatible(Class<?> javaType, String dbTypeName) {
        if (javaType == null || dbTypeName == null)
            return true;
        String dbType = dbTypeName.toUpperCase();

        if (javaType == String.class) {
            return dbType.contains("VARCHAR") || dbType.contains("CHAR") || dbType.contains("TEXT")
                    || dbType.contains("JSON");
        } else if (javaType == Integer.class || javaType == int.class) {
            return dbType.contains("INT") || dbType.contains("BIT");
        } else if (javaType == Long.class || javaType == long.class) {
            return dbType.contains("BIGINT") || dbType.contains("INT");
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return dbType.contains("TINYINT") || dbType.contains("BIT");
        } else if (javaType == java.time.LocalDateTime.class || javaType == java.util.Date.class) {
            return dbType.contains("DATETIME") || dbType.contains("TIMESTAMP");
        } else if (javaType == java.math.BigDecimal.class) {
            return dbType.contains("DECIMAL") || dbType.contains("NUMERIC");
        } else if (javaType == Double.class || javaType == double.class || javaType == Float.class
                || javaType == float.class) {
            return dbType.contains("DOUBLE") || dbType.contains("FLOAT") || dbType.contains("DECIMAL");
        } else if (javaType.isEnum()) {
            // 枚举在库中一般存为 VARCHAR 或 TINYINT
            return dbType.contains("VARCHAR") || dbType.contains("INT") || dbType.contains("CHAR")
                    || dbType.contains("ENUM") || dbType.contains("TINYINT");
        } else if (java.util.Collection.class.isAssignableFrom(javaType)
                || java.util.Map.class.isAssignableFrom(javaType)
                || javaType.getName().startsWith("com.fasterxml.jackson.databind.JsonNode")) {
            // 集合、字典或 JSON Node 对象通常存储为 JSON 字符串
            return dbType.contains("JSON") || dbType.contains("VARCHAR") || dbType.contains("TEXT");
        }

        // 遇到无法识别的自定义复杂对象时（如通过 MyBatis TypeHandler 转换），为了防止误报暂时放行
        return true;
    }
}
