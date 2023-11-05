SPACE := $(EMPTY) $(EMPTY)
JARS = $(subst $(SPACE),:,$(wildcard libs/*.jar))
CLASS_PATH = $(JARS):src

# Optimize for build speed and portability. The code is fast enough already.
BUILD_OPTS = -H:+UnlockExperimentalVMOptions \
	-H:ReflectionConfigurationFiles=config/reflect-config.json \
	-H:ResourceConfigurationFiles=config/resource-config.json \
	-Ob -march=compatibility -H:Name=librelaxng --shared -cp $(CLASS_PATH)

CONFIG_OPTS = -agentlib:native-image-agent=config-output-dir=config -cp $(CLASS_PATH)

CLASSES = $(patsubst %.java,%.class,$(wildcard src/*.java))

all: librelaxng.so

config: $(CLASSES)
	java $(CONFIG_OPTS) Main < test/commands.txt

clean:
	$(RM) src/*.class *.h

.PHONY: all config clean

librelaxng.so: $(CLASSES) $(wildcard libs/*.jar)
	native-image $(BUILD_OPTS)

%.class: %.java
	javac -cp $(CLASS_PATH) $<
