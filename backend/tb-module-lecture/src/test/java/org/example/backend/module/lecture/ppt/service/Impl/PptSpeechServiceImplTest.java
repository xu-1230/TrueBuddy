package org.example.backend.module.lecture.ppt.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.lecture.ppt.config.PptProperties;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateRespVO;
import org.example.backend.module.lecture.ppt.prompt.PptPromptConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 现场讲词（narrate）服务层的单测，钉在三个接缝上：
 * <ol>
 *   <li><b>输入校验</b>：备注与正文全空 = 没有可讲的内容，必须显式报错（前端工作项据此走「跳过」）；</li>
 *   <li><b>prompt 组装</b>：remark 蓝本 + 页面信息 + 现场进度要进 user prompt，系统提示词用 NARRATE_SYSTEM
 *       （与 optimize 的 SPEECH_SYSTEM 是两套口径）；</li>
 *   <li><b>输出清理</b>：剥掉模型的代码围栏/引号、按上限硬截断、字数与秒数按语速常数换算。</li>
 * </ol>
 *
 * <p>模型调用链（{@code prompt().system().user().call().content()}）用 Mockito deep stub 打桩，
 * 断言只盯「我们发了什么 prompt、把返回清理成了什么」——模型本身的行为不可断言。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PptSpeechServiceImplTest {

    private static final String SPEECH_TEXT = "同学们好，接下来我们看微服务的拆分原则。";

    @Mock
    private ChatClient pptChatClient;

    private PptSpeechServiceImpl service;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setUp() {
        service = new PptSpeechServiceImpl(pptChatClient, new PptProperties());
        requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(pptChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(SPEECH_TEXT);
    }

    private PptSpeechNarrateReqVO narrateReq(String title, String text, String remark) {
        PptSpeechNarrateReqVO reqVO = new PptSpeechNarrateReqVO();
        reqVO.setIndex(3);
        reqVO.setTitle(title);
        reqVO.setText(text);
        reqVO.setRemark(remark);
        PptSpeechNarrateReqVO.Context context = new PptSpeechNarrateReqVO.Context();
        context.setPageCount(12);
        context.setElapsedMin(5);
        reqVO.setContext(context);
        return reqVO;
    }

    @Test
    @DisplayName("输入校验：备注与正文全空时报「无可讲内容」，且不触发模型调用")
    void narrate_emptyInput() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.narrate(narrateReq("只有标题没有内容", "   ", "")));

        // 标题不算可讲内容：没有 remark/text 就不能讲（前端工作项据此走「跳过」）
        assertTrue(error.getMessage().contains("没有可讲的内容"));
        verify(pptChatClient, org.mockito.Mockito.never()).prompt();
    }

    @Test
    @DisplayName("正常生成：remark 蓝本 + 现场进度进 user prompt，系统提示词为 NARRATE_SYSTEM")
    void narrate_buildsPromptAndCleans() {
        PptSpeechNarrateRespVO respVO = service.narrate(narrateReq("微服务拆分", "服务按业务边界拆分。", "先讲拆分原则"));

        verify(requestSpec).system(PptPromptConstants.NARRATE_SYSTEM);
        org.mockito.ArgumentCaptor<String> userCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userCaptor.capture());
        String prompt = userCaptor.getValue();
        assertTrue(prompt.contains("现场进度：共 12 页"), prompt);
        assertTrue(prompt.contains("已讲约 5 分钟"), prompt);
        assertTrue(prompt.contains("现有备注：先讲拆分原则"), prompt);
        assertTrue(prompt.contains("正文：服务按业务边界拆分。"), prompt);

        assertEquals(SPEECH_TEXT, respVO.getSpeechText());
        assertEquals(SPEECH_TEXT.length(), respVO.getChars());
        // 21 字 ÷ 4 字/秒 ≈ 5 秒（换算常数只此一处）
        assertEquals(Math.round(SPEECH_TEXT.length() / 4.0), (long) respVO.getSeconds());
    }

    @Test
    @DisplayName("输出清理：剥掉模型的 ``` 围栏与包裹引号后才计入字数")
    void narrate_stripsModelWrappers() {
        when(callResponseSpec.content()).thenReturn("```\n" + SPEECH_TEXT + "\n```");

        PptSpeechNarrateRespVO respVO = service.narrate(narrateReq("标题", "正文", "备注"));

        assertEquals(SPEECH_TEXT, respVO.getSpeechText());
    }

    @Test
    @DisplayName("输出上限：模型写飞时按 narrateMaxChars 硬截断（不加省略号）")
    void narrate_clampsLongOutput() {
        String longText = "字".repeat(2000);
        when(callResponseSpec.content()).thenReturn(longText);

        PptSpeechNarrateRespVO respVO = service.narrate(narrateReq("标题", "正文", "备注"));

        assertEquals(500, respVO.getSpeechText().length(), "默认 narrateMaxChars=500");
        assertEquals(500, respVO.getChars());
    }

    @Test
    @DisplayName("模型调用失败或返回为空：转成 PPT_SPEECH_NARRATE_FAILED（前端走重试/跳过）")
    void narrate_modelFailure() {
        when(requestSpec.call()).thenThrow(new RuntimeException("network down"));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.narrate(narrateReq("标题", "正文", "备注")));
        assertTrue(error.getMessage().contains("现场讲词生成失败"));

        // 模型返回空串：同样按失败处理，绝不把空讲词交给队列
        // （call() 的桩刚被 thenThrow 占着，重新 when(...) 求值时会立刻抛出——改用 doReturn 语法）
        org.mockito.Mockito.doReturn(callResponseSpec).when(requestSpec).call();
        when(callResponseSpec.content()).thenReturn("   ");
        assertThrows(ServiceException.class, () -> service.narrate(narrateReq("标题", "正文", "备注")));
    }

}
