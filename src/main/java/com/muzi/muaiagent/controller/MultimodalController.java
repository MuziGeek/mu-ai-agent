package com.muzi.muaiagent.controller;

import com.muzi.muaiagent.app.MultimodalApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 多模态视觉理解 REST 控制器。
 *
 * 提供三种图片输入方式的 API：
 *   - GET  /multimodal/analyze-url      通过公网图片 URL 分析
 *   - POST /multimodal/analyze-upload   通过文件上传分析
 *   - POST /multimodal/analyze-base64   通过 Base64 编码分析
 *
 * 所有端点在 /api 上下文路径下，可通过 Knife4j 文档界面测试：
 *   http://localhost:8123/api/doc.html
 */
@Tag(name = "多模态视觉理解", description = "图片理解、OCR、视觉问答")
@RestController
@RequestMapping("/multimodal")
@RequiredArgsConstructor
public class MultimodalController {

    private final MultimodalApp multimodalApp;

    @Operation(summary = "通过图片URL分析图片")
    @GetMapping("/analyze-url")
    public ResponseEntity<Map<String, String>> analyzeByUrl(
            @Parameter(description = "图片公网URL") @RequestParam String imageUrl,
            @Parameter(description = "提问内容") @RequestParam(defaultValue = "请详细描述这张图片的内容") String question) {
        try {
            String result = multimodalApp.analyzeImageUrl(imageUrl, question);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "上传图片文件进行分析")
    @PostMapping(value = "/analyze-upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> analyzeUpload(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "提问内容") @RequestParam(defaultValue = "请详细描述这张图片的内容") String question) {
        try {
            String result = multimodalApp.analyzeImageFile(file, question);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "图片分析失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "通过Base64编码分析图片")
    @PostMapping("/analyze-base64")
    public ResponseEntity<Map<String, String>> analyzeBase64(
            @RequestBody Base64ImageRequest request) {
        try {
            String result = multimodalApp.analyzeImageBase64(
                    request.base64Image(), request.mimeType(), request.question());
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Base64 图片请求体。
     *
     * @param base64Image Base64 编码的图片数据（不含 data:image 前缀）
     * @param mimeType    MIME 类型，默认 image/jpeg
     * @param question    提问内容，默认"请详细描述这张图片的内容"
     */
    public record Base64ImageRequest(
            String base64Image,
            String mimeType,
            String question
    ) {
        public Base64ImageRequest {
            if (question == null || question.isBlank()) {
                question = "请详细描述这张图片的内容";
            }
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/jpeg";
            }
        }
    }
}
