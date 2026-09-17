# medical-data-transfer

医疗数据批量交换（Transfer / 数据共享）服务。

## 运行环境
- JDK 1.8
- Maven 构建，产物：`medical-data-transfer-1.0.0-SNAPSHOT.jar`
- Linux 部署目录：`/app/app`
- 启动脚本：`transfer_service.sh start | stop | status | restart`

## 模块说明
- `com.transfer.medical.controller`  REST 接口（列表、删除等）
- `com.transfer.medical.task`        定时任务（接收消息、下载、处理、上传、校验、清理、探测、失败重试、发送 HDFS）
- `com.transfer.medical.service`     业务服务（动作、取文件、HDFS、HTTP 动作、上传文件）
- `com.transfer.medical.mapper`      MyBatis Mapper 接口（对应 `resources/mapper/*.xml`）
- `com.transfer.medical.pojo`        数据对象
- `com.transfer.medical.enums`       枚举
- `com.transfer.medical.util`        工具类
- `com.transfer.medical.run`         启动执行器

## 配置
- `resources/application.properties`   Spring Boot 配置
- `resources/config.properties`        业务配置（MedicalAppPropertiesUtil 读取）
- `resources/cpdsmsgj2.properties`     医疗数据交换连接配置
- `resources/transfer_app_config.properties`
- `resources/logback.xml`              日志

## 说明
本仓库内容根据屏幕照片整理还原，部分文件（未在照片中完整出现）为骨架/占位，需对照原工程补全。
