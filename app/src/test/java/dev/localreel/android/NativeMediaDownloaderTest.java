package dev.localreel.android;
import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class NativeMediaDownloaderTest {
    @Test public void partialResponsesNeverBecomeCompleteFiles()throws Exception {
        assertEquals(100,NativeMediaDownloader.completeBodyLength(200,null,100));
        assertEquals(100,NativeMediaDownloader.completeBodyLength(206,"bytes 0-99/100",100));
        for(String range:new String[]{null,"bytes 1-99/100","bytes 0-49/100","bytes 0-99/*","bytes 0-9999999999999999999999/100"}){
            try{NativeMediaDownloader.completeBodyLength(206,range,100);fail("Partial response accepted");}catch(NativeMediaDownloader.Failure expected){assertEquals("network",expected.code);}
        }
        try{NativeMediaDownloader.completeBodyLength(206,"bytes 0-99/100",50);fail("Length mismatch");}catch(NativeMediaDownloader.Failure expected){}
    }
    @Test public void publicTargetsOnly()throws Exception {
        for(String value:new String[]{"127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","198.18.0.1","192.0.2.1","198.51.100.1","203.0.113.1","224.0.0.1","::1","fc00::1","fe80::1","::ffff:127.0.0.1","64:ff9b::a00:1","2002:a00:1::","2001:db8::1","2001::1"})
            assertFalse(value,NativeMediaDownloader.publicAddress(InetAddress.getByName(value)));
        for(String value:new String[]{"1.1.1.1","8.8.8.8","2001:4860:4860::8888"})
            assertTrue(value,NativeMediaDownloader.publicAddress(InetAddress.getByName(value)));
    }
    @Test public void mediaRequestKeepsHttpsAndNoCredentials()throws Exception {
        assertEquals("cdn.example",NativeMediaDownloader.checkedUrl("https://cdn.example/video.mp4").host());
        for(String value:new String[]{"http://cdn.example/v.mp4","https://user:pass@cdn.example/v.mp4","https://cdn.example:8080/v.mp4","file:///etc/passwd","https://cdn.example\\@127.0.0.1/v.mp4","https://127.0.0.1/x","https://127.1/x","https://2130706433/x","https://169.254.169.254/x","https://[::1]/x","https://[::ffff:127.0.0.1]/x","https://[64:ff9b::a00:1]/x"}){
            try{NativeMediaDownloader.checkedUrl(value);fail(value);}catch(NativeMediaDownloader.Failure expected){assertEquals("unsafe_url",expected.code);}
        }
    }
}
