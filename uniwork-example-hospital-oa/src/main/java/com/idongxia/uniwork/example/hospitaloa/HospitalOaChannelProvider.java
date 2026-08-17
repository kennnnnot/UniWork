package com.idongxia.uniwork.example.hospitaloa;

import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

/** Makes the custom channel discoverable through Java ServiceLoader. */
public final class HospitalOaChannelProvider
        implements UniWorkChannelProvider<HospitalOaChannel> {

    @Override
    public String configurationPrefix() {
        return "hospital-oa";
    }

    @Override
    public Class<HospitalOaChannel> channelType() {
        return HospitalOaChannel.class;
    }

    @Override
    public HospitalOaChannel create(UniWorkConfig config) {
        return new HttpHospitalOaChannel(
                config.required("endpoint"),
                config.required("app-id"),
                config.required("secret"),
                config.get("default-title", "系统通知"),
                config.getInt("connect-timeout-millis", 3000),
                config.getInt("read-timeout-millis", 5000));
    }
}
