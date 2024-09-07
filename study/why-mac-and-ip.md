-  https://networkengineering.stackexchange.com/questions/3329/reason-for-both-a-mac-and-an-ip-address

To start with, this is a very good question, which touches upon the basic network foundations.

The bottom line to your question is, we don't really need a MAC address in order to achieve connectivity. Theoretically, a network could be built implementing IP addresses alone!

However, some practical difficulties may arise out of using such a scheme. If you expect to manually assign an IP address to each network device, then you may avoid the major pitfalls. However, if you need to automatically assign IP addresses, as when there are too many network nodes to manually administer, then there is no way to make sure each node is allocated exactly a single IP address, or a predetermined number of them, because you cannot tell one from the other, unless the address distribution entity can uniquely and unequivocally identify the requesting device, e.g. by its MAC address, which is assumed to be unique for any device in a LAN.

Such an unidentifiable device may repeatedly ask for additional IP addresses and, eventually, wreak havoc on the network viability.

Back to the topic, all you need to establish an IP-only network, is to let network interface adapter's Data Link Layer pass through any data frame it receives, directly to the Network Layer, regardless of any MAC comparable address type, where it can be filtered according to its destination IP address, instead of being filtered at the Data Layer.

Just to illustrate the concept, assume a network is established by interconnecting RS-232 UART ports: UART devices don't have MAC addresses, or any other unique identifier, for that matter, yet you could construct a local network using UARTs and IP addresses alone, providing you install the proper UART drivers.

Hope this passage gave you some insights on the subject.
