package org.example.backend.module.lecture.util;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 局域网地址探测。
 *
 * <p>答辩现场走手机热点，教师机 IP 每次开会都可能变，因此入会链接与 LiveKit 信令地址都不写死，
 * 而是按这里探测出的局域网 IPv4 拼接。
 *
 * <p><b>为什么不能直接取「第一个站点本地地址」</b>：Windows 上 Docker Desktop / WSL 会造出
 * {@code vEthernet}、VMware 会造出 {@code VMnet} 等适配器，它们同样是站点本地地址，先被遍历到就会选错 ——
 * 现象是「学生点开链接打不开」或「连上信令但黑屏」。因此这里按三级规则挑选（见 {@link #pickLanAddress}），
 * 并把挑选逻辑做成纯函数以便用例锁定。
 */
@Slf4j
public final class LanAddressUtils {

    /** 一级优选：无线（热点）网卡 */
    private static final Pattern PREFERRED_ADAPTER = Pattern.compile("wlan|wi-?fi|wireless|无线", Pattern.CASE_INSENSITIVE);
    /** 二级排除：虚拟 / 隧道 / 调试类网卡 */
    private static final Pattern EXCLUDED_ADAPTER = Pattern.compile(
            "vEthernet|VMnet|VMware|Hyper-V|VirtualBox|Loopback|Npcap|TAP-|Bluetooth|虚拟", Pattern.CASE_INSENSITIVE);

    private LanAddressUtils() {
    }

    /**
     * 候选地址：一个网卡名 + 一个 IPv4。
     *
     * @param ip            地址
     * @param interfaceName 网卡显示名与系统名（用于判断是否虚拟网卡）
     * @param siteLocal     是否站点本地地址（192.168/10./172.16-31.）
     */
    public record Candidate(String ip, String interfaceName, boolean siteLocal) {
    }

    /**
     * 探测本机局域网 IPv4，失败回退 {@code 127.0.0.1}。
     */
    public static String detectLanIpv4() {
        List<Candidate> candidates = collectCandidates();
        String picked = pickLanAddress(candidates);
        if (log.isDebugEnabled()) {
            log.debug("[detectLanIpv4][候选={}, 选中={}]", candidates, picked);
        }
        return picked;
    }

    /**
     * 纯函数：三级挑选。
     * <ol>
     *     <li>无线网卡上的站点本地地址（热点/无线优先，最可能是学生可达的那张网卡）</li>
     *     <li>非虚拟网卡上的站点本地地址（排除 WSL / VMware 等）</li>
     *     <li>兜底：任意候选，都没有则 {@code 127.0.0.1}</li>
     * </ol>
     */
    static String pickLanAddress(List<Candidate> candidates) {
        Optional<String> wireless = candidates.stream()
                .filter(Candidate::siteLocal)
                .filter(candidate -> PREFERRED_ADAPTER.matcher(candidate.interfaceName()).find())
                .map(Candidate::ip)
                .findFirst();
        if (wireless.isPresent()) {
            return wireless.get();
        }

        Optional<String> physical = candidates.stream()
                .filter(Candidate::siteLocal)
                .filter(candidate -> !EXCLUDED_ADAPTER.matcher(candidate.interfaceName()).find())
                .map(Candidate::ip)
                .findFirst();
        if (physical.isPresent()) {
            return physical.get();
        }

        return candidates.stream().map(Candidate::ip).findFirst().orElse("127.0.0.1");
    }

    private static List<Candidate> collectCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                String interfaceName = networkInterface.getDisplayName() + " " + networkInterface.getName();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    // 169.254.x.x 是链路本地地址（DHCP 失败时的自分配），永远不是有效目标
                    if (ip == null || ip.startsWith("169.254.")) {
                        continue;
                    }
                    candidates.add(new Candidate(ip, interfaceName, address.isSiteLocalAddress()));
                }
            }
        } catch (SocketException e) {
            log.warn("[detectLanIpv4][枚举网卡失败，回退 127.0.0.1]", e);
        }
        return candidates;
    }

}
