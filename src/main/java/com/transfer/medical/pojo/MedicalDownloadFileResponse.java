package com.transfer.medical.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件下载响应体。
 */
@Data
@NoArgsConstructor
public class MedicalDownloadFileResponse {

    private String commonInfo;

    private String code;

    private String message;

    private String data;
}
