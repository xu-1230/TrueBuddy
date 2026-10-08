package org.example.backend.module.lecture.util;

import org.example.backend.module.lecture.util.LanAddressUtils.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 局域网地址挑选规则的回归锁定。
 *
 * <p>真实动机：开发机上有 WSL（{@code vEthernet}）与 VMware（{@code VMnet}）适配器，它们的地址同样是
 * 「站点本地地址」。如果按「第一个站点本地地址」取，入会链接与 LiveKit 信令地址就会指向一张学生根本
 * 到不了的网卡 —— 现象是「链接打不开」或「连上信令但黑屏」，而排查成本极高。
 */
class LanAddressUtilsTest {

    private static Candidate candidate(String ip, String interfaceName, boolean siteLocal) {
        return new Candidate(ip, interfaceName, siteLocal);
    }

    @Test
    @DisplayName("有无线网卡时优选它（热点场景就是这张）")
    void prefersWireless() {
        List<Candidate> candidates = List.of(
                candidate("192.168.153.1", "VMware Virtual Ethernet Adapter for VMnet8 eth3", true),
                candidate("172.24.80.1", "vEthernet (WSL (Hyper-V firewall)) eth4", true),
                candidate("172.16.209.195", "Intel(R) Wi-Fi 6 AX201 160MHz WLAN", true));
        assertEquals("172.16.209.195", LanAddressUtils.pickLanAddress(candidates));
    }

    @Test
    @DisplayName("没有无线网卡时，排除虚拟网卡后再取")
    void excludesVirtualAdapters() {
        List<Candidate> candidates = List.of(
                candidate("172.24.80.1", "vEthernet (WSL (Hyper-V firewall)) eth4", true),
                candidate("10.0.0.5", "Realtek PCIe GbE Family Controller 以太网", true));
        assertEquals("10.0.0.5", LanAddressUtils.pickLanAddress(candidates));
    }

    @Test
    @DisplayName("只有虚拟网卡时兜底使用（总比回环地址好）")
    void fallsBackWhenOnlyVirtual() {
        List<Candidate> candidates = List.of(candidate("172.24.80.1", "vEthernet (WSL) eth4", true));
        assertEquals("172.24.80.1", LanAddressUtils.pickLanAddress(candidates));
    }

    @Test
    @DisplayName("非站点本地地址不参与前两级（例如运营商级 NAT 的 100.64.x.x）")
    void ignoresNonSiteLocal() {
        List<Candidate> candidates = List.of(
                candidate("100.64.0.5", "Intel(R) Wi-Fi 6 AX201 WLAN", false),
                candidate("10.0.0.5", "Realtek PCIe GbE 以太网", true));
        assertEquals("10.0.0.5", LanAddressUtils.pickLanAddress(candidates));
    }

    @Test
    @DisplayName("没有任何候选时回退回环地址")
    void fallsBackToLoopback() {
        assertEquals("127.0.0.1", LanAddressUtils.pickLanAddress(List.of()));
    }

    @Test
    @DisplayName("真实探测：返回值必须是 IPv4 字面量（环境相关断言只到这一步）")
    void detectReturnsIpv4Literal() {
        assertEquals(true, LanAddressUtils.detectLanIpv4().matches("^\\d{1,3}(\\.\\d{1,3}){3}$"));
    }

}
