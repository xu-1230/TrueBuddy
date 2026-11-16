package org.example.backend.module.homework.service.Impl;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.backend.module.homework.mapper.HomeworkGradingResultMapper;
import org.example.backend.module.homework.mapper.HomeworkTaskMapper;
import org.example.backend.module.homework.model.HomeworkTaskDO;
import org.example.backend.module.homework.model.vo.HomeworkResultRecordVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteRespVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实落库最小复现：内置标准工作流产出非空 records 时，
 * POST /homework/{taskId}/results 对应的 Service 是否真的在 homework_grading_result 里留下行。
 *
 * <p>用独立临时 schema，跑完即删，不碰业务库 public schema。
 */
class HomeworkResultWriteDbReproTest {

    private static final String SCHEMA = "hw_repro_" + System.currentTimeMillis();

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void initSchema() throws Exception {
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        dataSource = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5432/tb", user, password);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }

        dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:5432/tb?currentSchema=" + SCHEMA, user, password);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String script : List.of("../sql/homework_task.sql", "../sql/homework_grading_result.sql")) {
                statement.execute(Files.readString(Path.of(script)));
            }
        }

        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(HomeworkTaskMapper.class);
        configuration.addMapper(HomeworkGradingResultMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void dropSchema() throws Exception {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void writeResults_withTwoStudentRecords_persistsTwoRows() {
        SqlSession session = sqlSessionFactory.openSession(true);
        HomeworkTaskMapper taskMapper = session.getMapper(HomeworkTaskMapper.class);
        HomeworkGradingResultMapper resultMapper = session.getMapper(HomeworkGradingResultMapper.class);

        HomeworkTaskDO task = new HomeworkTaskDO();
        task.setFolderName("内置标准工作流-最小复现");
        task.setFileCount(2);
        task.setTotalSize(1024L);
        task.setStatus(1);
        taskMapper.insert(task);

        HomeworkServiceImpl service = new HomeworkServiceImpl();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "resultMapper", resultMapper);
        ReflectionTestUtils.setField(service, "transactionTemplate",
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setConversationId("conv-db-repro");
        reqVO.setRecords(List.of(record("张三", 88.0), record("李四", 76.0)));

        HomeworkResultWriteRespVO response = service.writeResults(task.getId(), reqVO);

        assertThat(response.getStatus()).isEqualTo(2);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(resultMapper.selectListByTaskId(task.getId())).hasSize(2);
        session.close();
    }

    @Test
    void writeResults_withEmptyRecords_leavesZeroRows() {
        SqlSession session = sqlSessionFactory.openSession(true);
        HomeworkTaskMapper taskMapper = session.getMapper(HomeworkTaskMapper.class);
        HomeworkGradingResultMapper resultMapper = session.getMapper(HomeworkGradingResultMapper.class);

        HomeworkTaskDO task = new HomeworkTaskDO();
        task.setFolderName("失败兜底-空回写");
        task.setFileCount(2);
        task.setTotalSize(1024L);
        task.setStatus(1);
        taskMapper.insert(task);

        HomeworkServiceImpl service = new HomeworkServiceImpl();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "resultMapper", resultMapper);
        ReflectionTestUtils.setField(service, "transactionTemplate",
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setConversationId("");
        reqVO.setRecords(List.of());

        HomeworkResultWriteRespVO response = service.writeResults(task.getId(), reqVO);

        assertThat(response.getStatus()).isEqualTo(4);
        assertThat(response.getTotal()).isZero();
        assertThat(resultMapper.selectListByTaskId(task.getId())).isEmpty();
        session.close();
    }

    private static HomeworkResultRecordVO record(String student, Double score) {
        HomeworkResultRecordVO record = new HomeworkResultRecordVO();
        record.setStudent(student);
        record.setScore(score);
        record.setGrade("良");
        record.setComment("结构完整");
        record.setDeductions(List.of("第三题失分"));
        record.setReadFiles(List.of("作业/" + student + "/第三章.docx"));
        return record;
    }
}
