package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.LabWorkstation;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.wuxiaozhi.service.WorkstationService.fail;

/** USB 采集器独立推送，内存只保留每个工位的最新一帧。 */
@Service
public class WorkstationFrames {
    public static final int MAX_BYTES=1024*1024;
    public record Frame(byte[] bytes,LocalDateTime capturedAt){}
    private final Map<Long,Frame> latest=new ConcurrentHashMap<>();
    private final Clock clock;
    public WorkstationFrames(){this(Clock.systemDefaultZone());}
    public WorkstationFrames(Clock clock){this.clock=clock;}
    public boolean fresh(Long id){var f=latest.get(id);return f!=null&&f.capturedAt().isAfter(LocalDateTime.now(clock).minusSeconds(10));}
    public void clear(Long id){if(id!=null)latest.remove(id);}
    public void publish(LabWorkstation s,String key,byte[] bytes) {
        String provided=WorkstationService.hash(key==null?"":key);
        if(!s.isEnabled()||!"USB".equals(s.getCameraType())||s.getPublisherHash()==null||!MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),s.getPublisherHash().getBytes(StandardCharsets.UTF_8)))
            throw fail(HttpStatus.FORBIDDEN,"采集密钥无效");
        validateImage(bytes);latest.put(s.getId(),new Frame(bytes.clone(),LocalDateTime.now(clock)));
    }
    public Frame capture(LabWorkstation s) {
        if(!s.isEnabled())throw fail(HttpStatus.CONFLICT,"工位未启用");
        if("USB".equals(s.getCameraType())) {
            var frame=latest.get(s.getId());
            if(frame==null||!frame.capturedAt().isAfter(LocalDateTime.now(clock).minusSeconds(10)))throw fail(HttpStatus.SERVICE_UNAVAILABLE,"USB 采集器未连接或画面已过期");
            return frame;
        }
        if(s.getSnapshotUrl()==null||s.getSnapshotUrl().isBlank())throw fail(HttpStatus.CONFLICT,"该摄像头未配置 JPEG 抓拍地址，无法执行巡检");
        validateSnapshotUrl(s.getSnapshotUrl());
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)URI.create(s.getSnapshotUrl()).toURL().openConnection();
            connection.setConnectTimeout(3000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);
            if(s.getCameraUsername()!=null&&!s.getCameraUsername().isBlank()) {
                String credentials=s.getCameraUsername()+":"+Objects.toString(s.getCameraPassword(),"");
                connection.setRequestProperty("Authorization","Basic "+Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            }
            if(connection.getResponseCode()!=200)throw new IOException("camera HTTP status");
            try(var input=connection.getInputStream()) {
                byte[] bytes=input.readNBytes(MAX_BYTES+1);validateImage(bytes);
                return new Frame(bytes,LocalDateTime.now(clock));
            }
        }catch(IOException e){throw fail(HttpStatus.SERVICE_UNAVAILABLE,"摄像头抓拍失败，请检查地址、设备及认证配置");}
        finally{if(connection!=null)connection.disconnect();}
    }
    public static void validateSnapshotUrl(String url) {
        try {
            URI uri=URI.create(url);
            if(!Set.of("http","https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)
                throw new IllegalArgumentException();
        }catch(IllegalArgumentException e){throw fail(HttpStatus.BAD_REQUEST,"抓拍地址应为 HTTP/HTTPS JPEG 地址，账号密码请单独填写");}
    }
    private static void validateImage(byte[] bytes) {
        if(bytes==null||bytes.length<4||bytes.length>MAX_BYTES||(bytes[0]&255)!=255||(bytes[1]&255)!=216)
            throw fail(HttpStatus.BAD_REQUEST,"请发送不超过 1MB 的 JPEG 图像");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();
            var reader=readers.next();try {
                reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>4096||h>4096||(long)w*h>8_000_000||reader.read(0)==null)throw new IOException();
            }finally{reader.dispose();}
        }catch(IOException|RuntimeException e){throw fail(HttpStatus.BAD_REQUEST,"JPEG 图像损坏或分辨率过大");}
    }
}
