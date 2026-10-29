package com.yupi.yupicture.interfaces.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.yupicture.application.service.UserApplicationService;
import com.yupi.yupicture.infrastructure.annotation.AuthCheck;
import com.yupi.yupicture.infrastructure.common.BaseResponse;
import com.yupi.yupicture.infrastructure.common.ResultUtils;
import com.yupi.yupicture.domain.user.constant.UserConstant;
import com.yupi.yupicture.infrastructure.exception.BusinessException;
import com.yupi.yupicture.infrastructure.exception.ErrorCode;
import com.yupi.yupicture.infrastructure.exception.ThrowUtils;
import com.yupi.yupicture.infrastructure.api.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    /**
     * 头像大小上限
     */
    private static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;

    /**
     * 允许上传的头像后缀
     */
    private static final List<String> AVATAR_SUFFIX_LIST = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 头像文件名格式
     */
    private static final Pattern AVATAR_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+\\.(jpg|jpeg|png|gif|webp)$");

    @Resource
    private CosManager cosManager;

    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 头像保存目录
     */
    @Value("${file.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    /**
     * 测试文件上传
     *
     * @param multipartFile
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回可访问的地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            // 释放流
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }

    }

    /**
     * 上传头像
     *
     * @param multipartFile 头像文件
     * @param request       请求对象
     * @return 头像可访问的地址
     */
    @PostMapping("/avatar/upload")
    public BaseResponse<String> uploadAvatar(@RequestPart("file") MultipartFile multipartFile,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要上传的图片");
        ThrowUtils.throwIf(multipartFile.getSize() > AVATAR_MAX_SIZE, ErrorCode.PARAMS_ERROR, "头像不能超过 2MB");
        String contentType = multipartFile.getContentType();
        ThrowUtils.throwIf(contentType == null || !contentType.startsWith("image/"), ErrorCode.PARAMS_ERROR, "只能上传图片文件");
        String suffix = StringUtils.getFilenameExtension(multipartFile.getOriginalFilename());
        suffix = suffix == null ? "" : suffix.toLowerCase();
        ThrowUtils.throwIf(!AVATAR_SUFFIX_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "仅支持 jpg、jpeg、png、gif、webp 格式的图片");
        // 必须登录后才能上传
        userApplicationService.getLoginUser(request);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.copy(inputStream, dir.resolve(filename));
        } catch (IOException e) {
            log.error("avatar upload error, filename = {}", filename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败");
        }
        // 返回可访问的地址
        return ResultUtils.success(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                + request.getContextPath() + "/file/avatar/" + filename);
    }

    /**
     * 获取头像
     *
     * @param filename 头像文件名
     * @return 头像文件
     */
    @GetMapping("/avatar/{filename:.+}")
    public ResponseEntity<FileSystemResource> getAvatar(@PathVariable String filename) {
        ThrowUtils.throwIf(!AVATAR_FILENAME_PATTERN.matcher(filename).matches(), ErrorCode.PARAMS_ERROR, "文件名不合法");
        Path file = Paths.get(avatarDir).toAbsolutePath().normalize().resolve(filename);
        ThrowUtils.throwIf(!Files.isRegularFile(file), ErrorCode.NOT_FOUND_ERROR);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(new FileSystemResource(file));
    }
}
