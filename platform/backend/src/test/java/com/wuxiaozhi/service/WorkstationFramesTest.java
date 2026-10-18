package com.wuxiaozhi.service;
import com.wuxiaozhi.entity.LabWorkstation;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class WorkstationFramesTest {
    @Test void expiredFramesNeverAppearAsLiveAndTwoStationsAreIndependent() throws Exception {
        class TestClock extends Clock {
            Instant now=Instant.parse("2026-10-16T00:00:00Z");
            public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;}
        }
        var clock=new TestClock();var frames=new WorkstationFrames(clock);var station=new LabWorkstation();station.setId(1L);station.setCameraType("USB");station.setPublisherHash(WorkstationService.hash("test-key"));
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"jpg",output);
        frames.publish(station,"test-key",output.toByteArray());assertThat(frames.fresh(1L)).isTrue();assertThat(frames.fresh(2L)).isFalse();
        clock.now=clock.now.plusSeconds(10);assertThat(frames.fresh(1L)).isFalse();
        assertThatThrownBy(()->frames.capture(station)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("过期");
    }
}
