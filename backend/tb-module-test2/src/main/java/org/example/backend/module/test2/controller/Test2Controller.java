package org.example.backend.module.test2.controller;

import org.apache.poi.xslf.usermodel.*;
import org.example.backend.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试模块2：接口功能连通性验证
 */
@RestController
@RequestMapping("/test2")
public class Test2Controller {

    private static final String PPTX_PATH = "d:/c桌面/毕业设计/testfile/企业级应用开发综合实践 课程PPT.pptx";

    @GetMapping("/ping")
    public CommonResult<String> ping() {
        return CommonResult.success("pong");
    }

    @GetMapping("/read-pptx")
    public CommonResult<List<PptxPage>> readPptx() {
        List<PptxPage> pages = new ArrayList<>();

        File pptxFile = new File(PPTX_PATH);

        try (FileInputStream fis = new FileInputStream(pptxFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
             List<XSLFSlide> slides = ppt.getSlides();
             for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                XSLFNotes notes = slide.getNotes();
                int pageIndex = i + 1;

                // 提取该页所有文本框的文本
                StringBuilder textBuilder = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            textBuilder.append(text).append("\n");
                        }
                    }
                }

                // 提取备注
                StringBuilder remarkBuilder = new StringBuilder();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape textShape) {
                            String text = textShape.getText().trim();
                            if (!text.isEmpty()) {
                                remarkBuilder.append(text).append("\n");
                            }
                        }
                    }
                }

                PptxPage page = new PptxPage();
                page.setIndex(pageIndex);
                page.setText(textBuilder.toString().trim());
                page.setRemark(remarkBuilder.toString().trim());
                pages.add(page);
            }

        } catch (IOException e) {
            return CommonResult.error(500, "读取PPTX失败: " + e.getMessage());
        }

        return CommonResult.success(pages);
    }


    

    


}