package com.wuxiaozhi.service;

import com.sun.net.httpserver.HttpServer;
import com.wuxiaozhi.config.LabDeploymentProperties;
import com.wuxiaozhi.entity.LabWorkstation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationVideoServiceTest {
    final WorkstationService service=mock(WorkstationService.class);
    final LabDeploymentProperties properties=new LabDeploymentProperties();
    final WorkstationVideoService video=new WorkstationVideoService(service,properties);
    LabWorkstation station(String url){var s=new LabWorkstation();s.setId(1L);s.setRoomId(2L);s.setStreamUrl(url);when(service.station(1L)).thenReturn(s);return s;}
    @Test void resolvesLegacyAddressOnServerAndRejectsRtspAndEmbeddedPasswords(){
        properties.setCameraHost("192.168.1.9:80");properties.setCameraDirectHost("192.168.2.9");
        assertThat(video.resolve("/ws/live.flv").toString()).isEqualTo("ws://192.168.1.9:80/ws/live.flv");
        assertThat(video.resolve("/ws-direct/live.flv").toString()).isEqualTo("ws://192.168.2.9/ws/live.flv");
        assertThatThrownBy(()->video.resolve("rtsp://192.168.1.9/live")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->video.resolve("http://admin:password@192.168.1.9/live.flv")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void httpFlvBytesAreForwardedAndPermissionsRechecked() throws Exception {
        byte[] bytes={'F','L','V',1,5,0,0,0,9};
        var upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        upstream.createContext("/live.flv",exchange->{exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});upstream.start();
        try {
            station("http://127.0.0.1:"+upstream.getAddress().getPort()+"/live.flv");
            var out=new ByteArrayOutputStream();video.stream(7L,1L).writeTo(out);
            assertThat(out.toByteArray()).isEqualTo(bytes);verify(service,atLeast(2)).cameraAccess(eq(7L),any(),isNull());
            doThrow(WorkstationService.fail(HttpStatus.FORBIDDEN,"课次结束")).when(service).cameraAccess(eq(7L),any(),isNull());
            assertThatThrownBy(()->video.stream(7L,1L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }finally{upstream.stop(0);}
    }
    @Test void websocketBinaryFlvIsConvertedToHttpBody() throws Exception {
        byte[] payload={'F','L','V',1,5,0,0,0,9};
        try(var listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var executor=Executors.newSingleThreadExecutor();
            try {
                var server=executor.submit(()->{
                    try(var socket=listener.accept()) {
                        socket.setSoTimeout(5000);
                        var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String line,key=null;
                        while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:"))key=line.substring(line.indexOf(':')+1).trim();
                        String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                        var output=socket.getOutputStream();output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        output.write(new byte[]{(byte)0x82,(byte)payload.length});output.write(payload);output.write(new byte[]{(byte)0x88,0});output.flush();
                    }catch(Exception e){throw new RuntimeException(e);}
                });
                station("ws://127.0.0.1:"+listener.getLocalPort()+"/ws/live.flv");var out=new ByteArrayOutputStream();
                // A camera can close immediately after its final frame. Forward that frame before terminating.
                try{video.stream(7L,1L).writeTo(out);}catch(IOException expected){}
                assertThat(out.toByteArray()).isEqualTo(payload);server.get(8,TimeUnit.SECONDS);
            }finally{executor.shutdownNow();}
        }
    }
}
