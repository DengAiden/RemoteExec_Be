package com.rexec.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/upload")
public class FileUploadController {

    @PostMapping
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        // 定义文件保存路径
        String uploadDir = "/root/dev/java/server/data/upload/";

        // 创建目录（如果不存在）
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成文件路径
        File serverFile = new File(uploadDir + file.getOriginalFilename());
        try {
            // 保存文件到服务器
            file.transferTo(serverFile);
            return ResponseEntity.status(HttpStatus.OK).body("文件上传成功: " + serverFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件上传失败: " + e.getMessage());
        }
    }
}
