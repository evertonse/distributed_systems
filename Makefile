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

.PHONY: markdown run clean reset zip random-file
