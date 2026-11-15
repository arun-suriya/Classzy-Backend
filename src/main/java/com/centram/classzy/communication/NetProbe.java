package com.centram.classzy.communication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.centram.classzy.communication.CommDto.TestResult;

/** Checks that a host can be reached over TCP. Private and local addresses are refused so this can't be used to probe the server's own network. */
public final class NetProbe {
    private NetProbe() {}

    public static TestResult probe(String host, int port) {
        if (host == null || host.isBlank()) return new TestResult(false, "Save the host first.");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host.trim());
            for (InetAddress a : addresses) {
                byte[] b = a.getAddress();
                boolean uniqueLocal = b.length == 16 && (b[0] & 0xFE) == 0xFC;
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress() || uniqueLocal) {
                    return new TestResult(false, "Private or local addresses cannot be tested.");
                }
            }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addresses[0], port), 5000);
            }
            return new TestResult(true, "Server reachable at " + host.trim() + ":" + port + ". Login details are not verified by this test.");
        } catch (IOException e) {
            return new TestResult(false, "Could not reach " + host.trim() + ":" + port + ".");
        }
    }
}
