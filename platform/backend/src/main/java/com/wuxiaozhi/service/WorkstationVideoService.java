package com.wuxiaozhi.service;

import com.wuxiaozhi.config.LabDeploymentProperties;
import com.wuxiaozhi.entity.LabWorkstation;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.wuxiaozhi.service.WorkstationService.fail;

/** 将设备的 HTTP/WS FLV 转为同源、鉴权的 HTTP FLV，开发及 JAR 部署共用。 */
@Service
public class WorkstationVideoService {
    private final WorkstationService service;
    private final LabDeploymentProperties config;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public WorkstationVideoService(WorkstationService service,LabDeploymentProperties config){this.service=service;this.config=config;}
    URI resolve(String address) {
        if(address==null||address.isBlank())throw fail(HttpStatus.CONFLICT,"摄像头播放地址未配置");
        String value=address;
        if(value.startsWith("/ws-direct/"))value="ws://"+config.getCameraDirectHost()+value.replaceFirst("/ws-direct/","/ws/");
        else if(value.startsWith("/ws/"))value="ws://"+config.getCameraHost()+value;
        try {
            URI uri=URI.create(value);
            if(!Set.of("http","https","ws","wss").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();
            return uri;
        }catch(IllegalArgumentException e){throw fail(HttpStatus.BAD_REQUEST,"播放地址无效，请由管理员检查视频服务地址及部署配置");}
    }
    interface Source extends AutoCloseable { byte[] read() throws Exception; void close(); }
    private Source open(URI uri) throws Exception {
        if(uri.getScheme().startsWith("http")) {
            var connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(4000);connection.setReadTimeout(6000);connection.setInstanceFollowRedirects(false);
            if(connection.getResponseCode()!=200){connection.disconnect();throw new IOException("upstream unavailable");}
            var in=connection.getInputStream();
            return new Source(){public byte[] read() throws IOException {byte[] b=new byte[65536];int n=in.read(b);return n<0?null:Arrays.copyOf(b,n);}public void close(){try{in.close();}catch(IOException ignored){}connection.disconnect();}};
        }
        var queue=new ArrayBlockingQueue<byte[]>(16);
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        var socket=client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(4)).buildAsync(uri,new WebSocket.Listener(){
            final ByteArrayOutputStream partial=new ByteArrayOutputStream();
            public void onOpen(WebSocket ws){ws.request(1);}
            public CompletionStage<?> onBinary(WebSocket ws,ByteBuffer data,boolean last){
                if(partial.size()+data.remaining()>2_000_000){closed.set(true);ws.abort();return null;}
                byte[] bytes=new byte[data.remaining()];data.get(bytes);partial.writeBytes(bytes);
                if(last){if(!queue.offer(partial.toByteArray())){closed.set(true);ws.abort();}partial.reset();}
                ws.request(1);return null;
            }
            public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){ws.request(1);return null;}
            public CompletionStage<?> onClose(WebSocket ws,int code,String reason){closed.set(true);return null;}
            public void onError(WebSocket ws,Throwable error){closed.set(true);}
        }).get(5,TimeUnit.SECONDS);
        return new Source(){public byte[] read() throws Exception {if(closed.get()&&queue.isEmpty())return null;var bytes=queue.poll(6,TimeUnit.SECONDS);if(bytes==null)throw new IOException(closed.get()?"stream closed":"stream timed out");return bytes;}public void close(){socket.abort();queue.clear();}};
    }
    public StreamingResponseBody stream(Long userId,Long id) {
        var station=service.station(id);service.cameraAccess(userId,station,null);
        if(!station.isEnabled()||!"NETWORK".equals(station.getCameraType()))throw fail(HttpStatus.CONFLICT,"该工位未启用网络摄像头");
        // 在异步处理内建立连接，异常和客户端断开都会关闭上游。
        URI uri=resolve(station.getStreamUrl());
        return output->{
            try(var source=open(uri)) {
                long checked=0;int headerIndex=0;byte[] magic={'F','L','V'};
                while(true) {
                    if(System.nanoTime()-checked>TimeUnit.SECONDS.toNanos(1)) {
                        var current=service.station(id);service.cameraAccess(userId,current,null);
                        if(!current.isEnabled()||!Objects.equals(current.getStreamUrl(),station.getStreamUrl())||!Objects.equals(current.getCameraType(),station.getCameraType()))break;
                        checked=System.nanoTime();
                    }
                    byte[] bytes=source.read();if(bytes==null)break;
                    for(int i=0;i<bytes.length&&headerIndex<3;i++,headerIndex++)if(bytes[i]!=magic[headerIndex])throw new IOException("upstream is not FLV");
                    output.write(bytes);output.flush();
                }
            }catch(Exception e){throw new IOException("摄像头连接失败或已中断，请检查设备、播放地址及网络");}
        };
    }
}
