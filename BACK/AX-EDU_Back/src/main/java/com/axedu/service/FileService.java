package com.axedu.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.common.security.AuthUser;
import com.axedu.common.util.IdGenerator;
import com.axedu.common.util.TimeUtil;
import com.axedu.config.AppProperties;
import com.axedu.dto.chat.AttachmentResponse;
import com.axedu.dto.file.UploadedFileResponse;
import com.axedu.entity.StoredFile;
import com.axedu.entity.enums.FilePurpose;
import com.axedu.entity.enums.FileStatus;
import com.axedu.repository.StoredFileRepository;
import com.axedu.service.storage.FileStorage;

import lombok.RequiredArgsConstructor;

/** POST /files 업로드, 첨부 연결, 서명 URL 발급 (계약서 3.9) */
@Service
@RequiredArgsConstructor
public class FileService {

    public static final long IMAGE_LIMIT = 10L * 1024 * 1024;
    public static final long DOCUMENT_LIMIT = 20L * 1024 * 1024;

    private final StoredFileRepository fileRepository;
    private final FileStorage storage;
    private final AppProperties props;

    @Transactional
    public UploadedFileResponse upload(AuthUser user, MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) {
            throw ApiException.validation("파일을 골라 주세요.", "file", "required");
        }
        String mime = multipart.getContentType() == null || multipart.getContentType().isBlank()
                ? "application/octet-stream" : multipart.getContentType();
        boolean image = mime.startsWith("image/");
        if (multipart.getSize() > (image ? IMAGE_LIMIT : DOCUMENT_LIMIT)) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, image ? "이미지는 10MB까지 올릴 수 있습니다." : "문서는 20MB까지 올릴 수 있습니다.");
        }
        String name = multipart.getOriginalFilename() == null || multipart.getOriginalFilename().isBlank()
                ? "file" : multipart.getOriginalFilename();
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }

        OffsetDateTime now = TimeUtil.now();
        StoredFile file = new StoredFile();
        file.setId(IdGenerator.newId(IdGenerator.FILE));
        file.setSchoolId(user.schoolId());
        file.setUploaderId(user.id());
        file.setOriginalName(name);
        file.setMimeType(mime);
        file.setSizeBytes((int) multipart.getSize());
        file.setStorageKey(user.schoolId() + "/" + now.format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/" + file.getId());
        file.setPurpose(FilePurpose.MESSAGE); // 연결될 때 MESSAGE/COMPLAINT 로 확정
        file.setStatus(FileStatus.TEMP);
        file.setExpiresAt(now.plusHours(24)); // TEMP 파일 정리 시각. TODO: 매시간 정리 배치
        try (InputStream in = multipart.getInputStream()) {
            storage.save(file.getStorageKey(), in);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "파일을 저장하지 못했습니다. 다시 시도해 주세요.");
        }
        fileRepository.save(file);

        OffsetDateTime urlExpires = urlExpiry();
        return new UploadedFileResponse(file.getId(), file.getOriginalName(), file.getMimeType(), file.getSizeBytes(),
                storage.signedUrl(file.getId(), urlExpires), urlExpires);
    }

    /**
     * 메시지·민원에 붙일 파일 확인 후 ATTACHED 로 바꾼다. 내가 올린 TEMP 파일만 가능.
     * 실패 시 400 fields.attachmentIds
     */
    @Transactional
    public List<StoredFile> attach(AuthUser user, List<String> fileIds, FilePurpose purpose) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(fileIds));
        Map<String, StoredFile> found = fileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        List<StoredFile> result = new ArrayList<>();
        for (String id : ids) {
            StoredFile f = found.get(id);
            if (f == null || !f.getUploaderId().equals(user.id()) || f.getStatus() != FileStatus.TEMP) {
                throw ApiException.validation("첨부 파일을 찾을 수 없습니다. 다시 올려 주세요.", "attachmentIds", "invalid");
            }
            f.setStatus(FileStatus.ATTACHED);
            f.setPurpose(purpose);
            f.setExpiresAt(null);
            result.add(f);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, StoredFile> findAll(Collection<String> ids) {
        return fileRepository.findAllById(ids).stream().collect(Collectors.toMap(StoredFile::getId, Function.identity()));
    }

    public AttachmentResponse toAttachment(StoredFile f) {
        String url = storage.signedUrl(f.getId(), urlExpiry());
        return new AttachmentResponse(f.getId(), f.getOriginalName(), f.getMimeType(), f.getSizeBytes(), url,
                f.isImage() ? url : null); // TODO: 썸네일 생성
    }

    /** 서명 URL 로 파일 내용 열기 (인증 헤더 없이 <img src> 로 쓰임) */
    @Transactional(readOnly = true)
    public FileContent open(String fileId, long expires, String sig) {
        if (!storage.verify(fileId, expires, sig)) {
            throw ApiException.notFound("파일을 찾을 수 없습니다.");
        }
        StoredFile f = fileRepository.findById(fileId)
                .filter(x -> x.getStatus() != FileStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound("파일을 찾을 수 없습니다."));
        return new FileContent(f, storage.load(f.getStorageKey()));
    }

    private OffsetDateTime urlExpiry() {
        return TimeUtil.now().plusMinutes(props.storage().urlTtlMinutes());
    }

    public record FileContent(StoredFile file, Resource resource) {
    }
}
