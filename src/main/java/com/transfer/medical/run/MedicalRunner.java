package com.transfer.medical.run;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.transfer.medical.MedicalServiceApp;
import com.transfer.medical.util.MedicalAppPropertiesUtil;
import com.transfer.medical.util.MedicalStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 高可用启动执行器。
 *
 * 基于 ZooKeeper 选主，只有主节点才会启动应用；与 ZK 失去连接时主动退出，
 * 避免出现多个主节点同时运行。
 */
@Slf4j
public class MedicalRunner {

    private static final CuratorFramework CURATOR = buildCurator();

    public static void main(String[] args) throws InterruptedException {
        if (haEnabled()) {
            runAsLeader(args);
        } else {
            MedicalServiceApp.main(args);
        }
    }

    private static boolean haEnabled() {
        return Objects.equals(MedicalAppPropertiesUtil.getProperty("is.need.ha", "1"), "1");
    }

    private static void runAsLeader(String[] args) {
        String leaderPath = MedicalAppPropertiesUtil.getProperty("zookeeper.path");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(leaderPath), "zk节点Leader地址不能为空");
        String nodeId = MedicalStringHelper.getLocalHostAndName();
        Preconditions.checkArgument(!Strings.isNullOrEmpty(nodeId), "主机IP获取失败");

        LeaderSelector selector = new LeaderSelector(CURATOR, leaderPath, new LeaderSelectorListenerAdapter() {
            @Override
            public void takeLeadership(CuratorFramework client) throws Exception {
                log.info("==================== 主节点：{} ====================", nodeId);
                MedicalServiceApp.main(args);
                // 作为主节点常驻
                TimeUnit.DAYS.sleep(Long.MAX_VALUE);
            }

            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                if (newState == ConnectionState.SUSPENDED) {
                    log.warn("State Changed:【{}】", ConnectionState.SUSPENDED.toString());
                } else if (newState == ConnectionState.LOST) {
                    log.error("{} 与zk连接发生异常", nodeId);
                    CloseableUtils.closeQuietly(CURATOR);
                    System.exit(-1);
                }
            }
        });
        selector.autoRequeue();
        selector.start();
        try {
            TimeUnit.DAYS.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static CuratorFramework buildCurator() {
        String zkHost = MedicalAppPropertiesUtil.getProperty("zookeeper.server");
        Preconditions.checkArgument(zkHost != null, "zk连接信息不能为空");

        RetryPolicy retryPolicy = new ExponentialBackoffRetry(
                Integer.parseInt(MedicalAppPropertiesUtil.getProperty("zookeeper.baseSleepTimeMs", "1000")),
                Integer.parseInt(MedicalAppPropertiesUtil.getProperty("zookeeper.maxRetries", "3")));

        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(zkHost)
                .retryPolicy(retryPolicy)
                .sessionTimeoutMs(Integer.parseInt(
                        MedicalAppPropertiesUtil.getProperty("zookeeper.sessionTimeoutMs", "5000")))
                .connectionTimeoutMs(Integer.parseInt(
                        MedicalAppPropertiesUtil.getProperty("zookeeper.connectionTimeoutMs", "6000")))
                .namespace(MedicalAppPropertiesUtil.getProperty("zookeeper.namespace", "transfer"))
                .build();
        client.start();
        return client;
    }
}
