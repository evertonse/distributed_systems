void NetDomainNameResolve(NetTask *_task, void *_buffer) {
  pEsBuffer *buffer = (EsBuffer *)_buffer;

  NetDomainNameResolveTask *task = (NetDomainNameResolveTask *)_task;
  NetInterface *interface = task->interface;

  if (task->completed) {
    if (task->index != 0xFFFF) {
      KMutexAcquire(&networking.udpTaskBitsetMutex);
      networking.udpTaskBitset.Put(task->index);
      KMutexRelease(&networking.udpTaskBitsetMutex);
    }

    if (task->event) {
      // This must be the last thing we do, otherwise the NetTask might be
      // freed.
      KEventSet(task->event);
    }
  } else if (task->step == 0) {
    KMACAddress dnsServerMAC;

    if (!NetARPLookup(task, interface->dnsServerIP, &dnsServerMAC)) {
      return;
    }

    KMutexAcquire(&networking.udpTaskBitsetMutex);
    ptrdiff_t taskIndex = networking.udpTaskBitset.Get();

    if (taskIndex == -1) {
      KMutexRelease(&networking.udpTaskBitsetMutex);
      NetTaskComplete(task, ES_ERROR_INSUFFICIENT_RESOURCES);
      return;
    }

    networking.udpTasks[taskIndex] = task;
    task->index = taskIndex;
    KMutexRelease(&networking.udpTaskBitsetMutex);

    EsBuffer buffer = NetTransmitBufferGet();

    if (buffer.error) {
      NetTaskComplete(task, ES_ERROR_INSUFFICIENT_RESOURCES);
      return;
    }

    EthernetHeader *ethernet =
        (EthernetHeader *)buffer.Write(nullptr, sizeof(EthernetHeader));
    ETHERNET_HEADER(ethernet, ETHERNET_TYPE_IPV4, dnsServerMAC);
    IPHeader *ip = (IPHeader *)buffer.Write(nullptr, sizeof(IPHeader));
    IP_HEADER(ip, interface->dnsServerIP, IP_PROTOCOL_UDP);
    UDPHeader *udp = (UDPHeader *)buffer.Write(nullptr, sizeof(UDPHeader));

    UDP_HEADER(udp, taskIndex + UDP_PORT_BASE, 53 /* DNS server */);

    DNSHeader *dns = (DNSHeader *)buffer.Write(nullptr, sizeof(DNSHeader));
    dns->identifier = task->identifier = EsRandomU64();
    dns->flags = SwapBigEndian16(1 << 8 /* recursion desired */);
    dns->questionCount = SwapBigEndian16(1);

    for (uintptr_t i = 0, j = 0; i <= task->nameBytes; i++) {

      if (i == task->nameBytes || task->name[i] == '.') {
        uint8_t bytes = i - j;
        buffer.Write(&bytes, 1);
        buffer.Write(task->name + j, bytes);

        j = i + 1;
      }
    }

    {

      uint8_t zero = 0;
      buffer.Write(&zero, 1);

      uint16_t queryType = SwapBigEndian16(1 /* A - IPv4 address */);
      buffer.Write(&queryType, 2);

      uint16_t queryClass = SwapBigEndian16(1 /* IN - the internet */);
      buffer.Write(&queryClass, 2);
    }

    if (buffer.error) {
      KernelPanic("NetInterface::DomainNameResolve - Network interface buffer "
                  "size too small.\n");
    }

    ip->totalLength = ByteSwap16(buffer.position - sizeof(*ethernet));
    udp->length = ByteSwap16(buffer.position - sizeof(*ethernet) - sizeof(*ip));

    task->step++;

    if (!NetTransmit(interface, &buffer, NET_PACKET_ETHERNET)) {
      NetTaskComplete(task, ES_ERROR_INSUFFICIENT_RESOURCES);
      return;
    }
  } else if (task->step == 1) {
    const DNSHeader *header =
        (const DNSHeader *)buffer->Read(sizeof(DNSHeader));

    if (!header) {
      KernelLog(LOG_ERROR, "Networking", "bad packet", "Missing DNS header.\n");

      return;
    }

    if (header->identifier != task->identifier) {
      KernelLog(LOG_ERROR, "Networking", "bad packet",
                "Received DNS packet with wrong identifier.\n");
      return;
    }

    uint16_t flags = SwapBigEndian16(header->flags);

    if (~flags & (1 << 15)) {
      KernelLog(LOG_ERROR, "Networking", "bad packet",
                "Received DNS request (expecting reponse).\n");
      return;
    }

    EsError error = ES_ERROR_UNKNOWN;

    if ((flags & 15) == 3) {
      error = ES_ERROR_NO_ADDRESS_FOR_DOMAIN_NAME;
    } else if ((flags & 15) == 0) {
      error = ES_SUCCESS;
    }

    for (uintptr_t i = 0; i < SwapBigEndian16(header->questionCount); i++) {
      while (true) {
        const uint8_t *length = (const uint8_t *)buffer->Read(1);
        if (!length)
          break;

        if ((*length & 0xC0) == 0xC0) {

          buffer->Read(1);
          break;
        } else if (*length == 0) {

          break;
        }

        buffer->Read(*length);
      }

      buffer->Read(4);
    }

    bool foundAddress = false;

    for (uintptr_t i = 0; i < SwapBigEndian16(header->answerCount); i++) {
      while (true) {
        const uint8_t *length = (const uint8_t *)buffer->Read(1);
        if (!length)
          break;

        if ((*length & 0xC0) == 0xC0) {
          buffer->Read(1);
          break;
        } else if (*length == 0) {

          break;
        }

        buffer->Read(*length);
      }

      const uint16_t *type = (const uint16_t *)buffer->Read(2);
      const uint16_t *classType = (const uint16_t *)buffer->Read(2);
      const uint32_t *timeToLive = (const uint32_t *)buffer->Read(4);
      const uint16_t *dataLength = (const uint16_t *)buffer->Read(2);

      if (!type || !classType || !timeToLive || !dataLength) {
        break;
      }

      const void *data =
          (const void *)buffer->Read(SwapBigEndian16(*dataLength));

      if (!data) {
        break;
      }

      if (SwapBigEndian16(*type) == 1 /* A - IPv4 address */
          && SwapBigEndian16(*classType) == 1 /* IN - the internet */) {
        if (SwapBigEndian16(*dataLength) != 4) {
          KernelLog(LOG_ERROR, "Networking", "bad packet",
                    "IPv4 address was not 4 bytes.\n");
          return;
        }

        EsMemoryCopy(&task->address->ipv4, data, 4);
        foundAddress = true;

        break;
      }
    }

    if (buffer->error) {
      KernelLog(LOG_ERROR, "Networking", "bad packet",
                "Missing data after DNS header.\n");
      return;
    }

    if (!foundAddress) {
      KernelLog(LOG_ERROR, "Networking", "bad packet",
                "Could not find IP address in DNS packet.\n");
      error = ES_ERROR_UNKNOWN;
    }

    NetTaskComplete(task, error);
  } else {
    KernelPanic("NetDomainNameResolve - Invalid step.\n");
  }
}
