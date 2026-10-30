package dev.localreel.android;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Private worker IPC -> Android TLS -> current job's fixed file. Never logs URLs. */
final class NativeMediaDownloader {
    static final class Failure extends IOException {
        final String code;
        Failure(String code){super(code);this.code=code;}
    }
    interface Progress { void update(long received,long total); }
    private volatile Call active;
    private volatile boolean cancelled;
    private final OkHttpClient client=new OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .dns(host->{List<InetAddress> addresses=Dns.SYSTEM.lookup(host);
            if(addresses.isEmpty())throw new UnknownHostException("unsafe_url");
            for(InetAddress ip:addresses)if(!publicAddress(ip))throw new UnknownHostException("unsafe_url");
            return addresses;})
        .connectTimeout(20,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true).build();

    static boolean publicAddress(InetAddress ip){
        if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())return false;
        byte[] raw=ip.getAddress();
        if(raw.length==4){int a=raw[0]&255,b=raw[1]&255,c=raw[2]&255;
            return !(a==0||a==10||a==127||a>=224||(a==100&&b>=64&&b<=127)||(a==169&&b==254)
                ||(a==172&&b>=16&&b<=31)||(a==192&&(b==168||b==0||b==2))
                ||(a==198&&(b==18||b==19||(b==51&&c==100)))||(a==203&&b==0&&c==113));}
        if(raw.length!=16)return false;
        // Only native global-unicast IPv6. Excludes local/mapped/NAT64/6to4/Teredo.
        if((raw[0]&0xe0)!=0x20)return false;
        int b=raw[1]&255,c=raw[2]&255,d=raw[3]&255;
        return !(b==2||(b==1&&((c==0&&d==0)||(c==0x0d&&d==0xb8)||c==0)));
    }
    static HttpUrl checkedUrl(String value)throws Failure {
        HttpUrl url=HttpUrl.parse(value);
        if(url==null||!url.isHttps()||url.port()!=443||!url.username().isEmpty()||!url.password().isEmpty()||value.contains("\\"))throw new Failure("unsafe_url");
        // OkHttp bypasses custom Dns for IP literals, including redirected URLs.
        // Resolve only syntactic literals here; hostnames go through checked Dns.
        String host=url.host();
        if(host.contains(":")||host.matches("[0-9.]+")){
            try{if(!publicAddress(InetAddress.getByName(host)))throw new Failure("unsafe_url");}
            catch(UnknownHostException error){throw new Failure("unsafe_url");}
        }
        return url;
    }
    void cancel(){cancelled=true;Call call=active;if(call!=null)call.cancel();}
    void close(){cancel();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    void begin(){cancelled=false;}
    private void check()throws Failure {if(cancelled)throw new Failure("interrupted");}

    static long completeBodyLength(int status,String range,long length)throws Failure {
        if(status==200)return length;
        if(status==206&&range!=null){
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("bytes 0-([0-9]+)/([0-9]+)").matcher(range);
            if(match.matches())try{
                long end=Long.parseLong(match.group(1)),total=Long.parseLong(match.group(2));
                if(total>0&&end==total-1&&(length<0||length==total))return total;
            }catch(NumberFormatException ignored){}
        }
        throw new Failure("network");
    }

    void download(JSONObject event,File directory,long cap,Progress progress)throws Exception {
        check();int slot=event.getInt("slot");if(slot<0||slot>1)throw new Failure("unsafe_url");
        HttpUrl url=checkedUrl(event.getString("url"));
        File target=new File(directory,"stream-"+slot+".bin"),part=new File(directory,"stream-"+slot+".native-part");
        JSONObject headers=event.optJSONObject("headers");
        try {
            for(int redirects=0;redirects<=5;redirects++){
                check();Request.Builder request=new Request.Builder().url(url).get().header("Accept-Encoding","identity");
                if(headers!=null)for(String name:new String[]{"User-Agent","Referer","Accept","Accept-Language","Origin"}){
                    String value=headers.optString(name,"");if(!value.isEmpty()&&!value.contains("\r")&&!value.contains("\n"))request.header(name,value);
                }
                Call call=client.newCall(request.build());active=call;check();
                try(Response response=call.execute()){
                    check();int status=response.code();
                    if(Arrays.asList(301,302,303,307,308).contains(status)){
                        String location=response.header("Location");HttpUrl next=location==null?null:url.resolve(location);
                        if(next==null||redirects==5)throw new Failure("network");url=checkedUrl(next.toString());continue;
                    }
                    if(status==401)throw new Failure("login_required");
                    if(status==403||status==429)throw new Failure("blocked");
                    if(status==404||status==410)throw new Failure("unavailable");
                    if(!response.isSuccessful()||response.body()==null)throw new Failure("network");
                    long expected=completeBodyLength(status,response.header("Content-Range"),response.body().contentLength());if(expected>cap)throw new Failure("limit");
                    long received=0,last=0;
                    try(InputStream in=response.body().byteStream();OutputStream out=new FileOutputStream(part)){
                        byte[] buffer=new byte[65536];int n;
                        while((n=in.read(buffer))!=-1){check();received+=n;
                            if(received>cap||directory.getUsableSpace()<32L*1024*1024)throw new Failure("limit");
                            out.write(buffer,0,n);long now=android.os.SystemClock.elapsedRealtime();
                            if(now-last>=400){progress.update(received,expected);last=now;}
                        }
                    }
                    check();if(received==0||(expected>=0&&received!=expected))throw new Failure("network");
                    if(target.exists()&&!target.delete())throw new IOException();
                    if(!part.renameTo(target))throw new IOException();progress.update(received,expected);return;
                }finally{active=null;}
            }
        }catch(Failure error){throw error;}
        catch(javax.net.ssl.SSLPeerUnverifiedException|javax.net.ssl.SSLHandshakeException error){throw new Failure("tls_verification_failed");}
        catch(UnknownHostException error){throw new Failure("unsafe_url".equals(error.getMessage())?"unsafe_url":"network");}
        catch(IOException error){throw new Failure(cancelled?"interrupted":"network");}
        finally{if(part.exists())part.delete();}
    }
}
