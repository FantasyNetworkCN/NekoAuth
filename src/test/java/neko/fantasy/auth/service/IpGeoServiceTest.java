package neko.fantasy.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpGeoServiceTest {

    @Test
    void distanceBetweenShanghaiAndMacauIsLargeEnoughForImpossibleTravel() {
        IpGeoService.IpLocation shanghai = new IpGeoService.IpLocation(
                "CN", "中国", "上海", "上海", 31.2304, 121.4737, null, "", "test");
        IpGeoService.IpLocation macau = new IpGeoService.IpLocation(
                "MO", "中国澳门", "", "澳门", 22.1987, 113.5439, null, "", "test");

        double distance = IpGeoService.distanceKm(shanghai, macau);

        assertThat(distance).isBetween(1200.0, 1500.0);
    }
}
