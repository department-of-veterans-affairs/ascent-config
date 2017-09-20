FROM jluck/ascent-base

ENV JAR_FILE "/ascent-config.jar"
ADD target/ascent-config-*.jar $JAR_FILE

# Append app specific secrets to load to the base config
RUN echo \
'secret { \
    format = "messagebroker.{{ key }}" \
    no_prefix = true \
    path = "secret/ascent-config-server/messagebroker" \
} \
secret { \
    format = "git.{{ key }}" \
    no_prefix = true \
    path = "secret/ascent-config-server/git" \
}' >> $ENVCONSUL_CONFIG
