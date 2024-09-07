# UDP

link: https://datatracker.ietf.org/doc/html/rfc768

In Linux, the UDP socket is represented by the struct sock data structure, which is part of the net/inet_sock.h header file.
giveme details about the C implementation of this in the OS

Sure, let's dive into the C implementation of UDP in the Linux kernel. We'll focus on the key data structures and functions involved in the UDP socket implementation.

UDP Socket Data Structure:

The main data structure representing a UDP socket in the Linux kernel is struct udp_sock, which is defined in the include/net/udp.h header file.
struct udp_sock is an extension of the generic struct sock and contains additional fields specific to UDP, such as the receive queue, checksum settings, and other protocol-specific information.
c

Copy Code

struct udp_sock {
    /* Generic socket data */
    struct sock sk;

    /* UDP-specific data */
    unsigned int    rcv_saddr;
    unsigned int    uaddr0, uaddr1, uaddr2, uaddr3;
    struct sk_buff_head    receive_queue;
    /* ... other UDP-specific fields ... */
};
UDP Socket Creation:

When an application calls the socket() syscall with the SOCK_DGRAM type, the kernel's inet_create() function is responsible for creating the UDP socket.
This function allocates a new struct sock instance and initializes the necessary fields, including creating the struct udp_sock extension.
c

Copy Code
int inet_create(struct net *net, struct socket *sock, int protocol, int kern)
{
    /* ... */
    if (protocol == IPPROTO_UDP) {
        sk->sk_prot = &udp_prot;
        sk->sk_family = PF_INET;
        /* Initialize UDP-specific fields in udp_sock */
        udp_sk(sk)->rcv_saddr = 0;
        /* ... */
    }
    /* ... */
}
UDP Packet Transmission:
When an application calls the sendto() syscall, the kernel's udp_sendmsg() function is responsible for handling the UDP packet transmission.
This function retrieves the relevant information from the struct udp_sock, constructs the UDP header, and passes the packet to the lower-level network layers for further processing and transmission.
c

Copy Code
int udp_sendmsg(struct sock *sk, struct msghdr *msg, size_t len)
{

    struct udp_sock *up = udp_sk(sk);
    /* ... */
    err = ip_send_skb(sk, skb);
    /* ... */
}

UDP Packet Reception:
When a UDP packet arrives at the network interface, the kernel's udp_rcv() function is responsible for processing the packet and delivering it to the appropriate socket.
This function identifies the destination socket based on the source and destination addresses and ports, and then adds the packet to the receive queue of the struct udp_sock.
c


Copy Code
int udp_rcv(struct sk_buff *skb)
{
    struct udp_sock *up;
    /* ... */
    up = udp_sk(sk);
    skb_queue_tail(&up->receive_queue, skb);
    /* ... */
}

Socket Lifecycle:
The lifecycle of a UDP socket is managed by the kernel's generic socket management functions, such as inet_create(), inet_release(), and inet_destroy().
When an application calls the close() syscall, the kernel's inet_release() function is responsible for releasing the resources associated with the UDP socket, including the struct udp_sock instance.
The implementation of UDP in the Linux kernel is designed to be efficient and lightweight, taking advantage of the connectionless nature of the protocol. The kernel handles the low-level details of UDP, providing a simple and easy-to-use interface for applications to send and receive UDP packets.
When a user-space application sends a UDP datagram using the sendto() system call, the following steps occur in the Linux kernel:

The socket layer receives the system call and passes the request to the UDP protocol implementation.
The UDP protocol layer prepares the UDP header and passes the datagram to the network layer.
The network layer adds the IP header and performs any necessary fragmentation.
The link layer frames the IP packet and sends it out through the appropriate network interface.
Similarly, when a UDP datagram is received by the network interface, the process is reversed:

The link layer receives the frame and passes the IP packet to the network layer.
The network layer performs IP reassembly (if necessary) and passes the UDP datagram to the UDP protocol layer.
The UDP protocol layer processes the UDP header, performs checksum verification, and passes the data to the socket layer.
The socket layer delivers the received data to the user-space application through the recvfrom() system call.

---

latency?
 multiple routers, gateways,?
what would be partial checksum coverage of the UDP payload on udp-lite? 

---
# UDP-lite

link: https://datatracker.ietf.org/doc/html/rfc3828

UDP-Lite (also known as Lightweight User Datagram Protocol or CUDP) is a variant of the standard UDP (User Datagram Protocol) that allows for partial checksum coverage of the UDP payload. It was introduced as an extension to the standard UDP protocol to address the needs of certain applications, particularly in the context of real-time multimedia streaming and wireless communications.

------

Transport Layer: The transport layer is responsible for handling the end-to-end communication between the client and the server. For UDP, this layer is responsible for tasks such as:
Maintaining the UDP socket state (e.g., source and destination ports, IP addresses)
Handling the UDP header (source port, destination port, length, checksum)

# ICP

You're right! Remote Procedure Call (RPC) is indeed another important interprocess communication mechanism. Here’s an updated list that includes RPC:

Pipes:
Anonymous Pipes: Used for communication between related processes (e.g., parent and child).
Named Pipes (FIFOs): Allow communication between unrelated processes via a filesystem path.
Shared Memory:
Enables multiple processes to access the same memory segment for fast data exchange.

Sockets:
Used for communication between processes, either on the same machine or over a network. This includes:
Stream Sockets (TCP): For reliable, connection-oriented communication.
Datagram Sockets (UDP): For connectionless communication.
Signals:
A limited form of IPC that allows processes to send notifications to each other about events.
Memory-Mapped Files:
Allow processes to map files or devices into memory for shared access.
DBus:
A message bus system for communication between applications, commonly used in desktop environments.
Unix Domain Sockets:
Similar to regular sockets but optimized for communication between processes on the same host.
Eventfd:
A file descriptor for event notification between processes, often used in event-driven programming.
Signalfd:
Allows processes to receive signals as file descriptors, enabling the use of standard I/O multiplexing techniques.
Remote Procedure Call (RPC):
A protocol that allows a program to execute a procedure (subroutine) in another address space (commonly on another computer in a shared network) as if it were a local call. RPC abstracts the communication details, allowing developers to focus on the procedure logic.
These IPC mechanisms, including RPC, provide various ways for processes to communicate and synchronize in a Linux environment, catering to different needs and scenarios.

---
The socket() system call in C is used to create a new socket, which is an endpoint for communication in a network. The socket() function takes three arguments:

__domain (domain):

The domain, or address family, specifies the protocol family to be used for the socket.
Common domain values include:
AF_INET (IPv4 Internet protocols)
AF_INET6 (IPv6 Internet protocols)
AF_UNIX (local communication)
AF_BLUETOOTH (Bluetooth protocols)
The domain determines the format and structure of the addresses that will be used with the socket.
__type (type):
The type specifies the communication semantics of the socket.

Common socket types include:
SOCK_STREAM (reliable, connection-oriented, e.g., TCP)
SOCK_DGRAM (unreliable, connectionless, e.g., UDP)
SOCK_RAW (raw protocol access)
The type determines the behavior and characteristics of the socket, such as whether it is connection-oriented or connectionless, and how data is delivered.

__protocol (protocol):
The protocol specifies the specific protocol to be used with the socket.
The available protocols depend on the domain and type specified.
For example, in the AF_INET domain, the common protocols are IPPROTO_TCP (for TCP) and IPPROTO_UDP (for UDP).
The protocol argument allows you to select a specific protocol implementation within a given domain and type.
The extern keyword in the function declaration is used to indicate that the socket() function is defined elsewhere, typically in a system library or the C standard library. This allows the compiler to recognize the function and generate the appropriate code to call it, even though the actual implementation is not present in the current source file.

The purpose of having these three arguments in the socket() function is to provide a flexible and extensible way to create network sockets. The domain, type, and protocol allow the application to specify the exact communication requirements, which can vary depending on the network protocol, the type of communication (e.g., reliable vs. unreliable), and the specific protocol implementation to be used.

This flexibility allows the socket() function to be used for a wide range of network programming tasks, from simple UDP communication to complex, connection-oriented TCP applications, and even low-level raw protocol access.

RFC 791 - Internet Protocol (IP):
Link: https://tools.ietf.org/html/rfc791
RFC 1191 - Path MTU Discovery:
Link: https://tools.ietf.org/html/rfc1191
RFC 1122 - Requirements for Internet Hosts - Communication Layers:
Link: https://tools.ietf.org/html/rfc1122

![shmget](assets/mmap%20and%20shget.png)
![comparison](assets/comparison.png)

---

## TCP Header:

The TCP header is more complex than the UDP header because it includes fields for managing the connection and ensuring reliable delivery. Key fields in the TCP header include:
     - Source Port: The port number of the sender.
     - Destination Port: The port number of the receiver.
     - Sequence Number: Used to ensure that packets are delivered in order.
     - Acknowledgment Number: Indicates the next expected byte from the sender.
     - Flags: Control flags (e.g., SYN, ACK, FIN) that manage the connection state.
     - Window Size: Used for flow control.
     - Checksum: For error-checking.

## UDP Header:

The UDP header is simpler and consists of only four fields:
    - Source Port: The port number of the sender (optional).
    - Destination Port: The port number of the receiver.
    - Length: The length of the UDP header and the data.
    - Checksum: For error-checking (optional in IPv4, mandatory in IPv6).

# TCP 
In the context of the Transmission Control Protocol (TCP), segments are the fundamental units of data that are transmitted over a TCP connection. Here’s how segments relate to TCP:

1. Definition of a TCP Segment
A TCP segment is a packet of data that is encapsulated within a TCP header. It contains both the actual data being transmitted (the payload) and control information necessary for the reliable delivery of that data. The TCP header includes various fields that help manage the transmission, such as sequence numbers, acknowledgment numbers, flags, and window size.

2. Segmentation of Data
When an application sends data over a TCP connection, the data is often larger than the maximum transmission unit (MTU) of the underlying network. TCP breaks this data into smaller pieces called segments. Each segment is sent independently, and TCP ensures that they are reassembled in the correct order at the receiving end.

3. Sequence Numbers
Each TCP segment is assigned a sequence number, which is crucial for maintaining the order of segments. The sequence number indicates the position of the first byte of data in the segment relative to the overall data stream. This allows the receiving TCP stack to reorder segments if they arrive out of order.

4. Acknowledgment and Reliability
TCP is a connection-oriented protocol that provides reliable data transmission. When a sender transmits a segment, it expects an acknowledgment (ACK) from the receiver. If the sender does not receive an ACK within a certain timeframe, it assumes the segment was lost and retransmits it. This acknowledgment mechanism is essential for ensuring that all segments are received correctly.

5. Flow Control
TCP uses a flow control mechanism to manage the rate of data transmission between sender and receiver. The TCP header includes a "window size" field, which indicates the amount of data that the sender can transmit before needing an acknowledgment. This helps prevent overwhelming the receiver with too much data at once.

6. Congestion Control
TCP segments also play a role in congestion control. TCP monitors network conditions and adjusts the rate of segment transmission based on perceived network congestion. Techniques such as slow start, congestion avoidance, and fast recovery are used to manage how segments are sent in response to network conditions.

7. Connection Establishment and Termination
TCP segments are used during the establishment and termination of a connection. The three-way handshake (SYN, SYN-ACK, ACK) used to establish a TCP connection involves the exchange of TCP segments. Similarly, the connection termination process (FIN, FIN-ACK) also involves the exchange of segments.

# IP layer (Data Link)

![ipstuff](assets/ip´.png)

- manchester code and others (nrz)j

- Adder resolution protocol
![](assets/addert%20resolutiojn.png)

    rfc: https://datatracker.ietf.org/doc/html/rfc826
    
![](assets/ARP.png)

![](assets/headers.png)

A computer can determine the Maximum Transmission Unit (MTU) of the next hop (the next machine or router) in the network path using a technique called Path MTU Discovery (PMTUD).

# DNS
- dns header 
    Google Public DNS is a recursive DNS resolver
    https://developers.google.com/speed/public-dns/docs/intro
    https://www.geeksforgeeks.org/dns-message-format/
    
    https://root-servers.org/

- root servers
    https://www.iana.org/domains/root/servers

### tools and commands
- dig www.google.com (pacman -Syu bind)
- 1 extra/strace 6.10-1 (492.8 KiB 1.5 MiB) 
- ltrace
- nslookup: A command-line tool for querying DNS to obtain domain name or IP address mapping. nslookup example.com
- dig: A more powerful DNS lookup tool that provides detailed information about DNS records.
- traceroute 
- tracepath -4 www.google.com
- ip route | grep default
- ip addr show
- curl ifconfig.me > ip.txt


# TODO
read ExemploThreads 
ExemploServidorMultiThread
exemploUDb exemploUDP_DNS inetadresstest

balanceador (blanceamento de carga nginx)
