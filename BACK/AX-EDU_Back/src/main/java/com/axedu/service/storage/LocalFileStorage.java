package com.axedu.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import com.axedu.config.AppProperties;
import com.axedu.config.WebConfig;

/** 로컬 디스크 저장 + HMAC 서명 URL ({base}/api/axedu/v1/files/{id}/content?expires=..&sig=..) */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String publicBaseUrl;
    private final byte[] signKey;

    public LocalFileStorage(AppProperties props) throws IOException {
        this.root = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String base = props.storage().publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.signKey = props.storage().signKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void save(String storageKey, InputStream in) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String storageKey) {
        try {
            return new UrlResource(resolve(storageKey).toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String signedUrl(String fileId, OffsetDateTime expiresAt) {
        long exp = expiresAt.toEpochSecond();
        return publicBaseUrl + WebConfig.API_BASE + "/files/" + fileId + "/content?expires=" + exp + "&sig=" + sign(fileId, exp);
    }

    @Override
    public boolean verify(String fileId, long expiresEpochSeconds, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expiresEpochSeconds) {
            return false;
        }
        return MessageDigest.isEqual(sign(fileId, expiresEpochSeconds).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String fileId, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signKey, "HmacSHA256"));
            byte[] raw = mac.doFinal((fileId + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path resolve(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return p;
    }
}
