FROM ascent/ascent-base

ENV JAR_FILE "/ascent-config.jar"
ADD target/ascent-config.jar $JAR_FILE

# Append app specific secrets to load to the base config
RUN echo \
'secret { \
    format = "{{ key }}" \
    no_prefix = true \
    path = "secret/application" \
} \
secret { \
    format = "git.{{ key }}" \
    no_prefix = true \
    path = "secret/ascent-config-server/git" \
}' >> $ENVCONSUL_CONFIG
