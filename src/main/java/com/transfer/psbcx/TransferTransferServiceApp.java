package com.transfer.psbcx;

import com.nantian.edb.cpdmessage.CpdsMsgException;
import com.nantian.edb.cpdmessage.MsgReceiver;
import com.transfer.psbcx.mapper.TransferDataShareMessageMapper;
import com.transfer.psbcx.service.TransferTransferHttpClientService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 批量文件交换服务启动类。
 *
 * 启动完成后会复位异常中间状态，保证主备切换后任务可继续执行。
 */
@SpringBootApplication
@MapperScan("com.transfer.psbcx.mapper")
@EnableAsync
@PropertySource(value = {"classpath:config.properties"})
public class TransferTransferServiceApp extends SpringBootServletInitializer implements ApplicationRunner {

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${edbClient.urls}")
    private String edbClientUrls;

    @Autowired
    private TransferDataShareMessageMapper shareMsgMapper;

    @Lazy
    @Bean(name = "msgReceiver")
    public MsgReceiver getMsgReceiver() throws CpdsMsgException {
        return new MsgReceiver("server1", "dest1");
    }

    @Bean(name = "edbHttpClientService")
    public TransferTransferHttpClientService getHttpClient() {
        return new TransferTransferHttpClientService(edbClientUrls);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(this.getClass());
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.print("password=========" + datasourcePassword);
        // 主节点切换后必须复位状态
        shareMsgMapper.resetAllDownFlag();
        shareMsgMapper.resetVerifyDownFlag();
    }

    public static void main(String[] args) {
        SpringApplication.run(TransferTransferServiceApp.class, args);
    }
}
