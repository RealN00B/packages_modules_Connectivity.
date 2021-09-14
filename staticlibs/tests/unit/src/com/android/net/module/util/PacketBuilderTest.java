/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.net.module.util;

import static android.system.OsConstants.IPPROTO_IP;
import static android.system.OsConstants.IPPROTO_TCP;

import static com.android.net.module.util.NetworkStackConstants.ETHER_TYPE_IPV4;
import static com.android.net.module.util.NetworkStackConstants.IPV4_HEADER_MIN_LEN;
import static com.android.net.module.util.NetworkStackConstants.TCPHDR_ACK;
import static com.android.net.module.util.NetworkStackConstants.TCP_HEADER_MIN_LEN;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;
import android.net.MacAddress;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Ipv4Header;
import com.android.net.module.util.structs.TcpHeader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PacketBuilderTest {
    private static final MacAddress SRC_MAC = MacAddress.fromString("11:22:33:44:55:66");
    private static final MacAddress DST_MAC = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final Inet4Address IPV4_SRC_ADDR = addr("192.0.2.1");
    private static final Inet4Address IPV4_DST_ADDR = addr("198.51.100.1");
    private static final short SRC_PORT = 9876;
    private static final short DST_PORT = 433;
    private static final short SEQ_NO = 13579;
    private static final short ACK_NO = 24680;
    private static final byte TYPE_OF_SERVICE = 0;
    private static final short ID = 27149;
    private static final short FLAGS_AND_FRAGMENT_OFFSET = (short) 0x4000; // flags=DF, offset=0
    private static final byte TIME_TO_LIVE = (byte) 0x40;
    private static final short WINDOW = (short) 0x2000;
    private static final short URGENT_POINTER = 0;
    private static final ByteBuffer DATA = ByteBuffer.wrap(new byte[] {
            (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
    });

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x28,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x8c,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xe5, (byte) 0xe5, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x2c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x88,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x48, (byte) 0x44, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_IPV4HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x28,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x8c,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xe5, (byte) 0xe5, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_IPV4HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x2c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x88,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x48, (byte) 0x44, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    /**
     * Build a TCPv4 packet which has ether header, IPv4 header, TCP header and data.
     * The ethernet header and data are optional. Note that both source mac address and
     * destination mac address are required for ethernet header.
     *
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                Layer 2 header (EthernetHeader)                | (optional)
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                  Layer 3 header (Ipv4Header)                  |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                  Layer 4 header (TcpHeader)                   |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                          Payload                              | (optional)
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * @param srcMac source MAC address. used by L2 ether header.
     * @param dstMac destination MAC address. used by L2 ether header.
     * @param payload the payload.
     */
    @NonNull
    private ByteBuffer buildTcpv4Packet(@Nullable final MacAddress srcMac,
            @Nullable final MacAddress dstMac, @Nullable final ByteBuffer payload)
            throws Exception {
        final boolean hasEther = (srcMac != null && dstMac != null);
        final int payloadLen = (payload == null) ? 0 : payload.limit();
        final ByteBuffer buffer = PacketBuilder.allocate(hasEther, IPPROTO_IP, IPPROTO_TCP,
                payloadLen);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);

        if (hasEther) packetBuilder.writeL2Header(srcMac, dstMac, (short) ETHER_TYPE_IPV4);
        packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                TIME_TO_LIVE, (byte) IPPROTO_TCP, IPV4_SRC_ADDR, IPV4_DST_ADDR);
        packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                TCPHDR_ACK, WINDOW, URGENT_POINTER);
        if (payload != null) {
            buffer.put(payload);
            // in case data might be reused by caller, restore the position and
            // limit of bytebuffer.
            payload.clear();
        }

        return packetBuilder.finalizePacket();
    }

    private void checkEtherHeader(final ByteBuffer actual) {
        final EthernetHeader eth = Struct.parse(EthernetHeader.class, actual);
        assertEquals(SRC_MAC, eth.srcMac);
        assertEquals(DST_MAC, eth.dstMac);
        assertEquals(ETHER_TYPE_IPV4, eth.etherType);
    }

    private void checkIpv4Header(final boolean hasData, final ByteBuffer actual) {
        final Ipv4Header ipv4Header = Struct.parse(Ipv4Header.class, actual);
        assertEquals(Ipv4Header.IPHDR_VERSION_IHL, ipv4Header.vi);
        assertEquals(TYPE_OF_SERVICE, ipv4Header.tos);
        assertEquals(ID, ipv4Header.id);
        assertEquals(FLAGS_AND_FRAGMENT_OFFSET, ipv4Header.flagsAndFragmentOffset);
        assertEquals(TIME_TO_LIVE, ipv4Header.ttl);
        assertEquals(IPV4_SRC_ADDR, ipv4Header.srcIp);
        assertEquals(IPV4_DST_ADDR, ipv4Header.dstIp);

        final int dataLength = hasData ? DATA.limit() : 0;
        assertEquals(IPV4_HEADER_MIN_LEN + TCP_HEADER_MIN_LEN + dataLength,
                ipv4Header.totalLength);
        assertEquals((byte) IPPROTO_TCP, ipv4Header.protocol);
        assertEquals(hasData ? (short) 0xe488 : (short) 0xe48c, ipv4Header.checksum);
    }

    private void checkTcpv4Packet(final boolean hasEther, final boolean hasData,
            final ByteBuffer actual) {
        if (hasEther) {
            checkEtherHeader(actual);
        }
        checkIpv4Header(hasData, actual);

        final TcpHeader tcpHeader = Struct.parse(TcpHeader.class, actual);
        assertEquals(SRC_PORT, tcpHeader.srcPort);
        assertEquals(DST_PORT, tcpHeader.dstPort);
        assertEquals(SEQ_NO, tcpHeader.seq);
        assertEquals(ACK_NO, tcpHeader.ack);
        assertEquals((short) 0x5010 /* offset=5(*4bytes), control bits=ACK */,
                tcpHeader.dataOffsetAndControlBits);
        assertEquals(WINDOW, tcpHeader.window);
        assertEquals(hasData ? (short) 0x4844 : (short) 0xe5e5, tcpHeader.checksum);
        assertEquals(URGENT_POINTER, tcpHeader.urgentPointer);
    }

    @Test
    public void testBuildPacketEtherIPv4Tcp() throws Exception {
        final ByteBuffer packet = buildTcpv4Packet(SRC_MAC, DST_MAC, null /* data */);
        checkTcpv4Packet(true /* hasEther */, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv4TcpData() throws Exception {
        final ByteBuffer packet = buildTcpv4Packet(SRC_MAC, DST_MAC, DATA);
        checkTcpv4Packet(true /* hasEther */, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR_DATA,
                packet.array());
    }

    @Test
    public void testBuildPacketIPv4Tcp() throws Exception {
        final ByteBuffer packet = buildTcpv4Packet(null /* srcMac */, null /* dstMac */,
                null /* data */);
        checkTcpv4Packet(false /* hasEther */, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_TCPHDR, packet.array());
    }

    @Test
    public void testBuildPacketIPv4TcpData() throws Exception  {
        final ByteBuffer packet = buildTcpv4Packet(null /* srcMac */, null /* dstMac */, DATA);
        checkTcpv4Packet(false /* hasEther */, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_TCPHDR_DATA, packet.array());
    }

    @Test
    public void testFinalizePacketWithoutIpv4Header() throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */, IPPROTO_IP,
                IPPROTO_TCP, 0 /* payloadLen */);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);
        packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                TCPHDR_ACK, WINDOW, URGENT_POINTER);
        assertThrows("java.io.IOException: Packet is missing IPv4 header", IOException.class,
                () -> packetBuilder.finalizePacket());
    }

    @Test
    public void testFinalizePacketWithoutTcpHeader() throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */, IPPROTO_IP,
                IPPROTO_TCP, 0 /* payloadLen */);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);
        packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                TIME_TO_LIVE, (byte) IPPROTO_TCP, IPV4_SRC_ADDR, IPV4_DST_ADDR);
        assertThrows("java.io.IOException: Packet is missing TCP headers", IOException.class,
                () -> packetBuilder.finalizePacket());
    }

    @Test
    public void testWriteL2HeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeL2Header(SRC_MAC, DST_MAC, (short) ETHER_TYPE_IPV4));
    }

    @Test
    public void testWriteIpv4HeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                        TIME_TO_LIVE, (byte) IPPROTO_TCP, IPV4_SRC_ADDR, IPV4_DST_ADDR));
    }

    @Test
    public void testWriteTcpHeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                        TCPHDR_ACK, WINDOW, URGENT_POINTER));
    }

    private static Inet4Address addr(String addr) {
        return (Inet4Address) InetAddresses.parseNumericAddress(addr);
    }
}
