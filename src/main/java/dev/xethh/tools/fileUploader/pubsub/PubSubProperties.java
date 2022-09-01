package dev.xethh.tools.fileUploader.pubsub;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dev.xethh.tools.file-uploader.pubsub")
public class PubSubProperties {
    private String url;
    private String un;
    private String pwd;
    private String vpn;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUn() {
        return un;
    }

    public void setUn(String un) {
        this.un = un;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getVpn() {
        return vpn;
    }

    public void setVpn(String vpn) {
        this.vpn = vpn;
    }
}
