package org.example.backend.framework.mybatis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编程式事务模板装配。
 *
 * <p>用于「一个用例里既有外部调用（对象存储 / 解析引擎 / 大模型 / 向量库）又有数据库写入」的场景：
 * 外部调用不能被事务包住——它既长时间占用连接，又无法随数据库一起回滚。
 * 因此把外部调用留在事务外，只让连续的数据库写入落在一个短事务里。
 *
 * <p>回滚语义与 {@code @Transactional(rollbackFor = Exception.class)} 一致：
 * {@link TransactionTemplate#execute} 对任何异常都回滚。
 */
@AutoConfiguration
public class TransactionTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
