SRC := $(shell find . -name "*.java")
CLASSES := $(shell find . -name "*.class")
JARS := $(shell find . -name "*.jar")

DIR := target
TARGET := $(DIR)/*.jar

run: $(TARGET)
	java -ea -jar $(TARGET)

$(TARGET): $(SRC) pom.xml
	@rm -f $(TARGET) # Delete the old target if it exists
	mvn clean compile assembly:single

markdown:
	watch --color -n 0.01 glow -p README.md

zip:
	zip -r SD.zip ./ -x **class


clean:
	@echo "Cleaning up..."

	@rm -f $(JARS) # Remove all .jar files
	@rm -f $(CLASSES) # Remove all .class files
	@rm -f $(TARGET)
	@rm -fr $(DIR)


	@echo "Cleanup completed."

reset:
	sudo rabbitmqctl stop_app
	sudo rabbitmqctl reset
	sudo rabbitmqctl start_app
	sudo rabbitmq-plugins enable rabbitmq_management

.PHONY: markdown run clean reset zip
