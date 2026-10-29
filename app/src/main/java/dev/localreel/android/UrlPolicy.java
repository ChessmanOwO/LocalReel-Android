package dev.localreel.android;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class UrlPolicy {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
        "facebook.com","www.facebook.com","m.facebook.com","web.facebook.com","fb.watch",
        "instagram.com","www.instagram.com","m.instagram.com",
        "tiktok.com","www.tiktok.com","m.tiktok.com","vm.tiktok.com","vt.tiktok.com",
        "douyin.com","www.douyin.com","v.douyin.com","www.iesdouyin.com",
        "xiaohongshu.com","www.xiaohongshu.com","xhslink.com","www.xhslink.com",
        "youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","youtu.be","www.youtube-nocookie.com"));
    public static String find(String text) {
        if(text==null || text.getBytes(StandardCharsets.UTF_8).length>16384) throw new IllegalArgumentException();
        Matcher m=Pattern.compile("https://[^\\s<>\"\u200b]+",Pattern.CASE_INSENSITIVE).matcher(text);
        while(m.find()) {
            String value=m.group().replaceAll("[，。；、）)]+$", "");
            try { validate(value); return value; } catch(IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException();
    }
    public static void validate(String value) {
        try {
            URI u=new URI(value); String h=u.getHost();
            if(value.length()>4096 || value.contains("\\") || value.chars().anyMatch(c->c<=32||c==127)
                || !"https".equalsIgnoreCase(u.getScheme()) || h==null || u.getRawUserInfo()!=null
                || !HOSTS.contains(h.toLowerCase(Locale.ROOT)) || (u.getPort()!=-1&&u.getPort()!=443))
                throw new IllegalArgumentException();
        } catch(java.net.URISyntaxException e) { throw new IllegalArgumentException(); }
    }
    public static String platform(String url) {
        String h=URI.create(url).getHost().toLowerCase(Locale.ROOT);
        if(h.contains("youtu"))return "YouTube";
        if(h.contains("facebook")||h.equals("fb.watch"))return "Facebook";
        if(h.contains("instagram"))return "Instagram";
        if(h.contains("tiktok"))return "TikTok";
        if(h.contains("douyin"))return "Douyin";
        return "Xiaohongshu";
    }
    public static String identity(String url) {
        URI u=URI.create(url);String p=platform(url),path=u.getPath().replaceAll("/+$", "");
        if(p.equals("Xiaohongshu")&&u.getHost().contains("xiaohongshu"))return p+":"+path.substring(path.lastIndexOf('/')+1);
        if(p.equals("YouTube")) {
            Matcher m=Pattern.compile("(?:[?&]v=|youtu\\.be/|/(?:shorts|embed|live)/)([A-Za-z0-9_-]{11})(?:[?&#/]|$)").matcher(url);
            if(m.find())return p+":"+m.group(1);
        }
        if(p.equals("TikTok")&&path.matches(".*?/video/[0-9]+"))return p+":"+path.substring(path.lastIndexOf('/')+1);
        return p+":"+u.getHost().replaceFirst("^(www|m|web)\\.","")+path+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
    }
    public static String id(String value) {
        if(value==null || !value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IllegalArgumentException();
        return value;
    }
    public static String quality(String value) {
        if(value!=null && (value.equals("best") || value.equals("ask") || value.matches("[1-9][0-9]{1,3}")))return value;
        throw new IllegalArgumentException();
    }
}
