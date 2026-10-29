package dev.localreel.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class UrlPolicyTest {
 @Test public void acceptsSharedTextFromSixPlatforms(){for(String url:new String[]{"https://youtu.be/jNQXAC9IVRw","https://www.facebook.com/reel/123/","https://www.instagram.com/reel/Ab12/","https://vt.tiktok.com/Ab12/","https://v.douyin.com/Ab12/","https://xhslink.com/a/Ab12"})assertEquals(url,UrlPolicy.find("Chia sẻ 小红书: "+url));}
 @Test public void rejectsHostnameBypassAndCommands(){for(String url:new String[]{"https://youtube.com.evil.test/watch?v=jNQXAC9IVRw","https://127.0.0.1/a","https://169.254.169.254/a","https://user:password@youtube.com/watch?v=jNQXAC9IVRw","https://youtube.com:8080/a","http://youtu.be/jNQXAC9IVRw","--exec=whoami","https://youtube.com\\@127.0.0.1/"}){try{UrlPolicy.find(url);fail(url);}catch(IllegalArgumentException expected){}}}
 @Test public void deduplicatesYouTubeVariants(){assertEquals(UrlPolicy.identity("https://youtu.be/jNQXAC9IVRw?t=4"),UrlPolicy.identity("https://www.youtube.com/watch?v=jNQXAC9IVRw&si=abc"));}
 @Test public void deduplicatesXhsWithoutDroppingAccessToken(){String first="https://www.xiaohongshu.com/explore/abcd?xsec_token=first";assertEquals(UrlPolicy.identity(first),UrlPolicy.identity("https://xiaohongshu.com/discovery/item/abcd?xsec_token=second"));assertEquals(first,UrlPolicy.find(first));}
 @Test public void rejectsUnsafeQualityAndIdentifiers(){for(String q:new String[]{"0","-1","99999","720;cmd","../"}){try{UrlPolicy.quality(q);fail(q);}catch(IllegalArgumentException expected){}}for(String id:new String[]{"../secret","C:/file","foo","00000000-0000-0000-0000-000000000000/.."}){try{UrlPolicy.id(id);fail(id);}catch(IllegalArgumentException expected){}}}
 @Test public void rejectsOversizedShares(){try{UrlPolicy.find(new String(new char[16385]).replace('\0','a'));fail();}catch(IllegalArgumentException expected){}}
}
