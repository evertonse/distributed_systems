SRC := $(shell find . -name "*.java")
CLASSES := $(shell find . -name "*.class")
JARS := $(shell find . -name "*.jar")

TARGET := target/EvertonChat-1.0-SNAPSHOT-jar-with-dependencies.jar

run: $(TARGET)
	java -jar $(TARGET)

$(TARGET): $(SRC) pom.xml
	@rm -f $(TARGET) # Delete the old target if it exists
	mvn clean compile assembly:single

markdown:
	watch --color -n 0.01 glow -p README.md

clean:
	@echo "Cleaning up..."

	@rm -f $(JARS) # Remove all .jar files
	@rm -f $(CLASSES) # Remove all .class files

	@echo "Cleanup completed."

.PHONY: markdown run clean


