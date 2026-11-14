package com.axedu.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.axedu.common.response.ApiResponse;
import com.axedu.common.security.AuthUser;
import com.axedu.common.security.CurrentUser;
import com.axedu.config.WebConfig;
import com.axedu.dto.file.UploadedFileResponse;
import com.axedu.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 파일 업로드 (계약서 3.9) + 서명 URL 로 파일 열기 */
@Tag(name = "파일", description = "첨부 파일 업로드와 서명 URL 로 열기 (계약서 3.9)")
@RestController
@RequestMapping(WebConfig.API_BASE + "/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** multipart/form-data, 필드명 file */
    @Operation(summary = "파일 업로드",
            description = "multipart/form-data, 필드명 `file` (최대 20MB). 응답의 `id` 를 메시지·민원의 `attachmentIds` 에 넣습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE — 20MB 초과",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(@CurrentUser AuthUser user,
                                                                    @Parameter(description = "업로드할 파일") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(fileService.upload(user, file)));
    }

    /** 서명 URL (인증 헤더 없음). 만료·위조면 404 */
    @Operation(summary = "파일 열기 (서명 URL)",
            description = "업로드·메시지 응답에 담긴 서명 URL 로 파일 내용을 엽니다. 인증 헤더가 필요 없고, 만료·위조면 404 입니다. 이미지는 inline, 그 밖은 attachment.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 바이너리",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 파일 없음, 서명 만료 또는 위조",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> content(@Parameter(description = "파일 ID") @PathVariable String fileId,
                                            @Parameter(description = "만료 시각 (epoch 초)") @RequestParam long expires,
                                            @Parameter(description = "서명") @RequestParam String sig) {
        FileService.FileContent fc = fileService.open(fileId, expires, sig);
        ContentDisposition disposition = (fc.file().isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fc.file().getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fc.file().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(fc.resource());
    }
}
