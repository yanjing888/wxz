package com.wuxiaozhi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 部署环境配置；工位/课次/入座记录仍存数据库。 */
@Data @Configuration @ConfigurationProperties(prefix="wuxiaozhi.lab")
public class LabDeploymentProperties {
    private boolean bootstrapEnabled=false;
    private String bootstrapRoomName="摄像头联调实验室";
    private String bootstrapStationCode="01";
    private String cameraHost="";
    private String cameraDirectHost="";
    private String initialStreamUrl="";
    /** 同型号设备的 IP 播放规则，例如 ws://{host}/设备的实际FLV路径。 */
    private String ipStreamTemplate="";
    private String initialSnapshotUrl="";
    private String cameraUsername="";
    private String cameraPassword="";
}
