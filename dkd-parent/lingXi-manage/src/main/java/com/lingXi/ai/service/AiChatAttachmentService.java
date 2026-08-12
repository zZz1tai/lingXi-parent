package com.lingXi.ai.service;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.domain.AiChatAttachment;
import com.lingXi.ai.domain.dto.AiChatAttachmentAgentDTO;
import com.lingXi.ai.domain.dto.AiImageOcrRequestDTO;
import com.lingXi.ai.domain.dto.AiImageOcrResultDTO;
import com.lingXi.ai.domain.vo.AiChatAttachmentVO;
import com.lingXi.ai.domain.vo.AiChatHistoryVO;
import com.lingXi.ai.mapper.AiChatAttachmentMapper;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ModelHistory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 管理会话附件的 OSS 存储、文本提取、归属校验和短期签名地址。
 * 浏览器和 Python Agent 都不能自行指定对象地址。
 */
@Slf4j
@Service
public class AiChatAttachmentService {
    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 5;
    public static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    public static final int MAX_EXTRACTED_TEXT_CHARS = 60_000;

    private static final Pattern ATTACHMENT_ID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "csv", "log",
            "java", "py", "js", "ts", "tsx", "jsx", "vue",
            "xml", "yml", "yaml", "sql", "properties", "sh", "ps1");
    private static final Map<String, String> TEXT_MIME_TYPES = textMimeTypes();

    private final AiChatAttachmentMapper mapper;
    private final FileStorageService fileStorageService;
    private final AgentClient agentClient;

    public AiChatAttachmentService(
            AiChatAttachmentMapper mapper,
            FileStorageService fileStorageService) {
        this(mapper, fileStorageService, null);
    }

    @Autowired
    public AiChatAttachmentService(
            AiChatAttachmentMapper mapper,
            FileStorageService fileStorageService,
            AgentClient agentClient) {
        this.mapper = mapper;
        this.fileStorageService = fileStorageService;
        this.agentClient = agentClient;
    }

    /** 上传一个经过类型验证的私有会话附件。 */
    public AiChatAttachmentVO upload(
            String sessionId, String userId, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ServiceException("附件不能为空");
        }
        if (multipartFile.getSize() > MAX_FILE_BYTES) {
            throw new ServiceException("单个附件不能超过10MB");
        }

        byte[] bytes;
        try {
            bytes = multipartFile.getBytes();
        } catch (IOException exception) {
            throw new ServiceException("读取附件失败");
        }
        NormalizedFile normalized = normalizeAndExtract(
                multipartFile.getOriginalFilename(), bytes);
        String attachmentId = UUID.randomUUID().toString();
        String storedFilename = attachmentId + "." + normalized.extension();
        String storagePath = storagePath(sessionId, attachmentId);

        FileInfo fileInfo = null;
        try {
            fileInfo = fileStorageService
                    .of(bytes, normalized.originalName(), normalized.mimeType())
                    .setPath(storagePath)
                    .setSaveFilename(storedFilename)
                    .setFileAcl(Constant.ACL.PRIVATE)
                    .upload();
            if (fileInfo == null || fileInfo.getPlatform() == null
                    || fileInfo.getFilename() == null) {
                throw new ServiceException("附件上传到OSS失败");
            }

            AiChatAttachment attachment = new AiChatAttachment();
            attachment.setAttachmentId(attachmentId);
            attachment.setSessionId(sessionId);
            attachment.setUserId(userId);
            attachment.setOriginalName(normalized.originalName());
            attachment.setStoragePlatform(fileInfo.getPlatform());
            attachment.setStoragePath(fileInfo.getPath());
            attachment.setStorageFilename(fileInfo.getFilename());
            attachment.setObjectUrl(fileInfo.getUrl());
            attachment.setMimeType(normalized.mimeType());
            attachment.setFileSize((long) bytes.length);
            attachment.setAttachmentKind(normalized.kind());
            attachment.setExtractedText(normalized.extractedText());
            attachment.setExtractTruncated(normalized.truncated());
            if ("IMAGE".equals(normalized.kind())) {
                applyImageOcr(attachment);
            }
            attachment.setStatus("PENDING");
            attachment.setCreateTime(new Date());
            if (mapper.insert(attachment) != 1) {
                throw new ServiceException("保存附件信息失败");
            }
            return toView(attachment, 3600);
        } catch (RuntimeException exception) {
            if (fileInfo != null) {
                try {
                    fileStorageService.delete(fileInfo);
                } catch (RuntimeException cleanupFailure) {
                    log.warn("附件上传回滚时删除OSS对象失败，errorType={}",
                            cleanupFailure.getClass().getSimpleName());
                }
            }
            throw exception;
        }
    }

    /**
     * 解析本轮附件，并生成仅供模型本轮读取的十分钟图片签名地址。
     * 返回顺序与浏览器提交顺序一致。
     */
    public List<AiChatAttachmentAgentDTO> prepareForModel(
            List<String> attachmentIds, String sessionId, String userId) {
        List<String> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AiChatAttachment> selected = mapper.selectOwnedByIds(ids, sessionId, userId);
        if (selected.size() != ids.size()) {
            throw new ServiceException("附件不存在或无权访问");
        }
        Map<String, AiChatAttachment> byId = new HashMap<>();
        for (AiChatAttachment attachment : selected) {
            if (!"PENDING".equals(attachment.getStatus())
                    || attachment.getHistoryId() != null) {
                throw new ServiceException("附件已经发送，不能重复使用");
            }
            byId.put(attachment.getAttachmentId(), attachment);
        }

        List<AiChatAttachmentAgentDTO> payloads = new ArrayList<>();
        for (String id : ids) {
            AiChatAttachment attachment = byId.get(id);
            AiChatAttachmentAgentDTO payload = new AiChatAttachmentAgentDTO();
            payload.setAttachmentId(id);
            payload.setName(attachment.getOriginalName());
            payload.setMimeType(attachment.getMimeType());
            payload.setSize(attachment.getFileSize());
            payload.setKind(attachment.getAttachmentKind().toLowerCase(Locale.ROOT));
            payload.setTruncated(Boolean.TRUE.equals(attachment.getExtractTruncated()));
            payload.setExtractedText(attachment.getExtractedText());
            if ("IMAGE".equals(attachment.getAttachmentKind())) {
                payload.setImageUrl(requireSignedUrl(attachment, 600));
            }
            payloads.add(payload);
        }
        return payloads;
    }

    /** OCR 失败或当前存储平台不支持签名地址时保留原图理解能力。 */
    private void applyImageOcr(AiChatAttachment attachment) {
        if (agentClient == null) {
            return;
        }
        String imageUrl = signedUrl(attachment, 600);
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("跳过图片 OCR：当前存储平台无法生成临时访问地址");
            return;
        }
        try {
            AiImageOcrRequestDTO request = new AiImageOcrRequestDTO();
            request.setName(attachment.getOriginalName());
            request.setMimeType(attachment.getMimeType());
            request.setImageUrl(imageUrl);
            AiImageOcrResultDTO result = agentClient.recognizeImageText(request);
            String text = normalizeExtractedText(result == null ? null : result.getText());
            if (text == null) {
                return;
            }
            boolean truncated = text.length() > MAX_EXTRACTED_TEXT_CHARS;
            if (truncated) {
                text = text.substring(0, MAX_EXTRACTED_TEXT_CHARS);
            }
            attachment.setExtractedText(text);
            attachment.setExtractTruncated(
                    truncated || Boolean.TRUE.equals(result.getTruncated()));
        } catch (RuntimeException exception) {
            log.warn("图片 OCR 失败，继续使用原图理解，errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private static String normalizeExtractedText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\u0000", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 将仍为待发送状态的附件原子绑定到已保存的用户消息。 */
    @Transactional
    public void bindToHistory(
            List<String> attachmentIds,
            String sessionId,
            String userId,
            Long historyId) {
        List<String> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) {
            return;
        }
        if (historyId == null || mapper.bindToHistory(ids, sessionId, userId, historyId)
                != ids.size()) {
            throw new ServiceException("附件绑定到消息失败，请重新上传");
        }
    }

    /** 删除尚未发送的附件及其 OSS 对象。 */
    public void deletePending(String attachmentId, String sessionId, String userId) {
        String normalizedId = requireAttachmentId(attachmentId);
        AiChatAttachment attachment = mapper.selectOwned(normalizedId, sessionId, userId);
        if (attachment == null) {
            throw new ServiceException("附件不存在或无权访问");
        }
        if (!"PENDING".equals(attachment.getStatus()) || attachment.getHistoryId() != null) {
            throw new ServiceException("已发送的附件不能单独删除");
        }
        deleteObject(attachment);
        if (mapper.deletePendingOwned(normalizedId, sessionId, userId) != 1) {
            throw new ServiceException("删除附件记录失败");
        }
    }

    /** 将历史实体转换为带附件的一小时签名展示 VO。 */
    public List<AiChatHistoryVO> toHistoryViews(
            List<ModelHistory> histories, String userId) {
        if (histories == null || histories.isEmpty()) {
            return List.of();
        }
        List<Long> historyIds = histories.stream()
                .filter(item -> item != null && item.getId() != null)
                .map(ModelHistory::getId)
                .toList();
        if (historyIds.isEmpty()) {
            return histories.stream()
                    .filter(item -> item != null)
                    .map(item -> toHistoryView(item, List.of()))
                    .toList();
        }
        List<AiChatAttachment> attachments = mapper.selectByHistoryIds(historyIds, userId);
        Map<Long, List<AiChatAttachmentVO>> grouped = new LinkedHashMap<>();
        for (AiChatAttachment attachment : attachments) {
            grouped.computeIfAbsent(attachment.getHistoryId(), ignored -> new ArrayList<>())
                    .add(toView(attachment, 3600));
        }
        return histories.stream()
                .filter(item -> item != null)
                .map(item -> toHistoryView(
                        item, grouped.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    /** 删除会话时同步清理附件对象和元数据。 */
    public void deleteSessionAttachments(String sessionId, String userId) {
        List<AiChatAttachment> attachments = mapper.selectBySession(sessionId, userId);
        for (AiChatAttachment attachment : attachments) {
            deleteObject(attachment);
        }
        mapper.deleteBySession(sessionId, userId);
    }

    private void deleteObject(AiChatAttachment attachment) {
        try {
            if (!fileStorageService.delete(toFileInfo(attachment))) {
                throw new ServiceException("删除OSS附件失败");
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("删除OSS附件失败，attachmentIdLength={}，errorType={}",
                    attachment.getAttachmentId() == null ? 0 : attachment.getAttachmentId().length(),
                    exception.getClass().getSimpleName());
            throw new ServiceException("删除OSS附件失败");
        }
    }

    private AiChatAttachmentVO toView(AiChatAttachment attachment, int expiresSeconds) {
        AiChatAttachmentVO view = new AiChatAttachmentVO();
        view.setAttachmentId(attachment.getAttachmentId());
        view.setName(attachment.getOriginalName());
        view.setMimeType(attachment.getMimeType());
        view.setSize(attachment.getFileSize());
        view.setKind(attachment.getAttachmentKind().toLowerCase(Locale.ROOT));
        view.setTruncated(Boolean.TRUE.equals(attachment.getExtractTruncated()));
        view.setPreviewUrl(signedUrl(attachment, expiresSeconds));
        return view;
    }

    private static AiChatHistoryVO toHistoryView(
            ModelHistory history, List<AiChatAttachmentVO> attachments) {
        AiChatHistoryVO view = new AiChatHistoryVO();
        view.setId(history.getId());
        view.setSessionId(history.getSessionId());
        view.setUserId(history.getUserId());
        view.setUserName(history.getUserName());
        view.setContent(history.getContent());
        view.setUiJson(history.getUiJson());
        view.setMessageType(history.getMessageType());
        view.setModelName(history.getModelName());
        view.setTokens(history.getTokens());
        view.setCreateTime(history.getCreateTime());
        view.setUpdateTime(history.getUpdateTime());
        view.setAttachments(attachments);
        return view;
    }

    private String requireSignedUrl(AiChatAttachment attachment, int expiresSeconds) {
        String url = signedUrl(attachment, expiresSeconds);
        if (url == null || url.isBlank()) {
            throw new ServiceException("当前OSS平台无法生成图片临时访问地址");
        }
        return url;
    }

    private String signedUrl(AiChatAttachment attachment, int expiresSeconds) {
        try {
            if (!fileStorageService.isSupportPresignedUrl(attachment.getStoragePlatform())) {
                return null;
            }
            return fileStorageService.generatePresignedUrl(
                    toFileInfo(attachment),
                    Date.from(Instant.now().plusSeconds(expiresSeconds)));
        } catch (RuntimeException exception) {
            log.warn("生成附件签名地址失败，errorType={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private static FileInfo toFileInfo(AiChatAttachment attachment) {
        return new FileInfo()
                .setPlatform(attachment.getStoragePlatform())
                .setPath(attachment.getStoragePath())
                .setFilename(attachment.getStorageFilename())
                .setUrl(attachment.getObjectUrl())
                .setOriginalFilename(attachment.getOriginalName())
                .setContentType(attachment.getMimeType())
                .setSize(attachment.getFileSize());
    }

    private static List<String> normalizeIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        if (rawIds.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new ServiceException("每条消息最多上传5个附件");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            unique.add(requireAttachmentId(rawId));
        }
        if (unique.size() != rawIds.size()) {
            throw new ServiceException("附件ID不能重复");
        }
        return List.copyOf(unique);
    }

    private static String requireAttachmentId(String rawId) {
        String normalized = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        if (!ATTACHMENT_ID.matcher(normalized).matches()) {
            throw new ServiceException("附件ID格式无效");
        }
        return normalized;
    }

    private static String storagePath(String sessionId, String attachmentId) {
        String month = LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return "ai-chat/" + month + "/" + shortHash(sessionId) + "/"
                + attachmentId + "/";
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                output.append(String.format("%02x", digest[index]));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成会话存储路径", exception);
        }
    }

    private static NormalizedFile normalizeAndExtract(String rawName, byte[] bytes) {
        String originalName = FilenameUtils.getName(rawName == null ? "" : rawName).trim();
        if (originalName.isEmpty() || originalName.length() > 255) {
            throw new ServiceException("附件文件名无效");
        }
        String extension = FilenameUtils.getExtension(originalName).toLowerCase(Locale.ROOT);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            String mimeType = verifiedImageMime(extension, bytes);
            return new NormalizedFile(
                    originalName, extension, mimeType, "IMAGE", null, false);
        }
        if ("pdf".equals(extension)) {
            requirePrefix(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII), "PDF文件格式无效");
            return extractedDocument(
                    originalName, extension, "application/pdf", extractPdf(bytes));
        }
        if ("docx".equals(extension)) {
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                throw new ServiceException("Word文件格式无效");
            }
            return extractedDocument(
                    originalName,
                    extension,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    extractDocx(bytes));
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return extractedDocument(
                    originalName,
                    extension,
                    TEXT_MIME_TYPES.getOrDefault(extension, "text/plain"),
                    decodeText(bytes));
        }
        throw new ServiceException("暂不支持该附件类型");
    }

    private static NormalizedFile extractedDocument(
            String originalName, String extension, String mimeType, String text) {
        String normalized = text == null ? "" : text.replace("\u0000", "").trim();
        if (normalized.isEmpty()) {
            throw new ServiceException("附件中没有可提取的文本，扫描版PDF暂不支持");
        }
        boolean truncated = normalized.length() > MAX_EXTRACTED_TEXT_CHARS;
        if (truncated) {
            normalized = normalized.substring(0, MAX_EXTRACTED_TEXT_CHARS);
        }
        return new NormalizedFile(
                originalName, extension, mimeType, "DOCUMENT", normalized, truncated);
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(document.getNumberOfPages(), 100));
            return stripper.getText(document);
        } catch (IOException exception) {
            throw new ServiceException("PDF文本提取失败");
        }
    }

    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder output = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendBounded(output, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        appendBounded(output, cell.getText());
                    }
                }
            }
            return output.toString();
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException("Word文本提取失败");
        }
    }

    private static void appendBounded(StringBuilder output, String value) {
        if (value == null || value.isBlank()
                || output.length() > MAX_EXTRACTED_TEXT_CHARS + 1) {
            return;
        }
        if (!output.isEmpty()) {
            output.append('\n');
        }
        output.append(value);
    }

    private static String decodeText(byte[] bytes) {
        for (int index = 0; index < Math.min(bytes.length, 8192); index++) {
            if (bytes[index] == 0) {
                throw new ServiceException("文本附件包含二进制内容");
            }
        }
        try {
            return decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            try {
                return decodeStrict(bytes, Charset.forName("GB18030"));
            } catch (CharacterCodingException exception) {
                throw new ServiceException("文本附件编码不受支持，请使用UTF-8");
            }
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String verifiedImageMime(String extension, byte[] bytes) {
        if (("jpg".equals(extension) || "jpeg".equals(extension))
                && bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if ("png".equals(extension) && bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if ("gif".equals(extension) && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if ("webp".equals(extension) && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new ServiceException("图片内容与扩展名不匹配");
    }

    private static void requirePrefix(byte[] bytes, byte[] prefix, String message) {
        if (bytes.length < prefix.length) {
            throw new ServiceException(message);
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                throw new ServiceException(message);
            }
        }
    }

    private static Map<String, String> textMimeTypes() {
        Map<String, String> types = new HashMap<>();
        types.put("md", "text/markdown");
        types.put("markdown", "text/markdown");
        types.put("json", "application/json");
        types.put("csv", "text/csv");
        types.put("xml", "application/xml");
        types.put("yml", "application/yaml");
        types.put("yaml", "application/yaml");
        types.put("js", "text/javascript");
        types.put("ts", "text/typescript");
        return Map.copyOf(types);
    }

    private record NormalizedFile(
            String originalName,
            String extension,
            String mimeType,
            String kind,
            String extractedText,
            boolean truncated) {
    }
}
