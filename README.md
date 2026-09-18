# 医疗数据批量交换服务 功能描述说明

## 一、功能概述

本服务是一套面向区域医疗数据中心与院内业务系统的**医疗数据批量交换（Transfer / 数据共享）中间服务**，基于 Spring Boot 构建。
它运行在医疗数据共享平台与院内业务系统之间，负责把上游医疗机构分发过来的数据文件自动接收、下载、解析、上传到 HDFS，
并在收齐后触发下游系统取数；同时支持向其他医疗机构 / 业务系统反向下发文件。

核心能力：

- **消息驱动**：通过医疗数据交换（EDB）客户端接口拉取文件通知消息，支持自动回执与手动回执。
- **文件接收入库**：按工作流编码解析文件名，落库并去重（幂等）。
- **文件下载**：支持文件路径方式与文件流方式两种下载模式。
- **文件处理**：对 gz / xml / dat / csv / verf 等格式解压、转换为 txt。
- **HDFS 上传**：将处理后的数据文件上传至医疗数据湖 Hadoop 集群（Kerberos 认证）。
- **收齐校验**：按接口 + 日期维度校验文件是否接收完整（固定数量、verf 文件、消息头文件数三种策略）。
- **文件下发**：从 HDFS 取文件回本地，再推送（下发）给其他医疗机构 / 系统。
- **失败重试与状态复位**：定时重置异常状态，保证断点可续。
- **高可用**：基于 ZooKeeper Leader 选举，多实例部署时只有主节点工作。
- **磁盘清理**：定期删除过期历史文件目录。

## 二、运行环境与技术栈

| 项目 | 说明 |
| --- | --- |
| JDK | 1.8 |
| 构建 | Maven，产物 `medical-data-transfer-1.0.0-SNAPSHOT.jar` |
| 部署目录 | `/app/app` |
| 启动脚本 | `transfer_service.sh start \| stop \| status \| restart`（另有 `transfer_service_medical.sh`） |
| 框架 | Spring Boot 2.1.6、Spring Scheduling、MyBatis |
| 存储 | PostgreSQL / MySQL（`application.properties` 中配置数据源） |
| 大数据 | Hadoop HDFS |
| 调度协调 | ZooKeeper + Curator Leader 选举 |
| 依赖库 | fastjson、gson、guava、commons-io、httpclient/httpmime、commons-vfs2、hadoop-common、nantian cpds_eftp / cpds 消息客户端 |

## 三、代码结构与模块职责

基础包：`com.transfer.medical`

| 模块 | 职责 | 主要类 |
| --- | --- | --- |
| `com.transfer.medical` | 启动入口 | `MedicalServiceApp`（Spring Boot 启动类）、`MedicalLocalCheck`（临时测试类） |
| `com.transfer.medical.run` | 高可用启动执行器 | `MedicalRunner`（Leader 选举，主节点才启动应用） |
| `com.transfer.medical.controller` | REST 接口 | `MedicalRestController` |
| `com.transfer.medical.task` | 定时任务 | `MedicalJob`（统一接口）、`MedicalReceiveMessageJob`、`MedicalDownloadFileJob`、`MedicalProcessFileJob`、`MedicalSendHdfsJob`、`MedicalCheckIsDoneJob`、`MedicalDownVerifyFileJob`、`MedicalPushJob`、`MedicalTryJob`、`MedicalCleanUpJob`、`MedicalMonitorJob`、`MedicalCheckListJob` |
| `com.transfer.medical.service` | 业务服务 | `MedicalActionService`、`MedicalRestActionService`、`MedicalHdfsActionService`、`MedicalHttpClientService`、`MedicalPutService` |
| `com.transfer.medical.mapper` | MyBatis Mapper 接口 | `MedicalDataShareMessageMapper`、`MedicalSendFileInformationMapper`、`MedicalVerifyConfigMapper`、`MedicalVerifyContentMapper` |
| `com.transfer.medical.pojo` | 数据对象 | `MedicalDataShareMessage`、`MedicalDataVerifyMessage`、`MedicalDownloadFileRequest`、`MedicalDownloadFileResponse`、`MedicalSendFileInformation`、`MedicalVerifyConfig`、`MedicalVerifyContent` |
| `com.transfer.medical.enums` | 枚举 | `MedicalDataFileCategory`、`MedicalDoneFlag` |
| `com.transfer.medical.util` | 工具类 | `MedicalAppPropertiesUtil`、`MedicalStringHelper`、`MedicalFileTools`、`MedicalFileEncodingDetector`、`MedicalDateTools`、`MedicalCryptoUtils`、`MedicalXmlTools`、`MedicalHdfsFileClient`、`MedicalConnection`、`MedicalHttpAction`、`MedicalSftpTools`、`MedicalScpFileTransfer`、`MedicalStreamGobbler`、`MedicalConstants`、`MedicalFlowStatus`、`MedicalApiResponse`、`MedicalDataShareMessageGenerator` |

## 四、核心业务处理流程

整体数据流：

```
上游医疗机构 / EDB客户端
   │  (消息)
   ▼
MedicalReceiveMessageJob ──► pm_data_share_msg 入库（幂等 + 回执）
   │
   ▼
MedicalDownloadFileJob ──► 下载文件到本地下载目录
   │
   ▼
MedicalProcessFileJob ──► 解压/转 txt
   │
   ▼
MedicalSendHdfsJob ──► 上传 HDFS
   │
   ▼
MedicalCheckIsDoneJob ──► 判断文件是否收齐（is_done）
   │
   ▼
下游系统取用 / MedicalPushJob 反向下发
```

### 1. 接收消息 `MedicalReceiveMessageJob`

- 定时（默认 5 秒）拉取消息队列中的文件通知。
- 根据 `is.autoReceiveMessage` 选择自动回执接口或手动回执接口。
- 从消息中解析文件名、发送方系统号、文件个数、消息时间等；文件名按工作流编码规则解析
  （`MedicalStringHelper.getWorkflowCode`），得到接口名、行政区划编码、日期等。
- 对部分文件名不含发送方系统码的情况，通过 `interface.filename.syscode.map` 配置映射。
- 文件类型划分：`.check` → `MedicalDataFileCategory.CHECK`、`.verf` → `MedicalDataFileCategory.VERF`、
  其他 → `MedicalDataFileCategory.DATA`；check 文件只回执不入库。
- **幂等处理**：对 `File_id` 去重，避免 EDB 客户端因重平衡重复投递造成重复入库。
- 处理成功后写入 `pm_data_share_msg`，并调用 `ackMessage` 回执。

### 2. 下载文件 `MedicalDownloadFileJob` / `MedicalRestActionService.download`

- 查询待下载记录（`selectPendingDownload`），先批量将下载状态置为“进行中(02)”，防止重复下载。
- 组装 `MedicalDownloadFileRequest`，携带服务名、发送/接收系统号、系统密钥、接收目录、下载方式等。
- 调用医疗数据交换客户端下载接口：
  - `receiveFile.stream.status=0`：按文件路径方式下载；
  - `=1`：按文件流方式下载（`MedicalHttpClientService` 中的流式保存）。
- 结果回写 `downFlag`（成功 `00` / 失败 `03` / 异常待重试 `01`）。

### 3. 处理文件 `MedicalProcessFileJob` / `MedicalActionService.process`

- 查询待处理记录（`selectPendingProcess`），将处理状态置为“进行中”。
- 按扩展名转换：
  - `gz`：`MedicalFileTools.decompress` 解压后按 xml 逻辑转 txt；
  - `xml`：`MedicalFileTools.xml2txt`；医保结算系统走 `xml2txtForFinance`；特殊文件走 `xmlSpectxt`（只取指定字段）；
  - `dat`：`MedicalFileTools.dat2txt`（渠道二期格式）；
  - `csv`：`MedicalFileTools.csv2txt`；
  - `verf`：逐行解析校验文件内容，写入 `verf_content` 表。
- 字符集由 `MedicalFileEncodingDetector` 探测，并可用 `interface.filepath.filecode.map` 手动指定。
- 记录生成文件的字节大小，更新 `processFlag` 与日志。

### 4. 上传 HDFS `MedicalSendHdfsJob` / `MedicalActionService.sendHdfs`

- 查询待上传记录（`selectPendingHdfs`），置为上传中。
- 通过 `MedicalHdfsFileClient`（Kerberos 认证，NameNode HA）上传；目标文件统一改为 `.txt`。
- 空文件（`isDone=4`）无需上传。
- 回写 `sendHdfsFlag` 与 `sendHdfsPath`。

### 5. 收齐校验 `MedicalCheckIsDoneJob`

按 `unit_name + tx_
