package org.example.backend.module.knowledgebase.service.parse;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToXMLContentHandler;
import org.example.backend.framework.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class DocumentParseService {

    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024; // 5MB

    public final TextCleaningService textCleaningService;

    public DocumentParseService(TextCleaningService textCleaningService) {
        this.textCleaningService = textCleaningService;
    }

    public String parseContentHtml(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("开始解析文件: {}", fileName);

        // 处理空文件
        if (file.isEmpty() || file.getSize() == 0) {
            log.warn("文件为空: {}", fileName);
            return "";
        }

        try (InputStream inputStream = file.getInputStream()) {
            String content = parseContentHtml(inputStream);
            String cleanedContent = textCleaningService.cleanText(content);
            log.info("文件解析成功，提取文本长度: {} 字符", cleanedContent.length());
            return cleanedContent;
        } catch (IOException | TikaException | SAXException e) {
            log.error("文件解析失败: {}", e.getMessage(), e);
            throw new ServiceException(500, "文件解析失败: " + e.getMessage());
        }
    }

    public String parseContentHtml(InputStream inputStream) throws IOException, TikaException, SAXException {
        // 1. 创建自动检测解析器
        AutoDetectParser parser = new AutoDetectParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);
        parser.parse(inputStream, handler, metadata, context);
        return handler.toString();
    }

}
