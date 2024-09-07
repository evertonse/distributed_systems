SRC := $(shell find . -name "*.java")
CLASSES := $(shell find . -name "*.class")
JARS := $(shell find . -name "*.jar")

DIR := target
DONWLOADS_DIR := ./downloads/
TARGET := $(DIR)/*.jar

run: $(TARGET)
	java -ea -jar $(TARGET)

$(TARGET): $(SRC) pom.xml
	@rm -f $(TARGET) # Delete the old target if it exists
	mvn compile assembly:single
	# mvn clean compile assembly:single

markdown:
	watch --color -n 0.01 glow -p README.md

zip:
	zip -r SD.zip ./ -x **class

random-file:
	head -c 100M /dev/urandom | tr -dc '[:print:]' > random_text.txt

clean:
	@echo "Cleaning up..."
	mvn clean
	@rm -f $(JARS) # Remove all .jar files
	@rm -f $(CLASSES) # Remove all .class files
	@rm -f $(TARGET)
	@rm -fr $(DIR)
	@rm -fr $(DONWLOADS_DIR)
	@echo "Cleanup completed."

reset:
	sudo rabbitmqctl stop_app
	sudo rabbitmqctl reset
	sudo rabbitmqctl start_app
	sudo rabbitmq-plugins enable rabbitmq_management

#NOTE: Observer behaviour with `sudo tcpdump -vvv -n -i any tcp port 6969`
# The "-n" option is used to translate the hostname and ports. Without this option, the output displays hostname which is converted to it's corresponding IP address.
# A: I want to talk to you
# B: I acknowledge that you want to talk to me, I also want to talk to you
# A: I acknowledge that you want to talk to me
# A: The weather today is nice
# B: Indeed
# B: The wind makes me want to go for a walk
# A: Indeed
#
# A: I need to go now
# B: I acknowledge that you need to go
# B: I need to go now
# A: I acknowledge that you need to go
tcp:
	javac ./examples/TCP.java && java -cp ./examples TCP $(ARGS)
	

#NOTE: Observer behaviour with `sudo tcpdump -i any udp port 6969`
udp:
	javac ./examples/UDP.java && java -cp ./examples UDP $(ARGS)

dns:
	javac ./examples/DNSClient.java && java -cp ./examples DNSClient $(ARGS)

dns.resolver:
	javac ./examples/SimpleDNSResolver.java && java -cp ./examples SimpleDNSResolver $(ARGS)

cexample:
	sh ./examples/$(FILE) $(ARGS)
     


.PHONY: markdown run clean reset zip random-file tcp udp
